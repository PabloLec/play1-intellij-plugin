package com.github.pablolec.play1toolkit.templates.service

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

@Service(Service.Level.PROJECT)
class PlayTemplateVariableResolver(private val project: Project) {

    data class VariableInfo(
        val declaration: PsiElement,
        val type: PsiType?,
        val exactOffset: Int? = null
    )

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

        fun getInstance(project: Project): PlayTemplateVariableResolver =
            project.getService(PlayTemplateVariableResolver::class.java)
    }

    fun resolveVariables(file: PsiFile): Set<String> {
        if (DumbService.isDumb(project)) return IMPLICIT_VARS
        return resolveVariableInfos(file).keys
    }

    fun resolveVariableDeclarations(file: PsiFile): Map<String, PsiElement> {
        if (DumbService.isDumb(project)) return emptyMap()
        return resolveVariableInfos(file).mapValues { it.value.declaration }
    }

    fun resolveVariableType(file: PsiFile, variableName: String): PsiType? {
        if (DumbService.isDumb(project)) return null
        return resolveVariableInfos(file)[variableName]?.type
    }

    fun resolveVariableInfo(file: PsiFile, variableName: String): VariableInfo? {
        if (DumbService.isDumb(project)) return null
        return resolveVariableInfos(file)[variableName]
    }

    fun resolveMember(element: PsiElement, qualifierType: PsiType?, memberName: String, methodCall: Boolean): PsiElement? {
        val classType = qualifierType as? PsiClassType ?: return null
        val psiClass = classType.resolve() ?: return null
        if (methodCall) {
            psiClass.findMethodsByName(memberName, true).firstOrNull()?.let { return it }
        } else {
            psiClass.findFieldByName(memberName, true)?.let { return it }
            val accessorNames = listOf(
                "get${memberName.replaceFirstChar { it.uppercase() }}",
                "is${memberName.replaceFirstChar { it.uppercase() }}"
            )
            accessorNames.forEach { accessor ->
                psiClass.findMethodsByName(accessor, true).firstOrNull()?.let { return it }
            }
        }
        return null
    }

    fun resolveMemberType(element: PsiElement, qualifierType: PsiType?, memberName: String, methodCall: Boolean): PsiType? {
        val resolved = resolveMember(element, qualifierType, memberName, methodCall) ?: return null
        return when (resolved) {
            is PsiField -> resolved.type
            is PsiMethod -> resolved.returnType
            else -> null
        }
    }

    private fun resolveVariableInfos(file: PsiFile): Map<String, VariableInfo> {
        val result = linkedMapOf<String, VariableInfo>()
        IMPLICIT_VARS.forEach { implicit ->
            result[implicit] = VariableInfo(file, null)
        }
        result.putAll(resolveVariableInfos(file, mutableSetOf()))
        return result
    }

    private fun resolveVariableInfos(file: PsiFile, visited: MutableSet<String>): Map<String, VariableInfo> {
        val result = linkedMapOf<String, VariableInfo>()
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
            result.putAll(resolveVariableInfos(parentFile, visited))
        }

        explicitTemplateBindingsCache.value[logicalPath].orEmpty().forEach { (name, element) ->
            result.putIfAbsent(name, element)
        }

        val knownForLocalInference = LinkedHashMap(result)
        resolveFromScriptBlocks(file).forEach { (name, element) ->
            result[name] = element
            knownForLocalInference[name] = element
        }
        resolveFromListTags(file, knownForLocalInference).forEach { (name, element) ->
            result[name] = element
        }

        return result
    }

    private fun resolveFromControllerAction(controllerName: String, actionName: String): Map<String, VariableInfo> {
        val method = RoutesControllerResolver.resolveMethod(project, controllerName, actionName)
            ?: return emptyMap()
        return extractRenderVariables(method)
    }

    private fun extractRenderVariables(method: PsiMethod): Map<String, VariableInfo> {
        val vars = linkedMapOf<String, VariableInfo>()
        method.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                val name = expression.methodExpression.referenceName ?: return

                if (name == "put") {
                    val qualifier = expression.methodExpression.qualifierExpression
                    if (qualifier?.text == "renderArgs") {
                        val args = expression.argumentList.expressions
                        if (args.size >= 2) {
                            val varName = (args[0] as? PsiLiteralExpression)?.value as? String ?: return
                            val valueArg = args[1]
                            val resolved = (valueArg as? PsiReferenceExpression)?.resolve()
                            if (resolved is PsiLocalVariable || resolved is PsiParameter || resolved is PsiField) {
                                vars.putIfAbsent(varName, VariableInfo(resolved, extractElementType(resolved)))
                            } else {
                                vars.putIfAbsent(varName, VariableInfo(valueArg, null))
                            }
                        }
                    }
                    return
                }

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
                            vars.putIfAbsent(arg.referenceName ?: continue, VariableInfo(localVar, extractElementType(localVar)))
                        }
                    }
                }
            }
        })
        return vars
    }

    private fun resolveFromListTags(file: PsiFile, knownTypes: Map<String, VariableInfo>): Map<String, VariableInfo> {
        val text = file.text ?: return emptyMap()
        val result = linkedMapOf<String, VariableInfo>()
        PlayTemplatePatterns.LIST_TAG_ITEMS_AND_VAR.findAll(text).forEach { match ->
            val itemsExpr = match.groupValues[1]
            val name = match.groupValues[2]
            val groupRange = match.groups[2]?.range ?: return@forEach
            if (file.findElementAt(groupRange.first) == null) return@forEach
            val itemType = inferListItemType(knownTypes, itemsExpr)
            result[name] = VariableInfo(
                com.github.pablolec.play1toolkit.templates.references.PlayTemplateScriptBlockElement(file, groupRange.first),
                itemType,
                groupRange.first
            )
        }
        return result
    }

    private fun resolveFromScriptBlocks(file: PsiFile): Map<String, VariableInfo> {
        val text = file.text ?: return emptyMap()
        val result = linkedMapOf<String, VariableInfo>()
        PlayTemplatePatterns.SCRIPT_BLOCK.findAll(text).forEach { block ->
            val bodyRange = block.groups[1]?.range ?: return@forEach
            PlayTemplatePatterns.SCRIPT_ASSIGNMENT.findAll(block.groupValues[1]).forEach { assignment ->
                val name = assignment.groupValues[1]
                val nameRange = assignment.groups[1]?.range ?: return@forEach
                val absoluteOffset = bodyRange.first + nameRange.first
                if (file.findElementAt(absoluteOffset) == null) return@forEach
                result[name] = VariableInfo(
                    com.github.pablolec.play1toolkit.templates.references.PlayTemplateScriptBlockElement(file, absoluteOffset),
                    null,
                    absoluteOffset
                )
            }
        }
        return result
    }

    private fun buildExplicitTemplateBindings(): Map<String, Map<String, VariableInfo>> {
        val result = mutableMapOf<String, MutableMap<String, VariableInfo>>()
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
                                    arg.referenceName?.let { vars.putIfAbsent(it, VariableInfo(resolved, extractElementType(resolved))) }
                                }
                            }
                            is PsiLiteralExpression -> Unit
                        }
                    }
                    PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java)
                        ?.accept(object : JavaRecursiveElementWalkingVisitor() {
                            override fun visitMethodCallExpression(putExpr: PsiMethodCallExpression) {
                                super.visitMethodCallExpression(putExpr)
                                if (putExpr.methodExpression.referenceName != "put") return
                                val qualifier = putExpr.methodExpression.qualifierExpression ?: return
                                if (qualifier.text != "renderArgs") return
                                val putArgs = putExpr.argumentList.expressions
                                if (putArgs.size < 2) return
                                val varName = (putArgs[0] as? PsiLiteralExpression)?.value as? String ?: return
                                val valueArg = putArgs[1]
                                val resolved = (valueArg as? PsiReferenceExpression)?.resolve()
                                if (resolved is PsiLocalVariable || resolved is PsiParameter || resolved is PsiField) {
                                    vars.putIfAbsent(varName, VariableInfo(resolved, extractElementType(resolved)))
                                } else {
                                    vars.putIfAbsent(varName, VariableInfo(valueArg, null))
                                }
                            }
                        })
                }
            })
        }
        return result
    }

    private fun inferListItemType(knownTypes: Map<String, VariableInfo>, itemsExpr: String): PsiType? {
        val itemsType = resolveExpressionType(knownTypes, itemsExpr) ?: return null
        if (itemsType is PsiArrayType) return itemsType.componentType
        val classType = itemsType as? PsiClassType ?: return null
        val parameters = classType.parameters
        if (parameters.isNotEmpty() && isCollectionType(classType)) {
            return parameters.first()
        }
        return null
    }

    private fun resolveExpressionType(knownTypes: Map<String, VariableInfo>, expression: String): PsiType? {
        val segments = expression.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        var currentType = knownTypes[segments.first()]?.type ?: return null
        for (segment in segments.drop(1)) {
            currentType = resolveMemberTypeByName(currentType, segment, false) ?: return null
        }
        return currentType
    }

    private fun resolveMemberTypeByName(qualifierType: PsiType?, memberName: String, methodCall: Boolean): PsiType? {
        val classType = qualifierType as? PsiClassType ?: return null
        val psiClass = classType.resolve() ?: return null
        if (methodCall) {
            return psiClass.findMethodsByName(memberName, true).firstOrNull()?.returnType
        }
        psiClass.findFieldByName(memberName, true)?.let { return it.type }
        val capitalized = memberName.replaceFirstChar { it.uppercase() }
        psiClass.findMethodsByName("get$capitalized", true).firstOrNull()?.returnType?.let { return it }
        psiClass.findMethodsByName("is$capitalized", true).firstOrNull()?.returnType?.let { return it }
        return null
    }

    private fun extractElementType(element: PsiElement): PsiType? =
        when (element) {
            is PsiLocalVariable -> element.type
            is PsiParameter -> element.type
            is PsiField -> element.type
            else -> null
        }

    private fun isCollectionType(classType: PsiClassType): Boolean {
        val psiClass = classType.resolve() ?: return false
        return InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_COLLECTION)
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
