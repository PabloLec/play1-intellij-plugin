package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

class RoutesAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element.node?.elementType) {
            RoutesTokenTypes.CONTROLLER_NAME -> annotateController(element, holder)
            RoutesTokenTypes.ACTION_NAME -> annotateAction(element, holder)
        }
    }

    private fun annotateController(element: PsiElement, holder: AnnotationHolder) {
        val name = element.text.trim()
        if (name.isEmpty() || name.contains('{')) return

        if (RoutesControllerResolver.resolveClass(element.project, name) == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Controller not found: $name")
                .range(element)
                .create()
        }
    }

    private fun annotateAction(element: PsiElement, holder: AnnotationHolder) {
        val actionName = element.text.trim()
        if (actionName.isEmpty() || actionName.contains('.')) return
        val routeElement = element.parent as? RoutesRouteElement ?: return
        val controllerName = routeElement.getControllerName()?.text?.trim() ?: return
        if (controllerName.contains('{')) return

        val psiClass = RoutesControllerResolver.resolveClass(element.project, controllerName) ?: return

        if (psiClass.findMethodsByName(actionName, true).isEmpty()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Action not found: $controllerName.$actionName")
                .range(element)
                .create()
        }
    }
}
