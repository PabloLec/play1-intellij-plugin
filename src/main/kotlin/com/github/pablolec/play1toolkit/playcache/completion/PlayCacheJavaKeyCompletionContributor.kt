package com.github.pablolec.play1toolkit.playcache.completion

import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.util.ProcessingContext

class PlayCacheJavaKeyCompletionContributor : CompletionContributor() {

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
                    val project = parameters.position.project
                    if (DumbService.isDumb(project)) return
                    val literal = parameters.position.parent as? PsiLiteralExpression ?: return
                    if (!isFirstArgOfCacheCall(literal)) return
                    val service = PlayCacheService.getInstance(project)
                    service.getKnownStaticKeys().forEach { key ->
                        result.addElement(
                            LookupElementBuilder.create(key)
                                .withIcon(AllIcons.Actions.MenuSaveall)
                                .withTypeText("Play cache key")
                        )
                    }
                }
            }
        )
    }

    private fun isFirstArgOfCacheCall(literal: PsiLiteralExpression): Boolean {
        val argList = literal.parent as? PsiExpressionList ?: return false
        val call = argList.parent as? PsiMethodCallExpression ?: return false
        if (!PlayCacheArgExtractor.isCacheCall(call)) return false
        val args = argList.expressions
        return args.isNotEmpty() && args[0] === literal
    }
}
