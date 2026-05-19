package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor

/**
 * Makes IntelliJ count route references as usages of Java controller classes and methods.
 *
 * Without this, the "n usages" inlay on a controller class/method shows "no usages" even
 * though conf/routes references it — because IntelliJ's background scanner doesn't reliably
 * resolve custom-language PsiReferences during the usage count pass.
 *
 * This searcher explicitly walks conf/routes and injects DirectRouteReference objects whose
 * resolve() returns the exact Java element being searched, so every match is counted.
 */
class RoutesReferencesSearcher : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

    override fun execute(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val target = params.elementToSearch
        val project = target.project
        if (DumbService.isDumb(project)) return true

        runReadAction {
            val routesVf = Play1ViewUtils.findRoutesFile(project) ?: return@runReadAction
            val psiFile = PsiManager.getInstance(project).findFile(routesVf) as? RoutesFile
                ?: return@runReadAction

            when (target) {
                is PsiClass -> searchClass(target, psiFile, consumer)
                is PsiMethod -> searchMethod(target, psiFile, consumer)
            }
        }
        return true
    }

    private fun searchClass(
        psiClass: PsiClass,
        routesFile: RoutesFile,
        consumer: Processor<in PsiReference>
    ) {
        if (!Play1ViewUtils.isPlayControllerClass(psiClass)) return
        val shortName = psiClass.name ?: return

        routesFile.getRoutes().forEach { route ->
            val ctrlElement = route.getControllerName() ?: return@forEach
            if (ctrlElement.text.trim().substringAfterLast('.') == shortName) {
                consumer.process(DirectRouteReference(ctrlElement, psiClass))
            }
        }
    }

    private fun searchMethod(
        method: PsiMethod,
        routesFile: RoutesFile,
        consumer: Processor<in PsiReference>
    ) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return
        if (!method.hasModifierProperty(PsiModifier.STATIC)) return
        val containingClass = method.containingClass ?: return
        if (!Play1ViewUtils.isPlayControllerClass(containingClass)) return
        val controllerShortName = containingClass.name ?: return
        val actionName = method.name

        routesFile.getRoutes().forEach { route ->
            val ctrlText = route.getControllerName()?.text?.trim()
                ?.substringAfterLast('.') ?: return@forEach
            val actionElement = route.getActionName() ?: return@forEach
            if (ctrlText == controllerShortName && actionElement.text.trim() == actionName) {
                consumer.process(DirectRouteReference(actionElement, method))
            }
        }
    }
}

/** A PsiReference that always resolves to the given [target]. Used to register route occurrences as usages. */
private class DirectRouteReference(element: PsiElement, private val target: PsiElement) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), false) {
    override fun resolve(): PsiElement = target
    override fun getVariants(): Array<Any> = emptyArray()
}
