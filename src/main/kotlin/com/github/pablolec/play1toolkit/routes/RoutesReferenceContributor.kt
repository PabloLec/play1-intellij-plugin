package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class RoutesReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement()
                .withElementType(RoutesTokenTypes.CONTROLLER_NAME)
                .inFile(PlatformPatterns.psiFile(RoutesFile::class.java)),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val text = element.text.trim()
                    if (text.isEmpty() || text.contains('{')) return PsiReference.EMPTY_ARRAY
                    return arrayOf(ControllerRouteReference(element))
                }
            }
        )

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement()
                .withElementType(RoutesTokenTypes.ACTION_NAME)
                .inFile(PlatformPatterns.psiFile(RoutesFile::class.java)),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val route = element.parent as? RoutesRouteElement ?: return PsiReference.EMPTY_ARRAY
                    val controllerName = route.getControllerName()?.text?.trim() ?: return PsiReference.EMPTY_ARRAY
                    if (controllerName.contains('{')) return PsiReference.EMPTY_ARRAY
                    return arrayOf(ActionRouteReference(element, controllerName))
                }
            }
        )
    }
}

private class ControllerRouteReference(element: PsiElement) :
    PsiReferenceBase<PsiElement>(element, false) {

    override fun resolve() = RoutesControllerResolver.resolveClass(element.project, element.text.trim())

    override fun getVariants(): Array<Any> = emptyArray()
}

private class ActionRouteReference(
    element: PsiElement,
    private val controllerName: String,
) : PsiReferenceBase<PsiElement>(element, false) {

    override fun resolve() =
        RoutesControllerResolver.resolveMethod(element.project, controllerName, element.text.trim())

    override fun getVariants(): Array<Any> = emptyArray()
}
