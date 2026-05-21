package com.github.pablolec.play1toolkit.playcache.hints

import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateScanner
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateValueResolver
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
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlText
import java.awt.FlowLayout
import javax.swing.JPanel

class PlayCacheTemplateInlayHintsProvider : InlayHintsProvider<NoSettings> {
    override val name: String = "Play v1 cache fragments"
    override val key: SettingsKey<NoSettings> = SettingsKey("play-v1.cache.template")
    override val group: InlayGroup = InlayGroup.OTHER_GROUP
    override val previewText: String = "#{cache 'home', for:'10mn'} ... #{/cache}"
    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        if (!PlayCacheTemplateScanner.isEligible(file)) {
            return object : InlayHintsCollector {
                override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean = true
            }
        }
        val fragments = PlayCacheService.getInstance(file.project).getTemplateFragments()
            .filter { it.templateFile == file }
        if (fragments.isEmpty()) {
            return object : InlayHintsCollector {
                override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean = true
            }
        }
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is XmlText) return true
                val xmlRange = element.textRange
                fragments.forEach { fragment ->
                    if (!xmlRange.contains(fragment.openTagRange.startOffset)) return@forEach
                    val factory = PresentationFactory(editor)
                    val label = buildLabel(file.project, fragment)
                    val text = factory.smallText(" $label")
                    sink.addInlineElement(fragment.openTagRange.endOffset, true, text, false)
                }
                return true
            }
        }
    }

    private fun buildLabel(project: com.intellij.openapi.project.Project, fragment: com.github.pablolec.play1toolkit.playcache.model.PlayCachedTemplateFragment): String {
        val guard = PlayCacheTemplateValueResolver.resolveGuard(project, fragment.templateFile)
        val keyLabel = PlayCacheTemplateValueResolver.resolveKey(project, fragment).displayText
        val ttlLabel = PlayCacheTemplateValueResolver.resolveTtl(project, fragment).displayText
        val prefix = if (guard?.booleanValue == false) "CACHE OFF" else "CACHE"
        return "$prefix $keyLabel · $ttlLabel"
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
    }

    override fun isLanguageSupported(language: Language): Boolean =
        language == HTMLLanguage.INSTANCE || language == XMLLanguage.INSTANCE
}
