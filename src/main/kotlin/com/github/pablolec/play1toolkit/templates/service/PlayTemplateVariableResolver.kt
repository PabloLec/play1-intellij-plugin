package com.github.pablolec.play1toolkit.templates.service

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class PlayTemplateVariableResolver(private val project: Project) {

    private val explicitTemplateBindingsCache = CachedValuesManager.getManager(project).createCachedValue({
        CachedValueProvider.Result.create(buildExplicitTemplateBindings(), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)

    private val includeParentsCache = CachedValuesManager.getManager(project).createCachedValue({
        CachedValueProvider.Result.create(buildIncludeParentsMap(), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)

    companion object {
        private val IMPLICIT_VARS = setOf(
            "request", "params", "session", "flash", "errors", "out", "play",
            "_", "_index", "_parity", "_isFirst", "_isLast"
        )
        private val SCRIPT_BLOCK = Regex("""%\{(.*?)}%""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val SCRIPT_ASSIGNMENT = Regex("""(?:^|[\s;])(?:def\s+)?([A-Za-z_]\w*)\s*=""")

        fun getInstance(project: Project): PlayTemplateVariableResolver =
            project.getService(PlayTemplateVariableResolver::class.java)
    }

    fun resolveVariables(file: PsiFile): Set<String> {
        if (DumbService.isDumb(project)) return IMPLICIT_VARS
        return resolveVariableDeclarations(file).keys
    }

    fun resolveVariableDeclarations(file: PsiFile): Map<String, PsiElement> {
        if (DumbService.isDumb(project)) return emptyMap()
        return resolveVariableDeclarations(file, mutableSetOf())
    }

    private fun resolveVariableDeclarations(file: PsiFile, visited: MutableSet<String>): Map<String, PsiElement> {
        val result = linkedMapOf<String, PsiElement>()
        val virtualFile = file.virtualFile ?: return result
        val logicalPath = PlayTemplateFileUtils.logicalPath(project, virtualFile) ?: return result
        if (!visited.add(logicalPath)) return result
        val controllerName = PlayTemplateFileUtils.controllerNameFromLogicalPath(logicalPath)
        val actionName = PlayTemplateFileUtils.actionNameFromLogicalPath(logicalPath)

        if (controllerName != null && actionName != null) {
            result.putAll(resolveFromControllerAction(controllerName, actionName))
        }

        includeParentsCache.value[logicalPath].orEmpty().forEach { parentLogicalPath ->
            val parentFile = PlayTemplateFileUtils.resolveTemplatePath(project, parentLogicalPath)
                ?.let { PsiManager.getInstance(project).findFile(it) }
                ?: return@forEach
            result.putAll(resolveVariableDeclarations(parentFile, visited))
        }

        explicitTemplateBindingsCache.value[logicalPath].orEmpty().forEach { (name, element) ->
            result.putIfAbsent(name, element)
        }

        resolveFromScriptBlocks(file).forEach { (name, element) ->
            result[name] = element
        }
        resolveFromListTags(file).forEach { (name, element) ->
            result[name] = element
        }

        return result
    }

    private fun resolveFromControllerAction(controllerName: String, actionName: String): Map<String, PsiElement> {
        val method = RoutesControllerResolver.resolveMethod(project, controllerName, actionName)
            ?: return emptyMap()
        return extractRenderVariables(method)
    }

    private fun extractRenderVariables(method: PsiMethod): Map<String, PsiElement> {
        val vars = linkedMapOf<String, PsiElement>()
        method.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                val name = expression.methodExpression.referenceName ?: return
                if (name != "render" && name != "renderTemplate") return
                val containingClass = expression.resolveMethod()?.containingClass ?: method.containingClass ?: return
                if (!Play1ViewUtils.isPlayControllerClass(containingClass)) return

                val args = expression.argumentList.expressions
                val startIdx = if (name == "renderTemplate") 1 else 0
                for (i in startIdx until args.size) {
                    val arg = args[i]
                    if (arg is PsiReferenceExpression) {
                        val localVar = arg.resolve()
                        if (localVar is PsiLocalVariable || localVar is PsiParameter || localVar is PsiField) {
                            vars.putIfAbsent(arg.referenceName ?: continue, localVar)
                        }
                    }
                }
            }
        })
        return vars
    }

    private fun resolveFromListTags(file: PsiFile): Map<String, PsiElement> {
        val text = file.text ?: return emptyMap()
        val result = linkedMapOf<String, PsiElement>()
        PlayTemplatePatterns.LIST_TAG_VAR.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val groupRange = match.groups[1]?.range ?: return@forEach
            val element = file.findElementAt(groupRange.first) ?: return@forEach
            result[name] = element
        }
        return result
    }

    private fun resolveFromScriptBlocks(file: PsiFile): Map<String, PsiElement> {
        val text = file.text ?: return emptyMap()
        val result = linkedMapOf<String, PsiElement>()
        SCRIPT_BLOCK.findAll(text).forEach { block ->
            val bodyRange = block.groups[1]?.range ?: return@forEach
            SCRIPT_ASSIGNMENT.findAll(block.groupValues[1]).forEach { assignment ->
                val name = assignment.groupValues[1]
                val nameRange = assignment.groups[1]?.range ?: return@forEach
                val absoluteOffset = bodyRange.first + nameRange.first
                val element = file.findElementAt(absoluteOffset) ?: return@forEach
                result[name] = element
            }
        }
        return result
    }

    private fun buildExplicitTemplateBindings(): Map<String, Map<String, PsiElement>> {
        val result = mutableMapOf<String, MutableMap<String, PsiElement>>()
        val scope = GlobalSearchScope.projectScope(project)
        FilenameIndex.getAllFilesByExt(project, "java", scope).forEach { virtualFile ->
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@forEach
            psiFile.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)
                    if (expression.methodExpression.referenceName != "renderTemplate") return
                    val args = expression.argumentList.expressions
                    val templatePath = (args.firstOrNull() as? PsiLiteralExpression)?.value as? String ?: return
                    val logicalPath = PlayTemplateFileUtils.normalizeTemplatePath(templatePath)
                    val vars = result.getOrPut(logicalPath) { linkedMapOf() }
                    for (arg in args.drop(1)) {
                        when (arg) {
                            is PsiReferenceExpression -> {
                                val resolved = arg.resolve()
                                if (resolved is PsiLocalVariable || resolved is PsiParameter || resolved is PsiField) {
                                    arg.referenceName?.let { vars.putIfAbsent(it, resolved) }
                                }
                            }
                            is PsiLiteralExpression -> Unit
                        }
                    }
                }
            })
        }
        return result
    }

    private fun buildIncludeParentsMap(): Map<String, Set<String>> {
        val parents = mutableMapOf<String, MutableSet<String>>()
        val templateService = PlayTemplateService.getInstance(project)
        val psiManager = PsiManager.getInstance(project)
        templateService.getAllTemplates().forEach { template ->
            val psiFile = psiManager.findFile(template.virtualFile) ?: return@forEach
            val parentLogicalPath = template.logicalPath
            sequenceOf(PlayTemplatePatterns.TAG_INCLUDE, PlayTemplatePatterns.TAG_EXTENDS).forEach { pattern ->
                pattern.findAll(psiFile.text).forEach { match ->
                    val childLogicalPath = PlayTemplateFileUtils.normalizeTemplatePath(match.groupValues[1])
                    parents.getOrPut(childLogicalPath) { linkedSetOf() }.add(parentLogicalPath)
                }
            }
        }
        return parents
    }
}
