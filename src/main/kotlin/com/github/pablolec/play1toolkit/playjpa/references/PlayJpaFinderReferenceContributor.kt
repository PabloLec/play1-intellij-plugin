package com.github.pablolec.play1toolkit.playjpa.references

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaFinderUtils
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

class PlayJpaFinderReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiLiteralExpression::class.java),
            PlayJpaFinderReferenceProvider(),
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}

private class PlayJpaFinderReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val literal = element as? PsiLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        val value = literal.value as? String ?: return PsiReference.EMPTY_ARRAY
        val call = (literal.parent as? PsiExpressionList)?.parent as? PsiMethodCallExpression ?: return PsiReference.EMPTY_ARRAY
        if (call.argumentList.expressions.firstOrNull() != literal) return PsiReference.EMPTY_ARRAY
        if (call.methodExpression.referenceName != "find") return PsiReference.EMPTY_ARRAY
        val qualText = call.methodExpression.qualifierExpression?.text?.trim() ?: return PsiReference.EMPTY_ARRAY
        val project = element.project
        val svc = PlayJpaModelService.getInstance(project)
        val model = svc.findModelByName(qualText) ?: return PsiReference.EMPTY_ARRAY

        val refs = mutableListOf<PsiReference>()

        if (value.startsWith("by", ignoreCase = true)) {
            val fieldNames = PlayJpaFinderUtils.parseByFieldPattern(value)
            val valueInLiteral = value  // without quotes
            var searchFrom = 2  // skip "by"
            for (fieldName in fieldNames) {
                val capitalized = fieldName.replaceFirstChar { it.uppercaseChar() }
                val idx = valueInLiteral.indexOf(capitalized, searchFrom)
                if (idx >= 0) {
                    // +1 for the opening quote in the literal text
                    val range = TextRange(idx + 1, idx + 1 + fieldName.length)
                    refs.add(PlayJpaFinderFieldReference(literal, range, model.psiClass, fieldName))
                    searchFrom = idx + capitalized.length
                }
            }
        } else {
            // JPQL-like: "email = ?"
            val jpqlFields = PlayJpaFinderUtils.parseJpqlFields(value)
            for (fieldName in jpqlFields) {
                val idx = value.indexOf(fieldName)
                if (idx >= 0) {
                    val range = TextRange(idx + 1, idx + 1 + fieldName.length)
                    refs.add(PlayJpaFinderFieldReference(literal, range, model.psiClass, fieldName))
                }
            }
        }

        return refs.toTypedArray()
    }
}

class PlayJpaFinderFieldReference(
    element: PsiLiteralExpression,
    range: TextRange,
    private val modelClass: PsiClass,
    private val fieldName: String
) : PsiReferenceBase<PsiLiteralExpression>(element, range) {

    override fun resolve(): PsiElement? = modelClass.findFieldByName(fieldName, true)

    override fun getVariants(): Array<Any> = emptyArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        val oldText = element.text
        val newByName = if (element.value.toString().startsWith("by", ignoreCase = true)) {
            "by${newElementName.replaceFirstChar { it.uppercaseChar() }}"
        } else newElementName
        val newText = oldText.replace(rangeInElement.substring(oldText), newByName)
        return ElementManipulators.handleContentChange(element, newText)
    }
}
