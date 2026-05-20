package com.github.pablolec.play1toolkit.templates.inspection

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInspection.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import com.intellij.psi.xml.XmlText
import com.intellij.openapi.util.TextRange

class PlayTemplateUnknownTagInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Unknown Play 1 template tag"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayTemplateUnknownTag"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val xmlText = element as? XmlText ?: return
                val text = xmlText.text
                val svc = PlayTemplateService.getInstance(holder.project)
                val customTagNames = svc.getAllCustomTags().flatMap { listOf(it.qualifiedName, it.name) }.toSet()

                PlayTemplatePatterns.TAG_NAME_AT.findAll(text).forEach { match ->
                    val tagName = match.groupValues[1]
                    if (tagName !in PlayTemplatePatterns.BUILTIN_TAGS && tagName !in customTagNames) {
                        val nameStart = match.range.first + 2
                        val nameEnd = nameStart + tagName.length
                        if (nameEnd <= xmlText.textLength) {
                            holder.registerProblem(
                                xmlText,
                                "Unknown Play tag: #{$tagName}",
                                ProblemHighlightType.WEAK_WARNING,
                                TextRange(nameStart, nameEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}
