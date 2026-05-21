package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.response.PlayActionResponseService
import com.github.pablolec.play1toolkit.response.PlayResponseIcons
import com.github.pablolec.play1toolkit.response.PlayResponsePresentation
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
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
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.FlowLayout
import javax.swing.JPanel

class Play1RoutesResponseInlayHintsProvider : InlayHintsProvider<NoSettings> {
    override val name: String = "Play v1 route responses"

    override val key: SettingsKey<NoSettings> = SettingsKey("play-v1.route.responses")

    override val previewText: String = """
        GET /users/{id} Users.show
        POST /api/users Users.create
    """.trimIndent()

    override fun createSettings(): NoSettings = NoSettings()

    override val group: InlayGroup = InlayGroup.OTHER_GROUP

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        if (file.language != RoutesLanguage) {
            return object : InlayHintsCollector {
                override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean = true
            }
        }

        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element.node?.elementType != RoutesTokenTypes.ACTION_NAME) return true
                val route = element.parent as? RoutesRouteElement ?: return true
                if (!route.isDynamicRoute()) return true

                val controllerName = route.getControllerName()?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return true
                val actionName = element.text.trim().takeIf { it.isNotEmpty() } ?: return true
                val method = RoutesControllerResolver.resolveMethod(file.project, controllerName, actionName) ?: return true
                val info = PlayActionResponseService.getInstance(file.project).analyze(method)

                val factory = PresentationFactory(editor)
                val icon = factory.smallScaledIcon(PlayResponseIcons.forKind(info.kind))
                val cachedSuffix = cachedActionSuffix(file.project, method)
                val text = factory.smallText(" ${PlayResponsePresentation.shortLabel(info.kind)}$cachedSuffix")
                val content = factory.seq(icon, text)
                val clickable = factory.psiSingleReference(content) { method }
                val presentation = factory.withTooltip(PlayResponsePresentation.tooltip(info), clickable)
                sink.addInlineElement(element.textRange.endOffset, true, presentation, false)
                return true
            }
        }
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: com.intellij.codeInsight.hints.ChangeListener) =
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
    }

    override fun isLanguageSupported(language: Language): Boolean = language == RoutesLanguage

    private fun cachedActionSuffix(project: com.intellij.openapi.project.Project, method: com.intellij.psi.PsiMethod): String {
        val info = runCatching { PlayCacheService.getInstance(project).findCachedAction(method) }.getOrNull() ?: return ""
        val ttl = when (val t = info.ttl) {
            is PlayCacheTtl.Static -> if (t.value.isEmpty()) "no expiration" else t.value
            is PlayCacheTtl.Dynamic -> "dynamic ttl"
            PlayCacheTtl.Absent -> "no expiration"
        }
        return " · cached $ttl"
    }
}
