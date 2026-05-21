package com.github.pablolec.play1toolkit.playcache.inspection

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateScanner
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlText

class PlayCacheTagMissingKeyInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayCacheTemplateScanner.isEligible(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        val fragments = PlayCacheService.getInstance(holder.project).getTemplateFragments()
            .filter { it.templateFile == holder.file && it.key is PlayCacheKey.Missing }
        if (fragments.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is XmlText) return
                val xmlRange = element.textRange
                fragments.forEach { fragment ->
                    if (!xmlRange.contains(fragment.openTagRange.startOffset)) return@forEach
                    val rel = TextRange(
                        fragment.openTagRange.startOffset - xmlRange.startOffset,
                        fragment.openTagRange.endOffset - xmlRange.startOffset
                    )
                    holder.registerProblem(
                        element,
                        "Play #{cache} tag has no key",
                        ProblemHighlightType.WEAK_WARNING,
                        rel
                    )
                }
            }
        }
    }
}
