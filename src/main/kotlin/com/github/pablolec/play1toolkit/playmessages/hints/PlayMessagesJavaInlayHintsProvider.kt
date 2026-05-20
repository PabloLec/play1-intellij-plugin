package com.github.pablolec.play1toolkit.playmessages.hints

import com.github.pablolec.play1toolkit.playmessages.references.PlayMessagesContextDetector
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import java.awt.FlowLayout
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class PlayMessagesJavaInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key = SettingsKey<NoSettings>("play-v1.messages.java-values")
    override val name = "Play message values"
    override val previewText = """Messages.get("hello")"""
    override val group: InlayGroup = InlayGroup.OTHER_GROUP

    override fun createSettings() = NoSettings()

    override fun isLanguageSupported(language: Language): Boolean = language == JavaLanguage.INSTANCE

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        if (DumbService.isDumb(file.project)) return null
        if (file.language != JavaLanguage.INSTANCE) return null

        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val literal = element as? PsiLiteralExpression ?: return true
                val key = literal.value as? String ?: return true
                if (key.isBlank()) return true
                if (!PlayMessagesContextDetector.isMessagesKeyContext(literal)) return true

                val svc = PlayMessagesService.getInstance(element.project)
                val hint = buildHintText(svc, key)

                val factory = PresentationFactory(editor)
                sink.addInlineElement(literal.textRange.endOffset, true, factory.smallText(hint), false)
                return true
            }

            private fun buildHintText(svc: PlayMessagesService, key: String): String {
                val entries = svc.entriesForKey(key)
                if (entries.isEmpty()) return " /* unresolved message key */"
                val defaultEntry = entries.firstOrNull { it.locale == null }
                if (defaultEntry != null) {
                    val value = defaultEntry.value
                    val display = if (value.length > 40) value.take(37) + "..." else value
                    val extra = if (entries.size > 1) " [${entries.size} locales]" else ""
                    return " /* = $display$extra */"
                }
                return " /* ${entries.size} locale(s) */"
            }
        }
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel(FlowLayout())
    }
}
