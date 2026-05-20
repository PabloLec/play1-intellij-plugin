package com.github.pablolec.play1toolkit.playconfig.hints

import com.github.pablolec.play1toolkit.playconfig.references.PlayConfigContextDetector
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
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
class PlayConfigJavaInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key = SettingsKey<NoSettings>("play-v1.config.java-values")
    override val name = "Play config values"
    override val previewText = """Play.configuration.getProperty("application.mode")"""
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
                val value = literal.value as? String ?: return true
                if (value.isBlank() || !value.contains('.')) return true
                if (!PlayConfigContextDetector.isConfigKeyContext(literal)) return true

                val svc = PlayConfigService.getInstance(element.project)
                val resolution = svc.resolve(value)
                val hint = buildHintText(resolution, svc, value)

                val factory = PresentationFactory(editor)
                sink.addInlineElement(literal.textRange.endOffset, true, factory.smallText(hint), false)
                return true
            }

            private fun buildHintText(
                resolution: com.github.pablolec.play1toolkit.playconfig.model.PlayConfigResolution,
                svc: PlayConfigService,
                key: String
            ): String {
                if (resolution.effectiveValue == null && resolution.defaultValue == null && resolution.profileValue == null) {
                    return " /* unresolved config key */"
                }

                if (resolution.activeProfile == null) {
                    val profilesForKey = svc.keysForLogical(key).mapNotNull { it.profile }
                    if (profilesForKey.isNotEmpty()) {
                        val defaultDisplay = resolution.defaultValue?.value?.take(20) ?: "—"
                        return " /* default: $defaultDisplay · overrides: ${profilesForKey.joinToString(", ")} */"
                    }
                }

                return " /* = ${svc.displayValue(resolution)} */"
            }
        }
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel(FlowLayout())
    }
}
