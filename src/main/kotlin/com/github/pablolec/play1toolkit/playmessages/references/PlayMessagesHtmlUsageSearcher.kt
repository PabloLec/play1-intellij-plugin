package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * Finds usages of a PlayMessagesProperty in Play 1 HTML templates under app/views/.
 * Scans raw file text (like PlayMessagesHtmlGotoDeclarationHandler) so it works
 * regardless of PSI structure — including &{'key'} inside <script> blocks where
 * IntelliJ parses content as embedded JavaScript rather than XmlText.
 */
class PlayMessagesHtmlUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val element = params.elementToSearch as? PlayMessagesProperty ?: return
        val key = element.key
        val project = element.project
        if (DumbService.isDumb(project)) return

        val scope = GlobalSearchScope.projectScope(project)
        FilenameIndex.getAllFilesByExt(project, "html", scope).forEach { vf ->
            if (!vf.path.contains("/app/views/")) return@forEach
            val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return@forEach
            val fileText = psiFile.text

            PlayMessagesHtmlReferenceContributor.MESSAGES_PATTERN.findAll(fileText).forEach { match ->
                if (match.groupValues[1] != key) return@forEach
                val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                if (quotePos < 0) return@forEach
                val absKeyStart = match.range.first + quotePos + 1
                val absKeyEnd = absKeyStart + key.length
                val leaf = psiFile.findElementAt(absKeyStart) ?: return@forEach
                val relStart = absKeyStart - leaf.textRange.startOffset
                val relEnd = relStart + key.length
                if (relStart < 0 || relEnd > leaf.textLength) return@forEach
                consumer.process(PlayMessagesRawUsageReference(leaf, TextRange(relStart, relEnd), key, project))
            }
        }
    }
}

private class PlayMessagesRawUsageReference(
    element: PsiElement,
    range: TextRange,
    private val key: String,
    private val project: com.intellij.openapi.project.Project
) : PsiReferenceBase.Poly<PsiElement>(element, range, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        PlayMessagesService.getInstance(project)
            .entriesForKey(key)
            .map { PsiElementResolveResult(it.property) }
            .toTypedArray()

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element !is PlayMessagesProperty) return false
        return element.key == key
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
