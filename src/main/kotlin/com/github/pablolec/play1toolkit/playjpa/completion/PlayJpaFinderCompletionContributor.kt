package com.github.pablolec.play1toolkit.playjpa.completion

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

class PlayJpaFinderCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PsiLiteralExpression::class.java),
            PlayJpaFinderCompletionProvider()
        )
    }
}

private class PlayJpaFinderCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val literal = parameters.position.parent as? PsiLiteralExpression ?: return
        val call = (literal.parent as? PsiExpressionList)?.parent as? PsiMethodCallExpression ?: return
        if (call.argumentList.expressions.firstOrNull() != literal) return
        if (call.methodExpression.referenceName != "find") return
        val qualText = call.methodExpression.qualifierExpression?.text?.trim() ?: return
        val project = parameters.position.project
        val svc = PlayJpaModelService.getInstance(project)
        val model = svc.findModelByName(qualText) ?: return
        val prefix = result.prefixMatcher.prefix.removePrefix("\"")

        for (field in model.fields + (model.idField?.let { listOf(it) } ?: emptyList())) {
            val byName = "by${field.name.replaceFirstChar { it.uppercaseChar() }}"
            if (byName.startsWith(prefix, ignoreCase = true)) {
                result.addElement(
                    LookupElementBuilder.create(byName)
                        .withTypeText(field.typeText)
                        .withPresentableText(byName)
                )
            }
        }
    }
}
