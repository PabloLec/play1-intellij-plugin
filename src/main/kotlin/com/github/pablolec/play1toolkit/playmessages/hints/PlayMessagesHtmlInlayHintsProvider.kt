package com.github.pablolec.play1toolkit.playmessages.hints

import com.github.pablolec.play1toolkit.playmessages.references.PlayMessagesHtmlReferenceContributor
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import com.intellij.psi.xml.XmlText
import java.awt.FlowLayout
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class PlayMessagesHtmlInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key = SettingsKey<NoSettings>("play-v1.messages.html-values")
    override val name = "Play message values (HTML)"
    override val previewText = """&{'hello'}"""
    override val group: InlayGroup = InlayGroup.OTHER_GROUP

    override fun createSettings() = NoSettings()

    override fun isLanguageSupported(language: Language): Boolean = language.isKindOf(HTMLLanguage.INSTANCE)

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        if (DumbService.isDumb(file.project)) return null
        val path = file.virtualFile?.path ?: return null
        if (!path.contains("/app/views/")) return null

        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val xmlText = element as? XmlText ?: return true
                val text = xmlText.text
                val svc = PlayMessagesService.getInstance(element.project)
                val factory = PresentationFactory(editor)

                PlayMessagesHtmlReferenceContributor.MESSAGES_PATTERN.findAll(text).forEach { match ->
                    val key = match.groupValues[1]
                    if (key.isBlank()) return@forEach
                    val hint = buildHintText(svc, key)
                    val absOffset = xmlText.textRange.startOffset + match.range.last + 1
                    sink.addInlineElement(absOffset, true, factory.smallText(hint), false)
                }
                return true
            }

            private fun buildHintText(svc: PlayMessagesService, key: String): String {
                val entries = svc.entriesForKey(key)
                if (entries.isEmpty()) return " /* unresolved message key */"
                val displayEntry = entries.firstOrNull { it.locale == null } ?: entries.first()
                val value = displayEntry.value
                val display = if (value.length > 40) value.take(37) + "..." else value
                val extra = if (entries.size > 1) " [${entries.size} locales]" else ""
                return " /* = $display$extra */"
            }
        }
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel(FlowLayout())
    }
}
