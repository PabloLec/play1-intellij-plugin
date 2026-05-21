package com.github.pablolec.play1toolkit.playcache.lineMarker

import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateScanner
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateValueResolver
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlText

class PlayCacheTemplateLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is XmlText) return null
        val file = element.containingFile ?: return null
        if (!PlayCacheTemplateScanner.isEligible(file)) return null
        if (!element.text.contains("#{cache")) return null

        val service = PlayCacheService.getInstance(element.project)
        val fragments = service.getTemplateFragments().filter { it.templateFile == file }
        if (fragments.isEmpty()) return null

        val xmlRange = element.textRange
        val fragment = fragments.firstOrNull { xmlRange.contains(it.openTagRange.startOffset) } ?: return null
        val tooltip = buildTooltip(element.project, fragment)
        return LineMarkerInfo(
            element,
            fragment.openTagRange,
            AllIcons.Actions.MenuSaveall,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { tooltip }
        )
    }

    private fun buildTooltip(
        project: com.intellij.openapi.project.Project,
        fragment: com.github.pablolec.play1toolkit.playcache.model.PlayCachedTemplateFragment
    ): String {
        val guard = PlayCacheTemplateValueResolver.resolveGuard(project, fragment.templateFile)
        val keyLabel = PlayCacheTemplateValueResolver.resolveKey(project, fragment).displayText
        val ttlLabel = PlayCacheTemplateValueResolver.resolveTtl(project, fragment).displayText
        val state = if (guard?.booleanValue == false) "disabled" else "enabled or dynamic"
        return "Play template cache fragment · $state · key $keyLabel · ttl $ttlLabel"
    }

    @Suppress("unused")
    private fun anyXmlFile(file: PsiFile): Boolean = true
}
