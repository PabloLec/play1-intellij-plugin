package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class PlayTemplateJavaReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val literal = element as? PsiLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    val value = literal.value as? String ?: return PsiReference.EMPTY_ARRAY
                    if (value.isBlank()) return PsiReference.EMPTY_ARRAY
                    if (!PlayTemplateJavaContextDetector.isRenderTemplatePathContext(literal)) return PsiReference.EMPTY_ARRAY
                    return arrayOf(
                        PlayTemplateJavaPathReference(
                            literal,
                            TextRange(1, value.length + 1),
                            PlayTemplateFileUtils.normalizeTemplatePath(value)
                        )
                    )
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}
