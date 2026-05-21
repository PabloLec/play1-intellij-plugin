package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateVariableResolver
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiReferenceBase

class PlayTemplateVariableReference(
    element: PsiElement,
    range: TextRange,
    private val variableName: String,
    private val qualifierName: String? = null,
    private val methodCall: Boolean = false
) : PsiReferenceBase<PsiElement>(element, range, true), PlayTemplateReference {

    override fun resolve(): PsiElement? {
        val resolver = PlayTemplateVariableResolver.getInstance(element.project)
        if (qualifierName == null) {
            return resolver.resolveVariableInfo(element.containingFile, variableName)?.declaration
        }
        val qualifierType = resolver.resolveVariableType(element.containingFile, qualifierName)
        return resolver.resolveMember(element, qualifierType, variableName, methodCall)
    }

    override fun getVariants(): Array<Any> =
        if (qualifierName == null) {
            PlayTemplateVariableResolver.getInstance(element.project)
                .resolveVariables(element.containingFile)
                .sorted()
                .map { LookupElementBuilder.create(it).withTypeText("template variable") }
                .toTypedArray()
        } else {
            emptyArray()
        }
}
