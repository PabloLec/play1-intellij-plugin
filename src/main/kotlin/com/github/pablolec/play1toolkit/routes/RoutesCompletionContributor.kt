package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.ProcessingContext

class RoutesCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .withElementType(RoutesTokenTypes.CONTROLLER_NAME)
                .inFile(PlatformPatterns.psiFile(RoutesFile::class.java)),
            ControllerCompletionProvider
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .withElementType(RoutesTokenTypes.ACTION_NAME)
                .inFile(PlatformPatterns.psiFile(RoutesFile::class.java)),
            ActionCompletionProvider
        )
    }
}

private object ControllerCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val project = parameters.position.project
        val scope = GlobalSearchScope.projectScope(project)
        PsiShortNamesCache.getInstance(project).getAllClassNames().forEach { name ->
            PsiShortNamesCache.getInstance(project).getClassesByName(name, scope).forEach { cls ->
                result.addElement(
                    LookupElementBuilder.create(cls.name ?: name)
                        .withIcon(cls.getIcon(0))
                        .withTypeText("Controller")
                )
            }
        }
    }
}

private object ActionCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val routeElement = element.parent as? RoutesRouteElement ?: return
        val controllerName = routeElement.getControllerName()?.text?.trim() ?: return
        if (controllerName.contains('{')) return

        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)

        val psiClass = psiFacade.findClass(controllerName, scope)
            ?: PsiShortNamesCache.getInstance(project).getClassesByName(controllerName, scope).firstOrNull()
            ?: return

        psiClass.methods
            .filter { it.hasModifierProperty(PsiModifier.PUBLIC) && it.hasModifierProperty(PsiModifier.STATIC) }
            .forEach { method ->
                result.addElement(
                    LookupElementBuilder.create(method.name)
                        .withTypeText(controllerName)
                        .withIcon(method.getIcon(0))
                )
            }
    }
}
