package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
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
        if (name.isEmpty() || name.contains('{')) return

        if (resolveClass(element.project, name) == null) {
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

        val psiClass = resolveClass(element.project, controllerName) ?: return

        if (psiClass.findMethodsByName(actionName, false).isEmpty()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Action not found: $controllerName.$actionName")
                .range(element)
                .create()
        }
    }

    /**
     * Resolves a Play controller name to a PsiClass.
     * Tries (in order):
     *   1. Exact FQN match
     *   2. "controllers." prefix (Play convention — controllers.Application, controllers.login.LoginCtl)
     *   3. Short class name via PsiShortNamesCache (last component after the last dot)
     */
    private fun resolveClass(project: com.intellij.openapi.project.Project, name: String): PsiClass? {
        val scope = GlobalSearchScope.allScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)
        val shortName = name.substringAfterLast('.')

        return psiFacade.findClass(name, scope)
            ?: psiFacade.findClass("controllers.$name", scope)
            ?: PsiShortNamesCache.getInstance(project).getClassesByName(shortName, scope).firstOrNull()
    }
}
