package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult

class PlayTemplateRouteReference(
    element: PsiElement,
    range: TextRange,
    private val controllerName: String,
    private val actionName: String,
    private val kind: Kind
) : PsiReferenceBase.Poly<PsiElement>(element, range, true), PlayTemplateReference {

    enum class Kind { CONTROLLER, ACTION }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project
        return when (kind) {
            Kind.CONTROLLER -> {
                val clazz = RoutesControllerResolver.resolveClass(project, controllerName)
                    ?: return ResolveResult.EMPTY_ARRAY
                arrayOf(PsiElementResolveResult(clazz))
            }
            Kind.ACTION -> {
                val method = RoutesControllerResolver.resolveMethod(project, controllerName, actionName)
                    ?: return ResolveResult.EMPTY_ARRAY
                arrayOf(PsiElementResolveResult(method))
            }
        }
    }

    override fun handleElementRename(newElementName: String) = when (kind) {
        Kind.CONTROLLER -> ElementManipulators.handleContentChange(element, rangeInElement, newElementName)
        Kind.ACTION -> ElementManipulators.handleContentChange(element, rangeInElement, newElementName)
    }
}
