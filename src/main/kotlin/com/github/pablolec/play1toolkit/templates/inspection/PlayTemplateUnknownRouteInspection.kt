package com.github.pablolec.play1toolkit.templates.inspection

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.xml.XmlText

class PlayTemplateUnknownRouteInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Unknown Play v1 reverse route"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayTemplateUnknownRoute"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                val xmlText = element as? XmlText ?: return
                val refs = xmlText.references.filterIsInstance<com.github.pablolec.play1toolkit.templates.references.PlayTemplateRouteReference>()
                if (refs.isNotEmpty()) {
                    refs.filter { it.resolve() == null }.forEach { ref ->
                        holder.registerProblem(
                            xmlText,
                            "Unknown reverse route target",
                            ProblemHighlightType.WEAK_WARNING,
                            ref.rangeInElement
                        )
                    }
                    return
                }

                // Fallback when reference stitching has not attached to the current leaf.
                sequenceOf(PlayTemplatePatterns.REVERSE_ROUTE, PlayTemplatePatterns.BARE_ACTION_REF).forEach { pattern ->
                    pattern.findAll(xmlText.text).forEach { match ->
                        val fullRef = match.groupValues[1]
                        if (fullRef.isBlank()) return@forEach
                        val absoluteStart = xmlText.textRange.startOffset + match.range.first
                        val routeElement = holder.file.findElementAt(absoluteStart) ?: return@forEach
                        val routeRef = routeElement.references.filterIsInstance<PsiReference>()
                            .firstOrNull { it.rangeInElement.length == fullRef.length || it.canonicalText.contains(fullRef) }
                        if (routeRef?.resolve() == null) {
                            val refStart = match.range.first + match.value.indexOf(fullRef)
                            holder.registerProblem(
                                xmlText,
                                "Unknown reverse route target",
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
