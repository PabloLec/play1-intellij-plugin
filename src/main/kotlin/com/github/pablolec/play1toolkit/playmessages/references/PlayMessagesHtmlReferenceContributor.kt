package com.github.pablolec.play1toolkit.playmessages.references

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.XmlText
import com.intellij.util.ProcessingContext

class PlayMessagesHtmlReferenceContributor : PsiReferenceContributor() {

    companion object {
        // Matches &{'key'} and &{'key', args...} and &{"key"} variants
        val MESSAGES_PATTERN = Regex("""&\{['"]([^'"]+)['"](?:\s*,.*?)?\}""")
    }

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(XmlText::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    if (!isInViewsDirectory(element)) return PsiReference.EMPTY_ARRAY
                    val xmlText = element as? XmlText ?: return PsiReference.EMPTY_ARRAY
                    val text = xmlText.text
                    return MESSAGES_PATTERN.findAll(text).mapNotNull { match ->
                        val key = match.groupValues[1]
                        if (key.isBlank()) return@mapNotNull null
                        // keyStart = position of first char of the key within xmlText.text
                        val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                        if (quotePos < 0) return@mapNotNull null
                        val keyStart = match.range.first + quotePos + 1
                        val keyEnd = keyStart + key.length
                        PlayMessagesHtmlStringReference(xmlText, key, TextRange(keyStart, keyEnd))
                    }.toList().toTypedArray()
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }

    private fun isInViewsDirectory(element: PsiElement): Boolean {
        val path = element.containingFile?.virtualFile?.path ?: return false
        return path.contains("/app/views/")
    }
}
