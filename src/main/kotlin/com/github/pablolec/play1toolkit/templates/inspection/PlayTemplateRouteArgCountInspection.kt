package com.github.pablolec.play1toolkit.templates.inspection

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlText

class PlayTemplateRouteArgCountInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Suspicious Play v1 reverse route argument count"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayTemplateRouteArgCount"
    override fun isEnabledByDefault() = false

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val xmlText = element as? XmlText ?: return
                PlayTemplatePatterns.REVERSE_ROUTE.findAll(xmlText.text).forEach { match ->
                    val fullRef = match.groupValues[1]
                    val argsText = match.groupValues[2]
                    val dotIndex = fullRef.lastIndexOf('.')
                    if (dotIndex < 0) return@forEach
                    val controllerName = fullRef.substring(0, dotIndex)
                    val actionName = fullRef.substring(dotIndex + 1)
                    val project = holder.project
                    RoutesControllerResolver.resolveMethod(project, controllerName, actionName) ?: return@forEach
                    val routes = Play1ViewUtils.findRoutesForAction(
                        project,
                        controllerName.substringAfterLast('.'),
                        actionName
                    )
                    val route = routes.firstOrNull() ?: return@forEach
                    val path = route.getPath() ?: return@forEach
                    val pathParams = PATH_PARAM_RE.findAll(path).toList()
                    if (pathParams.isEmpty()) return@forEach
                    val args = if (argsText.isBlank()) emptyList()
                    else argsText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    // Skip if any arg contains complex expressions (method calls, array access)
                    if (args.any { it.contains('(') || it.contains('[') }) return@forEach
                    if (args.size < pathParams.size) {
                        val refStart = match.range.first + match.value.indexOf(fullRef)
                        holder.registerProblem(
                            xmlText,
                            "Reverse route '$fullRef' expects ${pathParams.size} path parameter(s) but ${args.size} argument(s) provided",
                            ProblemHighlightType.INFORMATION,
                            TextRange(refStart, refStart + fullRef.length)
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val PATH_PARAM_RE = Regex("""\{<[^>]*>(\w+)\}|\{(\w+)\}|:(\w+)""")
    }
}
