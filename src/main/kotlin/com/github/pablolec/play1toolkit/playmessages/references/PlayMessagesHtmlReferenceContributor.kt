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
        // Target XmlText elements in HTML files
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(XmlText::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    if (!isInViewsDirectory(element)) return PsiReference.EMPTY_ARRAY
                    val xmlText = element as? XmlText ?: return PsiReference.EMPTY_ARRAY
                    return referencesInXmlText(xmlText)
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )

        // Fallback: target leaf elements anywhere inside XmlText (not just direct children),
        // because IntelliJ's HTML parser may create XmlEntityRef intermediate nodes for &{...}
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java)
                .inside(XmlText::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    if (!isInViewsDirectory(element)) return PsiReference.EMPTY_ARRAY
                    val xmlText = element.parent as? XmlText ?: return PsiReference.EMPTY_ARRAY
                    // Delegate to the XmlText-level function, but only return refs that
                    // fall within this specific leaf element's range
                    val leafRange = element.textRange
                    return referencesInXmlText(xmlText).filter { ref ->
                        val absStart = xmlText.textRange.startOffset + ref.rangeInElement.startOffset
                        leafRange.contains(absStart)
                    }.toTypedArray()
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }

    private fun referencesInXmlText(xmlText: XmlText): Array<PsiReference> {
        // Use the raw source text of the XmlText element (getText() returns full raw source)
        val text = xmlText.text
        return MESSAGES_PATTERN.findAll(text).mapNotNull { match ->
            val key = match.groupValues[1]
            if (key.isBlank()) return@mapNotNull null
            val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
            if (quotePos < 0) return@mapNotNull null
            val keyStart = match.range.first + quotePos + 1
            val keyEnd = keyStart + key.length
            if (keyEnd > text.length) return@mapNotNull null
            PlayMessagesHtmlStringReference(xmlText, key, TextRange(keyStart, keyEnd))
        }.toList().toTypedArray()
    }

    private fun isInViewsDirectory(element: PsiElement): Boolean {
        val path = element.containingFile?.virtualFile?.path ?: return false
        return path.contains("/app/views/")
    }
}
