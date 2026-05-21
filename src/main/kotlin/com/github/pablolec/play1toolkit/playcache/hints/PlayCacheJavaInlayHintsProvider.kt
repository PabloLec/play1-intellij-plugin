package com.github.pablolec.play1toolkit.playcache.hints

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsageKind
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import java.awt.FlowLayout
import javax.swing.JPanel

class PlayCacheJavaInlayHintsProvider : InlayHintsProvider<NoSettings> {
    override val name: String = "Play v1 cache calls"
    override val key: SettingsKey<NoSettings> = SettingsKey("play-v1.cache.java")
    override val group: InlayGroup = InlayGroup.OTHER_GROUP
    override val previewText: String = "Cache.set(\"key\", value, \"10mn\");"
    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        if (file.language != JavaLanguage.INSTANCE) {
            return object : InlayHintsCollector {
                override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean = true
            }
        }
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is PsiMethodCallExpression) return true
                if (!PlayCacheArgExtractor.isCacheCall(element)) return true
                val kind = PlayCacheArgExtractor.methodKind(element) ?: return true
                val label = buildLabel(kind, element)
                val factory = PresentationFactory(editor)
                val text = factory.smallText(" · $label")
                sink.addInlineElement(element.textRange.endOffset, true, text, false)
                return true
            }
        }
    }

    private fun buildLabel(kind: PlayCacheUsageKind, call: PsiMethodCallExpression): String {
        val arguments = call.argumentList.expressions
        val ttl = when (kind) {
            PlayCacheUsageKind.JAVA_WRITE,
            PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT,
            PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT,
            PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> arguments.getOrNull(2)
                ?.let { PlayCacheArgExtractor.extractTtl(it) }
                ?: PlayCacheTtl.Absent
            else -> PlayCacheTtl.Absent
        }
        val verb = when (kind) {
            PlayCacheUsageKind.JAVA_READ -> "cache read"
            PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> "cache read/compute"
            PlayCacheUsageKind.JAVA_WRITE -> "cache write"
            PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT -> "cache add"
            PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT -> "cache replace"
            PlayCacheUsageKind.JAVA_INVALIDATION -> "cache invalidate"
            PlayCacheUsageKind.JAVA_CLEAR -> "cache CLEAR"
            PlayCacheUsageKind.JAVA_MUTATION -> "cache mutate"
            else -> "cache"
        }
        return when (ttl) {
            is PlayCacheTtl.Static -> if (ttl.value.isEmpty()) verb else "$verb · ${ttl.value}"
            is PlayCacheTtl.Dynamic -> "$verb · dynamic ttl"
            PlayCacheTtl.Absent -> verb
        }
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
    }

    override fun isLanguageSupported(language: Language): Boolean = language == JavaLanguage.INSTANCE
}
