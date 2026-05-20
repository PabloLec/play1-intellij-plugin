package com.github.pablolec.play1toolkit.playjpa.hints

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
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
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import java.awt.FlowLayout
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class PlayJpaFinderInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("play.jpa.finder.hints")
    override val name: String = "Play JPA finder return type hints"
    override val previewText: String = "User.findById(id)"
    override val group: InlayGroup = InlayGroup.OTHER_GROUP

    override fun isLanguageSupported(language: Language): Boolean = language == JavaLanguage.INSTANCE

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel(FlowLayout())
    }

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
                if (element !is PsiMethodCallExpression) return true
                val project = element.project
                val svc = PlayJpaModelService.getInstance(project)
                val methodName = element.methodExpression.referenceName ?: return true
                val qualText = element.methodExpression.qualifierExpression?.text?.trim() ?: return true
                val model = svc.findModelByName(qualText) ?: return true

                val hint = when (methodName) {
                    "findById", "first" -> " /* ${model.className} */"
                    "findAll", "fetch" -> " /* List<${model.className}> */"
                    "count" -> " /* long */"
                    else -> return true
                }

                val factory = PresentationFactory(editor)
                sink.addInlineElement(element.textRange.endOffset, true, factory.smallText(hint), false)
                return true
            }
        }
    }
}
