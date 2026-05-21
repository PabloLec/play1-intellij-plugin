package com.github.pablolec.play1toolkit.playcache.lineMarker

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod

class PlayCacheActionLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        val project = elements.first().project
        if (DumbService.isDumb(project)) return
        val service = PlayCacheService.getInstance(project)

        for (element in elements) {
            if (element !is PsiIdentifier) continue
            val method = element.parent as? PsiMethod ?: continue
            if (method.nameIdentifier !== element) continue
            val info = service.findCachedAction(method) ?: continue
            val tooltip = "Cached Play action · ${ttlLabel(info.ttl)}"
            result.add(
                LineMarkerInfo(
                    element,
                    element.textRange,
                    AllIcons.Actions.MenuSaveall,
                    { tooltip },
                    null,
                    GutterIconRenderer.Alignment.LEFT,
                    { tooltip }
                )
            )
        }
    }

    private fun ttlLabel(ttl: PlayCacheTtl): String = when (ttl) {
        is PlayCacheTtl.Static -> if (ttl.value.isEmpty()) "no expiration" else ttl.value
        is PlayCacheTtl.Dynamic -> "dynamic ttl"
        PlayCacheTtl.Absent -> "no expiration"
    }
}
