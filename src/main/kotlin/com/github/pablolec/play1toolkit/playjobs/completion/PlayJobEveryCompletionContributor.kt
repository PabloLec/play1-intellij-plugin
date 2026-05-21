package com.github.pablolec.play1toolkit.playjobs.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

private val EVERY_SUGGESTIONS = listOf("1s", "10s", "1mn", "5mn", "1h", "1d")

class PlayJobEveryCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PsiLiteralExpression::class.java),
            PlayJobEveryCompletionProvider()
        )
    }
}

private class PlayJobEveryCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val literal = parameters.position.parent as? PsiLiteralExpression ?: return
        val annotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation::class.java) ?: return
        val simpleName = annotation.qualifiedName?.substringAfterLast('.')
            ?: annotation.nameReferenceElement?.referenceName
            ?: return
        if (simpleName != "Every") return

        EVERY_SUGGESTIONS.forEach { value ->
            result.addElement(
                LookupElementBuilder.create(value)
                    .withTypeText("Play duration")
                    .withPresentableText(value)
            )
        }
    }
}
