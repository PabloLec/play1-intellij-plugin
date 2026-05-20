package com.github.pablolec.play1toolkit.playmessages.references

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

class PlayMessagesJavaReferenceContributor : PsiReferenceContributor() {

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
                    if (value.isBlank()) return PsiReference.EMPTY_ARRAY
                    if (!PlayMessagesContextDetector.isMessagesKeyContext(literal)) return PsiReference.EMPTY_ARRAY
                    val range = TextRange(1, value.length + 1)
                    return arrayOf(PlayMessagesJavaStringLiteralReference(literal, range))
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}
