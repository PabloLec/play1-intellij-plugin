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
        return resolveVariables(file, mutableSetOf())
    }

    private fun resolveVariables(file: PsiFile, visited: MutableSet<String>): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(IMPLICIT_VARS)

        val virtualFile = file.virtualFile ?: return result
        val logicalPath = PlayTemplateFileUtils.logicalPath(project, virtualFile) ?: return result
        if (!visited.add(logicalPath)) return result
        val controllerName = PlayTemplateFileUtils.controllerNameFromLogicalPath(logicalPath)
        val actionName = PlayTemplateFileUtils.actionNameFromLogicalPath(logicalPath)

        if (controllerName != null && actionName != null) {
            result.addAll(resolveFromControllerAction(controllerName, actionName))
        }

        result.addAll(explicitTemplateBindingsCache.value[logicalPath].orEmpty())

        includeParentsCache.value[logicalPath].orEmpty().forEach { parentLogicalPath ->
            val parentFile = PlayTemplateFileUtils.resolveTemplatePath(project, parentLogicalPath)
                ?.let { PsiManager.getInstance(project).findFile(it) }
                ?: return@forEach
            result.addAll(resolveVariables(parentFile, visited))
        }

        result.addAll(resolveFromScriptBlocks(file.text ?: ""))
        result.addAll(resolveFromListTags(file.text ?: ""))

        return result
    }

    private fun resolveFromControllerAction(controllerName: String, actionName: String): Set<String> {
        val method = RoutesControllerResolver.resolveMethod(project, controllerName, actionName)
            ?: return emptySet()
        return extractRenderVariables(method)
    }

    private fun extractRenderVariables(method: PsiMethod): Set<String> {
        val vars = mutableSetOf<String>()
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
                        if (localVar is PsiLocalVariable || localVar is PsiParameter) {
                            vars.add(arg.referenceName ?: continue)
                        }
                    }
                }
            }
        })
        return vars
    }

    private fun resolveFromListTags(templateText: String): Set<String> =
        PlayTemplatePatterns.LIST_TAG_VAR.findAll(templateText)
            .map { it.groupValues[1] }
            .toSet()

    private fun resolveFromScriptBlocks(templateText: String): Set<String> =
        SCRIPT_BLOCK.findAll(templateText)
            .flatMap { block -> SCRIPT_ASSIGNMENT.findAll(block.groupValues[1]).map { it.groupValues[1] } }
            .toSet()

    private fun buildExplicitTemplateBindings(): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
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
                    val vars = result.getOrPut(logicalPath) { linkedSetOf() }
                    for (arg in args.drop(1)) {
                        when (arg) {
                            is PsiReferenceExpression -> {
                                val resolved = arg.resolve()
                                if (resolved is PsiLocalVariable || resolved is PsiParameter || resolved is PsiField) {
                                    arg.referenceName?.let(vars::add)
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
