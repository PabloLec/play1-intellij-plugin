package com.github.pablolec.play1toolkit.playconfig.completion

import com.github.pablolec.play1toolkit.playconfig.references.PlayConfigContextDetector
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiLiteralExpression
import com.intellij.util.ProcessingContext

class PlayConfigJavaCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(PsiLiteralExpression::class.java),
            PlayConfigJavaCompletionProvider()
        )
    }
}

private class PlayConfigJavaCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val literal = element.parent as? PsiLiteralExpression ?: return
        if (DumbService.isDumb(element.project)) return
        if (!PlayConfigContextDetector.isConfigKeyContext(literal)) return

        val svc = PlayConfigService.getInstance(element.project)
        val activeProfile = svc.resolveActiveProfile()

        svc.allKeys().groupBy { it.logicalKey }.forEach { (logicalKey, variants) ->
            val defaultVariant = variants.firstOrNull { it.profile == null }
            val profileVariant = if (activeProfile != null) variants.firstOrNull { it.profile == activeProfile } else null
            val effective = profileVariant ?: defaultVariant

            val tailText = when {
                effective == null -> ""
                effective.value.isBlank() -> " = (empty)"
                else -> " = ${effective.value.take(40)}"
            }

            val typeText = when {
                activeProfile != null && profileVariant != null -> "[$activeProfile]"
                activeProfile != null -> "[default]"
                else -> ""
            }

            result.addElement(
                LookupElementBuilder.create(logicalKey)
                    .withTailText(tailText, true)
                    .withTypeText(typeText)
                    .withCaseSensitivity(false)
            )
        }
    }
}
