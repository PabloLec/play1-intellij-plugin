package com.github.pablolec.play1toolkit.playconfig.lineMarker

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigTokenTypes
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch

class PlayConfigLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.node?.elementType != PlayConfigTokenTypes.KEY) return
        val prop = element.parent as? PlayConfigProperty ?: return
        if (DumbService.isDumb(element.project)) return

        val svc = PlayConfigService.getInstance(element.project)

        // Gutter icon for profiled properties → navigate to default key
        if (prop.profile != null) {
            val defaultKey = svc.keysForLogical(prop.logicalKey).firstOrNull { it.profile == null }
            if (defaultKey != null) {
                val builder = NavigationGutterIconBuilder.create(AllIcons.General.OverridingMethod)
                    .setTargets(listOf(defaultKey.property))
                    .setTooltipText("Overrides default: ${prop.logicalKey}")
                result.add(builder.createLineMarkerInfo(element))
            }
        }

        // Gutter icon for keys with Java usages
        val usages = try {
            ReferencesSearch.search(prop).findAll()
        } catch (e: Exception) {
            return
        }

        if (usages.isNotEmpty()) {
            val targets = usages.mapNotNull { it.element }
            val builder = NavigationGutterIconBuilder.create(AllIcons.Nodes.Related)
                .setTargets(targets)
                .setTooltipText("${usages.size} Java usage${if (usages.size > 1) "s" else ""}")
            result.add(builder.createLineMarkerInfo(element))
        }

    }
}
