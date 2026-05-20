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

class PlayTemplateTagBalanceInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Unbalanced Play v1 template tag"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "Play1TemplateTagBalance"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                val xmlText = element as? XmlText ?: return
                val text = xmlText.text
                val stack = mutableListOf<Pair<String, IntRange>>()

                val matches = mutableListOf<Pair<String, MatchResult>>()
                PlayTemplatePatterns.TAG_SELF_CLOSE.findAll(text).forEach { matches += "self" to it }
                PlayTemplatePatterns.TAG_OPEN.findAll(text).forEach { matches += "open" to it }
                PlayTemplatePatterns.TAG_CLOSE.findAll(text).forEach { matches += "close" to it }

                matches.sortedBy { it.second.range.first }.forEach { (kind, match) ->
                    when (kind) {
                        "self" -> Unit
                        "open" -> {
                            val tagName = match.groupValues[1]
                            if (tagName == "else" || tagName == "elseif") return@forEach
                            // Ignore open tags that are actually self-closing.
                            if (match.value.trimEnd().endsWith("/}")) return@forEach
                            stack += tagName to match.range
                        }
                        "close" -> {
                            val tagName = match.groupValues[1]
                            val last = stack.lastOrNull()
                            if (last == null || last.first != tagName) {
                                holder.registerProblem(
                                    xmlText,
                                    "Unexpected closing Play tag: #{$tagName}",
                                    ProblemHighlightType.WEAK_WARNING,
                                    TextRange(match.range.first + 3, match.range.first + 3 + tagName.length)
                                )
                            } else {
                                stack.removeLast()
                            }
                        }
                    }
                }

                stack.forEach { (tagName, range) ->
                    holder.registerProblem(
                        xmlText,
                        "Unclosed Play tag: #{$tagName}",
                        ProblemHighlightType.WEAK_WARNING,
                        TextRange(range.first + 2, range.first + 2 + tagName.length)
                    )
                }
            }
        }
    }
}
