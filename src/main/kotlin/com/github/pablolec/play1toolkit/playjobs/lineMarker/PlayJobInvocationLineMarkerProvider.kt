package com.github.pablolec.play1toolkit.playjobs.lineMarker

import com.github.pablolec.play1toolkit.playjobs.service.PlayJobService
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiNewExpression

class PlayJobInvocationLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element !is PsiIdentifier) return
        val refName = element.parent as? PsiJavaCodeReferenceElement ?: return
        if (refName.referenceNameElement !== element) return
        val newExpression = refName.parent as? PsiNewExpression ?: return
        if (newExpression.classReference !== refName) return

        val project = element.project
        if (DumbService.isDumb(project)) return
        val resolved = refName.resolve() as? PsiClass ?: return
        val service = PlayJobService.getInstance(project)
        val info = service.findJobForClass(resolved) ?: return

        result.add(
            NavigationGutterIconBuilder.create(AllIcons.Actions.RunAll)
                .setTargets(listOf(info.psiClass))
                .setTooltipText("Manual Play job invocation\nTarget: ${info.className}")
                .createLineMarkerInfo(element)
        )
    }
}
