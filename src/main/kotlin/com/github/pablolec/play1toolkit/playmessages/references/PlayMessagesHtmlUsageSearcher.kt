package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlText
import com.intellij.util.Processor

/**
 * Finds usages of a PlayMessagesProperty in Play 1 HTML templates under app/views/.
 * Uses FilenameIndex + PSI visitor instead of VfsUtil.processFilesRecursively to avoid
 * VFS cache issues, and visits XmlText nodes directly to avoid XmlEntityRef nesting
 * problems that break findElementAt-based anchor resolution.
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

            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    val xmlText = element as? XmlText ?: return
                    val text = xmlText.text
                    PlayMessagesHtmlReferenceContributor.MESSAGES_PATTERN.findAll(text).forEach { match ->
                        if (match.groupValues[1] != key) return@forEach
                        val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                        if (quotePos < 0) return@forEach
                        val relKeyStart = match.range.first + quotePos + 1
                        val relKeyEnd = relKeyStart + key.length
                        if (relKeyEnd > xmlText.textLength) return@forEach
                        consumer.process(PlayMessagesHtmlStringReference(xmlText, key, TextRange(relKeyStart, relKeyEnd)))
                    }
                }
            })
        }
    }
}
