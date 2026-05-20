package com.github.pablolec.play1toolkit.playmessages.lineMarker

import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesTokenTypes
import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch

class PlayMessagesLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.node?.elementType != PlayMessagesTokenTypes.KEY) return
        val prop = element.parent as? PlayMessagesProperty ?: return
        if (DumbService.isDumb(element.project)) return

        val svc = PlayMessagesService.getInstance(element.project)

        // Gutter icon for default key with locale translations → navigate to all locale variants
        if (prop.locale == null) {
            val localeVariants = svc.entriesForKey(prop.key).filter { it.locale != null }
            if (localeVariants.isNotEmpty()) {
                val builder = NavigationGutterIconBuilder.create(AllIcons.Actions.InlayGlobe)
                    .setTargets(localeVariants.map { it.property })
                    .setTooltipText("${localeVariants.size} locale translation(s)")
                result.add(builder.createLineMarkerInfo(element))
            }
        }

        // Gutter icon for Java + HTML usages
        val usages = try {
            ReferencesSearch.search(prop).findAll()
        } catch (e: Exception) {
            return
        }
        if (usages.isNotEmpty()) {
            val targets = usages.mapNotNull { it.element }
            val builder = NavigationGutterIconBuilder.create(AllIcons.Nodes.Related)
                .setTargets(targets)
                .setTooltipText("${usages.size} usage${if (usages.size > 1) "s" else ""}")
            result.add(builder.createLineMarkerInfo(element))
        }
    }
}
