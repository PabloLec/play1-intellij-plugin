package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

class RoutesAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element.node?.elementType) {
            RoutesTokenTypes.CONTROLLER_NAME -> annotateController(element, holder)
            RoutesTokenTypes.ACTION_NAME -> annotateAction(element, holder)
        }
    }

    private fun annotateController(element: PsiElement, holder: AnnotationHolder) {
        val name = element.text.trim()
        // Skip dynamic route placeholders like {controller}
        if (name.isEmpty() || name.contains('{')) return

        if (!findClass(element, name)) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Controller not found: $name")
                .range(element)
                .create()
        }
    }

    private fun annotateAction(element: PsiElement, holder: AnnotationHolder) {
        val actionName = element.text.trim()
        if (actionName.isEmpty()) return
        val routeElement = element.parent as? RoutesRouteElement ?: return
        val controllerName = routeElement.getControllerName()?.text?.trim() ?: return
        if (controllerName.contains('{')) return

        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)

        val psiClass = psiFacade.findClass(controllerName, scope)
            ?: PsiShortNamesCache.getInstance(project).getClassesByName(controllerName, scope).firstOrNull()
            ?: return // Controller annotation handles the missing controller case

        if (psiClass.findMethodsByName(actionName, true).isEmpty()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Action not found: $controllerName.$actionName")
                .range(element)
                .create()
        }
    }

    private fun findClass(element: PsiElement, name: String): Boolean {
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)
        return psiFacade.findClass(name, scope) != null
            || PsiShortNamesCache.getInstance(project).getClassesByName(name, scope).isNotEmpty()
    }
}
