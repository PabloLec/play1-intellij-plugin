package com.github.pablolec.play1toolkit.templates.inspection

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlText

class PlayTemplateTagBalanceInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Unbalanced Play v1 template tag"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayTemplateTagBalance"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (DumbService.isDumb(file.project)) return
                val text = file.text ?: return

                val matches = mutableListOf<Pair<String, MatchResult>>()
                PlayTemplatePatterns.TAG_SELF_CLOSE.findAll(text).forEach { matches += "self" to it }
                PlayTemplatePatterns.TAG_OPEN.findAll(text).forEach { matches += "open" to it }
                PlayTemplatePatterns.TAG_CLOSE.findAll(text).forEach { matches += "close" to it }

                // stack: tagName, absolute offset of the #{tagname} in the file
                val stack = mutableListOf<Pair<String, Int>>()

                matches.sortedBy { it.second.range.first }.forEach { (kind, match) ->
                    when (kind) {
                        "self" -> Unit
                        "open" -> {
                            val tagName = match.groupValues[1]
                            if (match.value.trimEnd().endsWith("/}")) return@forEach
                            stack += tagName to match.range.first
                        }
                        "close" -> {
                            val tagName = match.groupValues[1]
                            if (tagName == "if") {
                                while (stack.lastOrNull()?.first in setOf("else", "elseif")) {
                                    stack.removeLast()
                                }
                            }
                            val last = stack.lastOrNull()
                            if (last == null || last.first != tagName) {
                                registerAt(file, holder, match.range.first + 3, tagName,
                                    "Unexpected closing Play tag: #{$tagName}")
                            } else {
                                stack.removeLast()
                            }
                        }
                    }
                }

                stack.forEach { (tagName, absOffset) ->
                    registerAt(file, holder, absOffset + 2, tagName,
                        "Unclosed Play tag: #{$tagName}")
                }
            }
        }
    }

    private fun registerAt(file: PsiFile, holder: ProblemsHolder, nameAbsOffset: Int, tagName: String, message: String) {
        val leaf = file.findElementAt(nameAbsOffset) ?: return
        val xmlText = PsiTreeUtil.getParentOfType(leaf, XmlText::class.java)
        val anchor: PsiElement = xmlText ?: leaf
        val anchorStart = anchor.textRange.startOffset
        val relStart = nameAbsOffset - anchorStart
        val relEnd = relStart + tagName.length
        if (relStart < 0 || relEnd > anchor.textLength) return
        holder.registerProblem(anchor, message, ProblemHighlightType.WEAK_WARNING,
            TextRange(relStart, relEnd))
    }
}
