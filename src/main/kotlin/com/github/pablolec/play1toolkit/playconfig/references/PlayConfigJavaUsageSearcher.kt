package com.github.pablolec.play1toolkit.playconfig.references

import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * When Find Usages is invoked on a PlayConfigProperty, this searcher finds all
 * Java PsiLiteralExpression occurrences of the logical key in recognized contexts.
 */
class PlayConfigJavaUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val element = params.elementToSearch as? PlayConfigProperty ?: return
        val logicalKey = element.logicalKey
        val project = element.project
        val scope = params.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.projectScope(project)

        // Search for the literal string in Java files
        val helper = PsiSearchHelper.getInstance(project)
        helper.processElementsWithWord(
            { psiElement, _ ->
                val literal = psiElement as? PsiLiteralExpression ?: return@processElementsWithWord true
                if (literal.value?.toString() != logicalKey) return@processElementsWithWord true
                if (!PlayConfigContextDetector.isConfigKeyContext(literal) &&
                    !PlayConfigContextDetector.isProbableConfigCall(literal)) {
                    return@processElementsWithWord true
                }
                val ref = literal.reference ?: PlayConfigStringLiteralReference(
                    literal,
                    com.intellij.openapi.util.TextRange(1, logicalKey.length + 1)
                )
                consumer.process(ref)
            },
            scope,
            logicalKey,
            UsageSearchContext.IN_STRINGS,
            true
        )
    }
}
