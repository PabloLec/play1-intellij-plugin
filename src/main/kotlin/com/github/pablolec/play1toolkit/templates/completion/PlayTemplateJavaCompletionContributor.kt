package com.github.pablolec.play1toolkit.templates.completion

import com.github.pablolec.play1toolkit.templates.references.PlayTemplateJavaContextDetector
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiLiteralExpression
import com.intellij.util.ProcessingContext

class PlayTemplateJavaCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(PsiLiteralExpression::class.java),
            Provider()
        )
    }

    private class Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val literal = parameters.position.parent as? PsiLiteralExpression ?: return
            if (DumbService.isDumb(literal.project)) return
            if (!PlayTemplateJavaContextDetector.isRenderTemplatePathContext(literal)) return
            PlayTemplateService.getInstance(literal.project).getAllTemplates().forEach { template ->
                result.addElement(
                    LookupElementBuilder.create(template.logicalPath)
                        .withTypeText("Play template")
                )
            }
        }
    }
}
