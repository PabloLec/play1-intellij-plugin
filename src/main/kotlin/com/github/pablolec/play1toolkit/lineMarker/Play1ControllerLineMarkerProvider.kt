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

        when (val parent = element.parent) {
            is PsiClass -> collectClassMarker(element, parent, result)
            is PsiMethod -> collectMethodMarker(element, parent, result)
        }
    }

    private fun collectClassMarker(
        identifier: PsiIdentifier,
        psiClass: PsiClass,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (!Play1ViewUtils.isPlayControllerClass(psiClass)) return
        val controllerName = psiClass.name ?: return
        val routes = Play1ViewUtils.findAllRoutesForController(identifier.project, controllerName)
        if (routes.isEmpty()) return

        result.add(
            NavigationGutterIconBuilder.create(AllIcons.General.ArrowRight)
                .setTargets(routes)
                .setTooltipText("Go to routes for $controllerName (${routes.size})")
                .createLineMarkerInfo(identifier)
        )
    }

    private fun collectMethodMarker(
        identifier: PsiIdentifier,
        method: PsiMethod,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC) || !method.hasModifierProperty(PsiModifier.STATIC)) return
        val containingClass = method.containingClass ?: return
        if (!Play1ViewUtils.isPlayControllerClass(containingClass)) return

        val controllerName = containingClass.name ?: return
        val routes = Play1ViewUtils.findRoutesForAction(identifier.project, controllerName, method.name)
        if (routes.isEmpty()) return

        val tooltip = routes.joinToString("\n") { route ->
            "${route.getHttpMethod()?.text?.trim() ?: "?"} ${route.getPath() ?: "?"}"
        }

        result.add(
            NavigationGutterIconBuilder.create(AllIcons.General.ArrowRight)
                .setTargets(routes)
                .setTooltipText("Go to route: $tooltip")
                .createLineMarkerInfo(identifier)
        )
    }
}
