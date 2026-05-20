package com.github.pablolec.play1toolkit.playmessages.documentation

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesFile
import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.github.pablolec.play1toolkit.playmessages.references.PlayMessagesContextDetector
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.project.DumbService
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.SmartPointerManager

class PlayMessagesDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (DumbService.isDumb(file.project)) return emptyList()
        val element = file.findElementAt(offset) ?: return emptyList()
        val prop = findMessagesProperty(element) ?: return emptyList()
        return listOf(PlayMessagesDocumentationTarget(prop, PlayMessagesService.getInstance(file.project)))
    }

    private fun findMessagesProperty(element: PsiElement): PlayMessagesProperty? {
        val direct = element.parent as? PlayMessagesProperty
        if (direct != null) return direct
        val literal = element.parent as? PsiLiteralExpression ?: return null
        if (!PlayMessagesContextDetector.isMessagesKeyContext(literal)) return null
        return literal.reference?.resolve() as? PlayMessagesProperty
    }
}

private class PlayMessagesDocumentationTarget(
    private val prop: PlayMessagesProperty,
    private val svc: PlayMessagesService
) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val ptr = SmartPointerManager.createPointer(prop)
        return Pointer { ptr.element?.let { PlayMessagesDocumentationTarget(it, svc) } }
    }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(prop.key).presentation()

    override fun computeDocumentation(): DocumentationResult =
        DocumentationResult.documentation(buildHtml())

    private fun buildHtml() = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        append("<b>${prop.key}</b>")
        if (prop.locale != null) append(" <i>[${prop.locale}]</i>")
        append(DocumentationMarkup.DEFINITION_END)

        append(DocumentationMarkup.CONTENT_START)
        val entries = svc.entriesForKey(prop.key)
        append("<table>")
        entries.forEach { e ->
            val loc = e.locale ?: "<i>default</i>"
            append("<tr><td><b>$loc:</b></td><td>${e.value.take(80).escapeHtml()}</td></tr>")
        }
        val usages = try {
            com.intellij.psi.search.searches.ReferencesSearch.search(prop).findAll().size
        } catch (e: Exception) { 0 }
        append("<tr><td><b>Status:</b></td><td>${if (usages > 0) "used ($usages ref${if (usages > 1) "s" else ""})" else "possibly unused"}</td></tr>")
        append("</table>")
        append(DocumentationMarkup.CONTENT_END)
    }

    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
