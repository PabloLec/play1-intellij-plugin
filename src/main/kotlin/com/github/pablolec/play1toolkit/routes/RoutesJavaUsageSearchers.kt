package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor

class RoutesClassReferencesSearcher : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

    override fun execute(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val psiClass = params.elementToSearch as? PsiClass ?: return true
        val project = psiClass.project
        if (DumbService.isDumb(project)) return true

        runReadAction {
            if (!Play1ViewUtils.isPlayControllerClass(psiClass)) return@runReadAction
            val routesFile = loadRoutesFile(project) ?: return@runReadAction
            val shortName = psiClass.name ?: return@runReadAction
            routesFile.getRoutes().forEach { route ->
                val ctrlElement = route.getControllerName() ?: return@forEach
                if (ctrlElement.text.trim().substringAfterLast('.') == shortName) {
                    ctrlElement.references
                        .firstOrNull { it.resolve() == psiClass }
                        ?.let { consumer.process(it) }
                }
            }
        }
        return true
    }
}

class RoutesMethodReferencesSearcher : QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {

    override fun execute(
        params: MethodReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val method = params.method
        val project = method.project
        if (DumbService.isDumb(project)) return true

        runReadAction {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC) || !method.hasModifierProperty(PsiModifier.STATIC)) return@runReadAction
            val containingClass = method.containingClass ?: return@runReadAction
            if (!Play1ViewUtils.isPlayControllerClass(containingClass)) return@runReadAction
            val routesFile = loadRoutesFile(project) ?: return@runReadAction
            val controllerShortName = containingClass.name ?: return@runReadAction
            routesFile.getRoutes().forEach { route ->
                val ctrlText = route.getControllerName()?.text?.trim()?.substringAfterLast('.') ?: return@forEach
                val actionElement = route.getActionName() ?: return@forEach
                if (ctrlText == controllerShortName && actionElement.text.trim() == method.name) {
                    actionElement.references
                        .firstOrNull { it.resolve() == method }
                        ?.let { consumer.process(it) }
                }
            }
        }
        return true
    }
}

private fun loadRoutesFile(project: com.intellij.openapi.project.Project): RoutesFile? {
    val routesVf = Play1ViewUtils.findRoutesFile(project) ?: return null
    return PsiManager.getInstance(project).findFile(routesVf) as? RoutesFile
}
