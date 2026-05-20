package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlText
import com.intellij.util.Processor

/**
 * Finds usages of a PlayMessagesProperty in Play 1 HTML templates under app/views/.
 * Scans all HTML files directly rather than using word-based search, because:
 * 1. Word scanners break dotted keys like "patient.cededReason" at dots
 * 2. IN_STRINGS context doesn't match HTML text content
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

        val basePath = project.basePath ?: return
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return
        val viewsDir = baseDir.findFileByRelativePath("app/views") ?: return

        VfsUtil.processFilesRecursively(viewsDir) { vf ->
            if (vf.extension != "html") return@processFilesRecursively true
            val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return@processFilesRecursively true

            // Scan the raw document text for &{'key'} patterns — bypasses PSI structure assumptions
            val text = psiFile.text
            PlayMessagesHtmlReferenceContributor.MESSAGES_PATTERN.findAll(text).forEach { match ->
                if (match.groupValues[1] != key) return@forEach
                val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                if (quotePos < 0) return@forEach

                val absKeyStart = match.range.first + quotePos + 1
                val absKeyEnd = absKeyStart + key.length

                // Find the PSI element at the key offset and walk up to find an XmlText anchor
                val leafAtOffset = psiFile.findElementAt(absKeyStart) ?: return@forEach
                val xmlText = generateSequence(leafAtOffset) { it.parent }
                    .filterIsInstance<XmlText>()
                    .firstOrNull() ?: return@forEach

                // Make the TextRange relative to the XmlText element
                val relStart = absKeyStart - xmlText.textRange.startOffset
                val relEnd = absKeyEnd - xmlText.textRange.startOffset
                if (relStart < 0 || relEnd > xmlText.textLength) return@forEach

                consumer.process(
                    PlayMessagesHtmlStringReference(xmlText, key, TextRange(relStart, relEnd))
                )
            }
            true
        }
    }
}
