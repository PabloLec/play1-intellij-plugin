package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.routes.RoutesTokenTypes
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor

class PlayTemplateFileUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(params: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val targetFile = params.elementToSearch as? PsiFile ?: return
        val virtualFile = targetFile.virtualFile ?: return
        val project = targetFile.project
        if (DumbService.isDumb(project) || !PlayTemplateFileUtils.isPlayTemplateFile(virtualFile)) return

        val logicalPath = PlayTemplateFileUtils.logicalPath(project, virtualFile) ?: return
        val templateService = PlayTemplateService.getInstance(project)

        processTemplateToTemplateUsages(project, virtualFile, logicalPath, consumer)
        processJavaRenderTemplateUsages(project, logicalPath, consumer)
        processImplicitRenderUsages(project, logicalPath, targetFile, consumer)

        if (PlayTemplateFileUtils.isInTagsDirectory(virtualFile)) {
            val qualifiedName = PlayTemplateFileUtils.tagQualifiedName(logicalPath)
            processTagUsages(project, templateService, qualifiedName, virtualFile, consumer)
        }
    }

    private fun processTemplateToTemplateUsages(
        project: com.intellij.openapi.project.Project,
        targetFile: com.intellij.openapi.vfs.VirtualFile,
        logicalPath: String,
        consumer: Processor<in PsiReference>
    ) {
        val psiManager = PsiManager.getInstance(project)
        PlayTemplateService.getInstance(project).getAllTemplates()
            .asSequence()
            .filter { it.virtualFile != targetFile }
            .forEach { template ->
                val psiFile = psiManager.findFile(template.virtualFile) ?: return@forEach
                val text = psiFile.text
                sequenceOf(PlayTemplatePatterns.TAG_EXTENDS, PlayTemplatePatterns.TAG_INCLUDE).forEach { pattern ->
                    pattern.findAll(text).forEach { match ->
                        val path = PlayTemplateFileUtils.normalizeTemplatePath(match.groupValues[1])
                        if (path != logicalPath) return@forEach
                        val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                        if (quotePos < 0) return@forEach
                        val pathStart = match.range.first + quotePos + 1
                        val leaf = psiFile.findElementAt(pathStart) ?: return@forEach
                        consumer.process(
                            PlayTemplateTextUsageReference(leaf, TextRange(0, leaf.textLength), targetFile)
                        )
                    }
                }
            }
    }

    private fun processJavaRenderTemplateUsages(
        project: com.intellij.openapi.project.Project,
        logicalPath: String,
        consumer: Processor<in PsiReference>
    ) {
        com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "java").forEach { vf ->
            val psiJava = PsiManager.getInstance(project).findFile(vf) ?: return@forEach
            psiJava.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitLiteralExpression(expression: PsiLiteralExpression) {
                    super.visitLiteralExpression(expression)
                    val value = expression.value as? String ?: return
                    if (PlayTemplateFileUtils.normalizeTemplatePath(value) != logicalPath) return
                    if (!PlayTemplateJavaContextDetector.isRenderTemplatePathContext(expression)) return
                    consumer.process(
                        PlayTemplateJavaPathReference(expression, TextRange(1, value.length + 1), value)
                    )
                }
            })
        }
    }

    private fun processImplicitRenderUsages(
        project: com.intellij.openapi.project.Project,
        logicalPath: String,
        targetFile: PsiFile,
        consumer: Processor<in PsiReference>
    ) {
        val controllerName = PlayTemplateFileUtils.controllerNameFromLogicalPath(logicalPath) ?: return
        val actionName = PlayTemplateFileUtils.actionNameFromLogicalPath(logicalPath) ?: return
        val method = RoutesControllerResolver.resolveMethod(project, controllerName, actionName) ?: return
        method.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                if (expression.methodExpression.referenceName != "render") return
                consumer.process(PlayTemplateImplicitRenderReference(expression, targetFile))
            }
        })
    }

    private fun processTagUsages(
        project: com.intellij.openapi.project.Project,
        templateService: PlayTemplateService,
        qualifiedName: String,
        targetFile: com.intellij.openapi.vfs.VirtualFile,
        consumer: Processor<in PsiReference>
    ) {
        val simpleName = qualifiedName.substringAfterLast('.')
        templateService.getAllTemplates().forEach { template ->
            val psiFile = PsiManager.getInstance(project)
                .findFile(template.virtualFile) ?: return@forEach
            val text = psiFile.text
            PlayTemplatePatterns.TAG_NAME_AT.findAll(text).forEach { match ->
                val tagName = match.groupValues[1]
                if (tagName != qualifiedName && tagName != simpleName) return@forEach
                val start = match.range.first + 2
                val leaf = psiFile.findElementAt(start) ?: return@forEach
                consumer.process(PlayTemplateTextUsageReference(leaf, TextRange(0, leaf.textLength), targetFile))
            }
        }
    }
}

class PlayTemplateRouteMethodReferencesSearcher : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>(true) {
    override fun processQuery(params: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val method = params.method
        val project = method.project
        if (DumbService.isDumb(project)) return
        val controllerName = method.containingClass?.name ?: return
        val actionName = method.name
        val psiManager = PsiManager.getInstance(project)
        PlayTemplateService.getInstance(project).getAllTemplates().forEach { template ->
            val psiFile = psiManager.findFile(template.virtualFile) ?: return@forEach
            val text = psiFile.text
            sequenceOf(PlayTemplatePatterns.REVERSE_ROUTE, PlayTemplatePatterns.BARE_ACTION_REF).forEach { pattern ->
                pattern.findAll(text).forEach { match ->
                    val fullRef = match.groupValues[1]
                    val dotIndex = fullRef.lastIndexOf('.')
                    if (dotIndex < 0) return@forEach
                    if (fullRef.substring(0, dotIndex).substringAfterLast('.') != controllerName) return@forEach
                    if (fullRef.substring(dotIndex + 1) != actionName) return@forEach
                    val actionStart = match.range.first + match.value.indexOf(actionName)
                    val leaf = psiFile.findElementAt(actionStart) ?: return@forEach
                    consumer.process(
                        PlayTemplateMethodUsageReference(
                            leaf,
                            TextRange(0, leaf.textLength),
                            controllerName,
                            actionName
                        )
                    )
                }
            }
        }
    }
}

class PlayTemplateRouteActionUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(params: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val element = params.elementToSearch
        val nodeType = element.node?.elementType
        if (element !is RoutesRouteElement &&
            nodeType != RoutesTokenTypes.ACTION_NAME &&
            nodeType != RoutesTokenTypes.CONTROLLER_NAME) return
        val route = element as? RoutesRouteElement
            ?: PsiTreeUtil.getParentOfType(element, RoutesRouteElement::class.java) ?: return
        if (!route.isDynamicRoute()) return
        val controllerName = route.getControllerName()?.text?.trim()?.substringAfterLast('.') ?: return
        val actionName = route.getActionName()?.text?.trim() ?: return
        val project = element.project
        if (DumbService.isDumb(project)) return
        val psiManager = PsiManager.getInstance(project)
        PlayTemplateService.getInstance(project).getAllTemplates().forEach { template ->
            val psiFile = psiManager.findFile(template.virtualFile) ?: return@forEach
            val text = psiFile.text
            sequenceOf(PlayTemplatePatterns.REVERSE_ROUTE, PlayTemplatePatterns.BARE_ACTION_REF).forEach { pattern ->
                pattern.findAll(text).forEach { match ->
                    val fullRef = match.groupValues[1]
                    val dotIndex = fullRef.lastIndexOf('.')
                    if (dotIndex < 0) return@forEach
                    if (fullRef.substring(0, dotIndex).substringAfterLast('.') != controllerName) return@forEach
                    if (fullRef.substring(dotIndex + 1) != actionName) return@forEach
                    val actionStart = match.range.first + match.value.indexOf(actionName)
                    val leaf = psiFile.findElementAt(actionStart) ?: return@forEach
                    consumer.process(PlayTemplateMethodUsageReference(leaf, TextRange(0, leaf.textLength), controllerName, actionName))
                }
            }
        }
    }
}

class PlayTemplateRouteClassReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(params: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val psiClass = params.elementToSearch as? PsiClass ?: return
        val project = psiClass.project
        if (DumbService.isDumb(project)) return
        val controllerName = psiClass.name ?: return
        val psiManager = PsiManager.getInstance(project)
        PlayTemplateService.getInstance(project).getAllTemplates().forEach { template ->
            val psiFile = psiManager.findFile(template.virtualFile) ?: return@forEach
            val text = psiFile.text
            sequenceOf(PlayTemplatePatterns.REVERSE_ROUTE, PlayTemplatePatterns.BARE_ACTION_REF).forEach { pattern ->
                pattern.findAll(text).forEach { match ->
                    val fullRef = match.groupValues[1]
                    val dotIndex = fullRef.lastIndexOf('.')
                    val controllerRef = if (dotIndex > 0) fullRef.substring(0, dotIndex) else fullRef
                    if (controllerRef.substringAfterLast('.') != controllerName) return@forEach
                    val start = match.range.first + match.value.indexOf(controllerRef)
                    val leaf = psiFile.findElementAt(start) ?: return@forEach
                    consumer.process(
                        PlayTemplateClassUsageReference(
                            leaf,
                            TextRange(0, leaf.textLength),
                            controllerRef
                        )
                    )
                }
            }
        }
    }
}

private class PlayTemplateImplicitRenderReference(
    element: PsiMethodCallExpression,
    private val targetFile: PsiFile
) : PsiReferenceBase<PsiMethodCallExpression>(element, true) {
    override fun resolve(): PsiElement = targetFile
    override fun getVariants(): Array<Any> = emptyArray()
}

private class PlayTemplateMethodUsageReference(
    element: PsiElement,
    range: TextRange,
    private val controllerName: String,
    private val actionName: String
) : PsiReferenceBase.Poly<PsiElement>(element, range, true) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val method = RoutesControllerResolver.resolveMethod(element.project, controllerName, actionName)
            ?: return ResolveResult.EMPTY_ARRAY
        return arrayOf(PsiElementResolveResult(method))
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val text = element.text
        val idx = text.indexOf(".$actionName")
        if (idx >= 0) {
            val range = TextRange(idx + 1, idx + 1 + actionName.length)
            return ElementManipulators.handleContentChange(element, range, newElementName)
        }
        return element
    }
}

private class PlayTemplateTextUsageReference(
    element: PsiElement,
    range: TextRange,
    private val targetFile: com.intellij.openapi.vfs.VirtualFile
) : PsiReferenceBase<PsiElement>(element, range, true) {
    override fun resolve(): PsiElement? =
        PsiManager.getInstance(element.project).findFile(targetFile)

    override fun getVariants(): Array<Any> = emptyArray()
}

private class PlayTemplateClassUsageReference(
    element: PsiElement,
    range: TextRange,
    private val controllerName: String
) : PsiReferenceBase.Poly<PsiElement>(element, range, true) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val clazz = RoutesControllerResolver.resolveClass(element.project, controllerName)
            ?: return ResolveResult.EMPTY_ARRAY
        return arrayOf(PsiElementResolveResult(clazz))
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val text = element.text
        val idx = text.indexOf(controllerName)
        if (idx >= 0) {
            val range = TextRange(idx, idx + controllerName.length)
            return ElementManipulators.handleContentChange(element, range, newElementName)
        }
        return element
    }
}
