package com.github.pablolec.play1toolkit.playmessages.completion

import com.github.pablolec.play1toolkit.playmessages.references.PlayMessagesContextDetector
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiLiteralExpression
import com.intellij.util.ProcessingContext

class PlayMessagesJavaCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(PsiLiteralExpression::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    if (DumbService.isDumb(parameters.position.project)) return
                    val literal = parameters.position.parent as? PsiLiteralExpression ?: return
                    if (!PlayMessagesContextDetector.isMessagesKeyContext(literal)) return
                    val svc = PlayMessagesService.getInstance(parameters.position.project)
                    svc.allKeys().forEach { key ->
                        val defaultValue = svc.defaultEntry(key)?.value ?: ""
                        val tailText = if (defaultValue.isNotBlank()) " = ${defaultValue.take(40)}" else ""
                        val localeCount = svc.entriesForKey(key).size
                        val typeText = if (localeCount > 1) "$localeCount locales" else "default only"
                        result.addElement(
                            LookupElementBuilder.create(key)
                                .withTailText(tailText, true)
                                .withTypeText(typeText)
                                .withCaseSensitivity(false)
                        )
                    }
                }
            }
        )
    }
}
