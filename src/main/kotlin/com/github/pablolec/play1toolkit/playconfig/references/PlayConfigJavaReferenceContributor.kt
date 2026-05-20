package com.github.pablolec.play1toolkit.playconfig.references

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

class PlayConfigJavaReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val literal = element as? PsiLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    val value = literal.value as? String ?: return PsiReference.EMPTY_ARRAY
                    if (value.isBlank() || !value.contains('.')) return PsiReference.EMPTY_ARRAY

                    if (!PlayConfigContextDetector.isConfigKeyContext(literal)) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    // Range covers the string content (excluding surrounding quotes)
                    val range = TextRange(1, value.length + 1)
                    return arrayOf(PlayConfigStringLiteralReference(literal, range))
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}
