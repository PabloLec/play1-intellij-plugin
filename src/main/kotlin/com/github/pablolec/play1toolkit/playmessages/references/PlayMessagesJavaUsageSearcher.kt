package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

class PlayMessagesJavaUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val element = params.elementToSearch as? PlayMessagesProperty ?: return
        val key = element.key
        val project = element.project
        val scope = params.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.projectScope(project)

        val helper = PsiSearchHelper.getInstance(project)
        helper.processElementsWithWord(
            { psiElement, _ ->
                val literal = psiElement as? PsiLiteralExpression ?: return@processElementsWithWord true
                if (literal.value?.toString() != key) return@processElementsWithWord true
                if (!PlayMessagesContextDetector.isMessagesKeyContext(literal)) return@processElementsWithWord true
                val ref = literal.reference as? PlayMessagesJavaStringLiteralReference
                    ?: PlayMessagesJavaStringLiteralReference(literal, TextRange(1, key.length + 1))
                consumer.process(ref)
            },
            scope,
            key,
            UsageSearchContext.IN_STRINGS,
            true
        )
    }
}
