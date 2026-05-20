package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateVariableResolver
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

class PlayTemplateVariableReference(
    element: PsiElement,
    range: TextRange,
    private val variableName: String
) : PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? =
        PlayTemplateVariableResolver.getInstance(element.project)
            .resolveVariableDeclarations(element.containingFile)[variableName]

    override fun getVariants(): Array<Any> =
        PlayTemplateVariableResolver.getInstance(element.project)
            .resolveVariables(element.containingFile)
            .sorted()
            .map { LookupElementBuilder.create(it).withTypeText("template variable") }
            .toTypedArray()
}
