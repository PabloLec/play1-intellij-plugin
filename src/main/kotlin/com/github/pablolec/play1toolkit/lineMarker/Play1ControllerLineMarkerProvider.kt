package com.github.pablolec.play1toolkit.lineMarker

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.*

class Play1ControllerLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element !is PsiIdentifier) return
        val method = element.parent as? PsiMethod ?: return
        if (!method.hasModifierProperty(PsiModifier.PUBLIC) || !method.hasModifierProperty(PsiModifier.STATIC)) return

        val containingClass = method.containingClass ?: return
        if (!Play1ViewUtils.isPlayController(containingClass)) return

        val controllerName = containingClass.name ?: return
        val routes = Play1ViewUtils.findRoutesForAction(element.project, controllerName, method.name)
        if (routes.isEmpty()) return

        val tooltip = routes.joinToString("\n") { route ->
            "${route.getHttpMethod()?.text ?: "?"} ${route.getPath() ?: "?"}"
        }

        val builder = NavigationGutterIconBuilder.create(AllIcons.General.ArrowRight)
            .setTargets(routes)
            .setTooltipText("Go to route: $tooltip")

        result.add(builder.createLineMarkerInfo(element))
    }
}
