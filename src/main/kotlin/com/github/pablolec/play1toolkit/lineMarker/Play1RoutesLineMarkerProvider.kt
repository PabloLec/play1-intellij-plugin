package com.github.pablolec.play1toolkit.lineMarker

import com.github.pablolec.play1toolkit.routes.RoutesTokenTypes
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

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
        val scope = GlobalSearchScope.projectScope(project)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(controllerName, scope)
            ?: PsiShortNamesCache.getInstance(project).getClassesByName(controllerName, scope).firstOrNull()
            ?: return

        val method = psiClass.findMethodsByName(actionName, true).firstOrNull() ?: return

        val builder = NavigationGutterIconBuilder.create(AllIcons.General.ArrowLeft)
            .setTargets(listOf(method))
            .setTooltipText("Go to ${psiClass.name}.$actionName()")

        result.add(builder.createLineMarkerInfo(element))
    }
}
