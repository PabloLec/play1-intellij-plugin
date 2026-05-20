package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlText
import com.intellij.util.Processor

class PlayMessagesHtmlUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

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
                val xmlText = psiElement as? XmlText ?: return@processElementsWithWord true
                val isInViews = xmlText.containingFile?.virtualFile?.path?.contains("/app/views/") == true
                if (!isInViews) return@processElementsWithWord true
                // Find matching patterns in this text node
                PlayMessagesHtmlReferenceContributor.MESSAGES_PATTERN.findAll(xmlText.text).forEach { match ->
                    if (match.groupValues[1] == key) {
                        val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                        if (quotePos >= 0) {
                            val keyStart = match.range.first + quotePos + 1
                            val keyEnd = keyStart + key.length
                            consumer.process(
                                PlayMessagesHtmlStringReference(xmlText, key, TextRange(keyStart, keyEnd))
                            )
                        }
                    }
                }
                true
            },
            scope,
            key,
            UsageSearchContext.IN_STRINGS,
            true
        )
    }
}
