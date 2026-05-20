package com.github.pablolec.play1toolkit.templates.inspection

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlText

class PlayTemplateMissingAssetInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Missing Play v1 static asset"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayTemplateMissingAsset"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                val xmlText = element as? XmlText ?: return
                PlayTemplatePatterns.STATIC_ASSET.findAll(xmlText.text).forEach { match ->
                    val path = match.groupValues[1]
                    if (!path.startsWith("/public/") && !path.startsWith("public/")) return@forEach
                    if (PlayTemplateFileUtils.resolvePublicAsset(holder.project, path) != null) return@forEach
                    val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                    if (quotePos < 0) return@forEach
                    val start = match.range.first + quotePos + 1
                    holder.registerProblem(
                        xmlText,
                        "Static asset not found: $path",
                        ProblemHighlightType.WARNING,
                        TextRange(start, start + path.length)
                    )
                }
            }
        }
    }
}
