package com.github.pablolec.play1toolkit.playmessages.inspections

import com.github.pablolec.play1toolkit.playmessages.references.PlayMessagesHtmlReferenceContributor
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlText

class UnknownPlayMessageKeyHtmlInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : XmlElementVisitor() {
            override fun visitXmlText(text: XmlText) {
                if (DumbService.isDumb(text.project)) return
                val path = text.containingFile?.virtualFile?.path ?: return
                if (!path.contains("/app/views/")) return
                val svc = PlayMessagesService.getInstance(text.project)
                PlayMessagesHtmlReferenceContributor.MESSAGES_PATTERN.findAll(text.text).forEach { match ->
                    val key = match.groupValues[1]
                    if (key.isNotBlank() && svc.entriesForKey(key).isEmpty()) {
                        // Find the text range of the key within the xml text element
                        val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                        if (quotePos >= 0) {
                            val keyStart = match.range.first + quotePos + 1
                            val keyEnd = keyStart + key.length
                            // Register on the entire match expression for visibility
                            val matchStart = match.range.first
                            val matchEnd = match.range.last + 1
                            try {
                                val rangeInElement = com.intellij.openapi.util.TextRange(matchStart, matchEnd)
                                holder.registerProblem(
                                    text,
                                    rangeInElement,
                                    "Unknown Play message key '$key'"
                                )
                            } catch (e: Exception) {
                                // Range may be out of bounds in some edge cases — skip silently
                            }
                        }
                    }
                }
            }
        }
    }
}
