package com.github.pablolec.play1toolkit.playconfig.hints

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigTokenTypes
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import java.awt.FlowLayout
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class PlayConfigConfInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key = SettingsKey<NoSettings>("play-v1.config.conf-markers")
    override val name = "Play config property markers"
    override val previewText = "db.url=jdbc:mysql://localhost/db"
    override val group: InlayGroup = InlayGroup.OTHER_GROUP

    override fun createSettings() = NoSettings()

    override fun isLanguageSupported(language: Language): Boolean = language == PlayConfigLanguage

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        if (DumbService.isDumb(file.project)) return null
        if (file.language != PlayConfigLanguage) return null

        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element.node?.elementType != PlayConfigTokenTypes.KEY) return true
                val prop = element.parent as? PlayConfigProperty ?: return true

                val svc = PlayConfigService.getInstance(element.project)
                val markers = buildMarkers(prop, svc)
                if (markers.isEmpty()) return true

                val factory = PresentationFactory(editor)
                sink.addInlineElement(prop.textRange.endOffset, true, factory.smallText("  $markers"), false)
                return true
            }

            private fun buildMarkers(prop: PlayConfigProperty, svc: PlayConfigService): String {
                val parts = mutableListOf<String>()

                if (prop.profile != null) {
                    parts.add("profile: ${prop.profile}")
                    val hasDefault = svc.keysForLogical(prop.logicalKey).any { it.profile == null }
                    if (hasDefault) parts.add("overrides default") else parts.add("no default")
                }

                val envVars = svc.extractEnvVarNames(prop.valueText)
                val runEnv = svc.resolveEnvVarsFromRunConfig()
                for (envVar in envVars) {
                    if (envVar !in runEnv && System.getenv(envVar) == null) {
                        parts.add("ENV unresolved: $envVar")
                    }
                }

                return if (parts.isEmpty()) "" else "// ${parts.joinToString(" · ")}"
            }
        }
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel(FlowLayout())
    }
}
