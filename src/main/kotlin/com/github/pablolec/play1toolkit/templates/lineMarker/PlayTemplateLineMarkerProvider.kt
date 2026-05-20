package com.github.pablolec.play1toolkit.templates.lineMarker

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class PlayTemplateLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (DumbService.isDumb(element.project)) return
        val file = element.containingFile ?: return
        val virtualFile = file.virtualFile ?: return
        if (!PlayTemplateFileUtils.isPlayTemplateFile(virtualFile)) return
        if (element.textRange.startOffset != 0) return

        val service = PlayTemplateService.getInstance(element.project)

        val actions = service.findLikelyRenderingMethods(virtualFile)
        if (actions.isNotEmpty()) {
            val builder = NavigationGutterIconBuilder.create(AllIcons.Actions.Forward)
                .setTargets(actions)
                .setTooltipText(
                    if (actions.size == 1) "Go to rendering action"
                    else "Go to rendering actions"
                )
            result.add(builder.createLineMarkerInfo(element))
        }

        if (PlayTemplateFileUtils.isInTagsDirectory(virtualFile)) {
            val usages = try {
                com.intellij.psi.search.searches.ReferencesSearch.search(file as PsiFile).findAll()
            } catch (_: Exception) {
                emptySet()
            }
            if (usages.isNotEmpty()) {
                val builder = NavigationGutterIconBuilder.create(AllIcons.Nodes.Related)
                    .setTargets(usages.mapNotNull { it.element })
                    .setTooltipText("${usages.size} tag usage${if (usages.size > 1) "s" else ""}")
                result.add(builder.createLineMarkerInfo(element))
            }
        }
    }
}
