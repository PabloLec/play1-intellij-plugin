package com.github.pablolec.play1toolkit.templates.service

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.*

@Service(Service.Level.PROJECT)
class PlayTemplateVariableResolver(private val project: Project) {

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
        val result = mutableSetOf<String>()
        result.addAll(IMPLICIT_VARS)

        val virtualFile = file.virtualFile ?: return result
        val logicalPath = PlayTemplateFileUtils.logicalPath(project, virtualFile) ?: return result
        val controllerName = PlayTemplateFileUtils.controllerNameFromLogicalPath(logicalPath)
        val actionName = PlayTemplateFileUtils.actionNameFromLogicalPath(logicalPath)

        if (controllerName != null && actionName != null) {
            result.addAll(resolveFromControllerAction(controllerName, actionName))
        }

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
}
