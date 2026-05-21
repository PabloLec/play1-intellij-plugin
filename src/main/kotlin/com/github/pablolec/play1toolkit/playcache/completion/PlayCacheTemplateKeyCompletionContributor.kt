package com.github.pablolec.play1toolkit.playcache.completion

import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class PlayCacheTemplateKeyCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.position.project
                    if (DumbService.isDumb(project)) return
                    val file = parameters.originalFile
                    val ext = file.virtualFile?.extension ?: return
                    if (ext !in TEMPLATE_EXTS) return
                    if (file.virtualFile?.path?.contains("/app/views/") != true) return
                    val offset = parameters.offset
                    val text = parameters.originalFile.text ?: return
                    if (!isInsideCacheKeyLiteral(text, offset)) return
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

    /**
     * Quick text-based gate: walk backwards from the caret. We accept the position when:
     *  - there is an unclosed quote on the same line just before the caret, and
     *  - that quote follows a `#{cache` token within the prior characters.
     * Pure regex would over-match; this avoids competing with the standard completion.
     */
    private fun isInsideCacheKeyLiteral(text: String, offset: Int): Boolean {
        val cap = (offset - 200).coerceAtLeast(0)
        val prefix = text.substring(cap, offset)
        val cacheIdx = prefix.lastIndexOf("#{cache")
        if (cacheIdx == -1) return false
        val between = prefix.substring(cacheIdx)
        if (between.contains("}")) return false
        val singles = between.count { it == '\'' }
        val doubles = between.count { it == '"' }
        // Inside the FIRST positional argument: exactly one unmatched quote in the open tag head.
        if (singles % 2 != 1 && doubles % 2 != 1) return false
        // Bail if the for: attribute has already started — only the first arg gets completion.
        if (between.contains("for:")) return false
        return true
    }

    companion object {
        private val TEMPLATE_EXTS = setOf("html", "xml", "json", "txt")
    }
}
