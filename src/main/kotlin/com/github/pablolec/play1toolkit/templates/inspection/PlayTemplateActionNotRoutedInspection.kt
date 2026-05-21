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

class PlayTemplateActionNotRoutedInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Play v1 action not declared in conf/routes"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayTemplateActionNotRouted"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val xmlText = element as? XmlText ?: return
                sequenceOf(PlayTemplatePatterns.REVERSE_ROUTE, PlayTemplatePatterns.BARE_ACTION_REF).forEach { pattern ->
                    pattern.findAll(xmlText.text).forEach { match ->
                        val fullRef = match.groupValues[1]
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
                        if (routes.isEmpty()) {
                            val refStart = match.range.first + match.value.indexOf(fullRef)
                            holder.registerProblem(
                                xmlText,
                                "Action '$fullRef' exists but is not declared in conf/routes",
                                ProblemHighlightType.WEAK_WARNING,
                                TextRange(refStart, refStart + fullRef.length)
                            )
                        }
                    }
                }
            }
        }
    }
}
