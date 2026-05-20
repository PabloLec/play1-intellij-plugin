package com.github.pablolec.play1toolkit.lineMarker

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.response.PlayActionResponseService
import com.github.pablolec.play1toolkit.response.PlayResponseIcons
import com.github.pablolec.play1toolkit.response.PlayResponsePresentation
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier

class Play1ActionResponseLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = "Play v1 action response"

    override fun getIcon() = PlayResponseIcons.UNKNOWN

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element !is PsiIdentifier) return
        val method = element.parent as? PsiMethod ?: return
        if (!method.hasModifierProperty(PsiModifier.PUBLIC) || !method.hasModifierProperty(PsiModifier.STATIC)) return
        val containingClass = method.containingClass ?: return
        if (!Play1ViewUtils.isPlayControllerClass(containingClass)) return

        val routes = Play1ViewUtils.findRoutesForAction(element.project, containingClass.name ?: return, method.name)
        if (routes.isEmpty()) return

        val service = PlayActionResponseService.getInstance(element.project)
        if (!service.isPlayActionMethod(method)) return
        val info = service.analyze(method)
        val targets = info.outcomes.map { it.sourceElement }.ifEmpty { listOf(method) }

        result.add(
            NavigationGutterIconBuilder.create(PlayResponseIcons.forKind(info.kind))
                .setTargets(targets)
                .setTooltipText(PlayResponsePresentation.tooltip(info))
                .setPopupTitle("Play v1 response outcomes")
                .createLineMarkerInfo(element)
        )
    }
}
