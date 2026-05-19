package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.ProcessingContext

class RoutesReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement()
                .withElementType(RoutesTokenTypes.CONTROLLER_NAME)
                .inFile(PlatformPatterns.psiFile(RoutesFile::class.java)),
            ControllerNameReferenceProvider
        )
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement()
                .withElementType(RoutesTokenTypes.ACTION_NAME)
                .inFile(PlatformPatterns.psiFile(RoutesFile::class.java)),
            ActionNameReferenceProvider
        )
    }
}

private object ControllerNameReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> =
        arrayOf(ControllerNameReference(element))
}

private object ActionNameReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> =
        arrayOf(ActionNameReference(element))
}

class ControllerNameReference(element: PsiElement) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement? {
        val name = element.text.trim()
        return RoutesControllerResolver.resolveClass(element.project, name)
    }

    override fun getVariants(): Array<Any> {
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        return PsiShortNamesCache.getInstance(project).getAllClassNames()
            .flatMap { name ->
                PsiShortNamesCache.getInstance(project).getClassesByName(name, scope)
                    .filter { it.qualifiedName?.startsWith("controllers.") == true }
                    .toList()
            }
            .mapNotNull { psiClass ->
                val fqn = psiClass.qualifiedName ?: return@mapNotNull null
                val routesName = if (fqn.startsWith("controllers.")) fqn.removePrefix("controllers.") else fqn
                LookupElementBuilder.create(routesName)
                    .withIcon(psiClass.getIcon(0))
                    .withTypeText(fqn)
            }
            .toTypedArray()
    }
}

class ActionNameReference(element: PsiElement) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement? {
        val actionName = element.text.trim()
        if (actionName.isEmpty()) return null
        val routeElement = element.parent as? RoutesRouteElement ?: return null
        val controllerName = routeElement.getControllerName()?.text?.trim() ?: return null
        return RoutesControllerResolver.resolveMethod(element.project, controllerName, actionName)
    }

    override fun getVariants(): Array<Any> {
        val routeElement = element.parent as? RoutesRouteElement ?: return emptyArray()
        val controllerName = routeElement.getControllerName()?.text?.trim() ?: return emptyArray()
        if (controllerName.contains('{')) return emptyArray()

        val psiClass = RoutesControllerResolver.resolveClass(element.project, controllerName)
            ?: return emptyArray()

        return psiClass.methods
            .filter { it.hasModifierProperty(PsiModifier.PUBLIC) && it.hasModifierProperty(PsiModifier.STATIC) }
            .map { method ->
                LookupElementBuilder.create(method.name)
                    .withTypeText(controllerName)
                    .withIcon(method.getIcon(0))
            }
            .toTypedArray()
    }
}
