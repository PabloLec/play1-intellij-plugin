package com.github.pablolec.play1toolkit.templates.inspection

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateVariableResolver
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlText

class PlayTemplateUnknownVariableInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Unknown Play v1 template variable"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayTemplateUnknownVariable"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        val knownVariables = PlayTemplateVariableResolver.getInstance(holder.project).resolveVariables(holder.file)

        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                val xmlText = element as? XmlText ?: return
                PlayTemplatePatterns.GROOVY_EXPR.findAll(xmlText.text).forEach { match ->
                    val variable = match.groupValues[1]
                    if (variable in knownVariables) return@forEach
                    val start = match.range.first + 2
                    holder.registerProblem(
                        xmlText,
                        "Unknown template variable: $variable",
                        ProblemHighlightType.WEAK_WARNING,
                        TextRange(start, start + variable.length)
                    )
                }
            }
        }
    }
}
