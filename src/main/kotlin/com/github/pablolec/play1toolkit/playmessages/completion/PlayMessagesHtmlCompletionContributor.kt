package com.github.pablolec.play1toolkit.playmessages.completion

import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.xml.XmlText
import com.intellij.util.ProcessingContext

class PlayMessagesHtmlCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(XmlText::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    if (DumbService.isDumb(parameters.position.project)) return
                    val path = parameters.originalFile.virtualFile?.path ?: return
                    if (!path.contains("/app/views/")) return

                    // Check that we're inside &{' ... '} expression
                    val offset = parameters.offset
                    val docText = parameters.editor.document.text
                    val lineStart = docText.lastIndexOf('\n', offset - 1) + 1
                    val textBeforeCaret = docText.substring(lineStart, offset)
                    // Must match &{' or &{" followed by partial key
                    val prefixPattern = Regex(""".*&\{['"]([^'"]*)$""")
                    val match = prefixPattern.find(textBeforeCaret) ?: return
                    val partialKey = match.groupValues[1]

                    val svc = PlayMessagesService.getInstance(parameters.position.project)
                    val prefixResult = result.withPrefixMatcher(partialKey)
                    svc.allKeys().forEach { key ->
                        val defaultValue = svc.defaultEntry(key)?.value ?: ""
                        val tailText = if (defaultValue.isNotBlank()) " = ${defaultValue.take(40)}" else ""
                        prefixResult.addElement(
                            LookupElementBuilder.create(key)
                                .withTailText(tailText, true)
                                .withCaseSensitivity(false)
                        )
                    }
                }
            }
        )
    }
}
