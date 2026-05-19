package com.github.pablolec.play1toolkit.lineMarker

import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.routes.RoutesTokenTypes
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement

class Play1RoutesLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.node?.elementType != RoutesTokenTypes.CONTROLLER_NAME) return
        val routeElement = element.parent as? RoutesRouteElement ?: return
        if (!routeElement.isDynamicRoute()) return

        val controllerName = element.text.trim()
        if (controllerName.isEmpty() || controllerName.contains('{')) return
        val actionName = routeElement.getActionName()?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return

        val project = element.project
        val method = RoutesControllerResolver.resolveMethod(project, controllerName, actionName) ?: return
        val psiClass = method.containingClass ?: return

        result.add(
            NavigationGutterIconBuilder.create(AllIcons.General.ArrowLeft)
                .setTargets(listOf(method))
                .setTooltipText("Go to ${psiClass.name}.$actionName()")
                .createLineMarkerInfo(element)
        )
    }
}
