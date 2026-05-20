package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.XmlText
import com.intellij.util.ProcessingContext

class PlayTemplateHtmlReferenceContributor : PsiReferenceContributor() {

    private data class ExpressionToken(
        val name: String,
        val start: Int,
        val end: Int,
        val qualifierName: String? = null,
        val methodCall: Boolean = false
    )

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java).inside(XmlText::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return PsiReference.EMPTY_ARRAY
                    val xmlText = element.parent as? XmlText ?: return PsiReference.EMPTY_ARRAY
                    val leafRange = element.textRange
                    return referencesInXmlText(element, xmlText).toTypedArray()
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }

    private fun referencesInXmlText(element: PsiElement, xmlText: XmlText): List<PsiReference> {
        val text = xmlText.text
        val leafStart = element.textRange.startOffset
        val leafEnd = element.textRange.endOffset
        val refs = mutableListOf<PsiReference>()

        fun addReferenceIfInside(
            absoluteStart: Int,
            absoluteEnd: Int,
            factory: (TextRange) -> PsiReference
        ) {
            if (absoluteStart < leafStart || absoluteEnd > leafEnd) return
            refs.add(factory(TextRange(absoluteStart - leafStart, absoluteEnd - leafStart)))
        }

        // #{extends 'path' /}
        PlayTemplatePatterns.TAG_EXTENDS.findAll(text).forEach { match ->
            val path = match.groupValues[1]
            val quotePos = match.value.indexOf('\'').takeIf { it >= 0 }
                ?: match.value.indexOf('"').takeIf { it >= 0 } ?: return@forEach
            val keyStart = xmlText.textRange.startOffset + match.range.first + quotePos + 1
            val keyEnd = keyStart + path.length
            addReferenceIfInside(keyStart, keyEnd) { range -> PlayTemplatePathReference(element, range, path) }
        }

        // #{include 'path' /}
        PlayTemplatePatterns.TAG_INCLUDE.findAll(text).forEach { match ->
            val path = match.groupValues[1]
            val quotePos = match.value.indexOf('\'').takeIf { it >= 0 }
                ?: match.value.indexOf('"').takeIf { it >= 0 } ?: return@forEach
            val keyStart = xmlText.textRange.startOffset + match.range.first + quotePos + 1
            val keyEnd = keyStart + path.length
            addReferenceIfInside(keyStart, keyEnd) { range -> PlayTemplatePathReference(element, range, path) }
        }

        // #{tagname ...} — custom tags only
        PlayTemplatePatterns.TAG_NAME_AT.findAll(text).forEach { match ->
            val tagName = match.groupValues[1]
            if (tagName !in PlayTemplatePatterns.BUILTIN_TAGS) {
                val nameStart = xmlText.textRange.startOffset + match.range.first + 2 // skip #{
                val nameEnd = nameStart + tagName.length
                addReferenceIfInside(nameStart, nameEnd) { range -> PlayTemplateTagFileReference(element, range, tagName) }
            }
        }

        // #{if ...}, #{elseif ...}, #{ifnot ...}
        PlayTemplatePatterns.TAG_CONDITION.findAll(text).forEach { match ->
            val bodyRange = match.groups[1]?.range ?: return@forEach
            val bodyText = match.groupValues[1]
            extractExpressionTokens(bodyText, bodyRange.first).forEach { token ->
                val nameStart = xmlText.textRange.startOffset + token.start
                val nameEnd = xmlText.textRange.startOffset + token.end
                addReferenceIfInside(nameStart, nameEnd) { range ->
                    PlayTemplateVariableReference(
                        element,
                        range,
                        token.name,
                        token.qualifierName,
                        token.methodCall
                    )
                }
            }
        }

        // @{Controller.action(args)} reverse routes
        PlayTemplatePatterns.REVERSE_ROUTE.findAll(text).forEach { match ->
            val fullRef = match.groupValues[1]
            val dotIdx = fullRef.lastIndexOf('.')
            if (dotIdx > 0) {
                val controllerName = fullRef.substring(0, dotIdx)
                val actionName = fullRef.substring(dotIdx + 1)
                val refStart = xmlText.textRange.startOffset + match.range.first + match.value.indexOf(fullRef)
                val controllerEnd = refStart + controllerName.length
                val actionStart = controllerEnd + 1
                val actionEnd = actionStart + actionName.length
                addReferenceIfInside(controllerEnd - controllerName.length, controllerEnd) { range ->
                    PlayTemplateRouteReference(
                        element,
                        range,
                        controllerName,
                        actionName,
                        PlayTemplateRouteReference.Kind.CONTROLLER
                    )
                }
                addReferenceIfInside(actionStart, actionEnd) { range ->
                    PlayTemplateRouteReference(
                        element,
                        range,
                        controllerName,
                        actionName,
                        PlayTemplateRouteReference.Kind.ACTION
                    )
                }
            }
        }

        // ${variable.property} and nested identifiers inside groovy expressions
        PlayTemplatePatterns.GROOVY_EXPR_BLOCK.findAll(text).forEach { match ->
            val bodyRange = match.groups[1]?.range ?: return@forEach
            val bodyText = match.groupValues[1]
            extractExpressionTokens(bodyText, bodyRange.first).forEach { token ->
                val nameStart = xmlText.textRange.startOffset + token.start
                val nameEnd = xmlText.textRange.startOffset + token.end
                addReferenceIfInside(nameStart, nameEnd) { range ->
                    PlayTemplateVariableReference(element, range, token.name, token.qualifierName, token.methodCall)
                }
            }
        }

        // %{ expr; ... }% script block — RHS of assignments and free expressions
        PlayTemplatePatterns.SCRIPT_BLOCK.findAll(text).forEach { blockMatch ->
            val bodyRange = blockMatch.groups[1]?.range ?: return@forEach
            val bodyText = blockMatch.groupValues[1]
            extractExpressionTokens(bodyText, bodyRange.first, skipAssignmentLhs = true).forEach { token ->
                val nameStart = xmlText.textRange.startOffset + token.start
                val nameEnd = xmlText.textRange.startOffset + token.end
                addReferenceIfInside(nameStart, nameEnd) { range ->
                    PlayTemplateVariableReference(element, range, token.name, token.qualifierName, token.methodCall)
                }
            }
        }

        // #{list expr, as:'var'} — items expression (e.g. resume.paiements)
        PlayTemplatePatterns.LIST_TAG_ITEMS_AND_VAR.findAll(text).forEach { match ->
            val itemsRange = match.groups[1]?.range ?: return@forEach
            val itemsText = match.groupValues[1]
            extractExpressionTokens(itemsText, itemsRange.first).forEach { token ->
                val nameStart = xmlText.textRange.startOffset + token.start
                val nameEnd = xmlText.textRange.startOffset + token.end
                addReferenceIfInside(nameStart, nameEnd) { range ->
                    PlayTemplateVariableReference(element, range, token.name, token.qualifierName, token.methodCall)
                }
            }
        }

        // @{'/public/path'} static assets
        PlayTemplatePatterns.STATIC_ASSET.findAll(text).forEach { match ->
            val path = match.groupValues[1]
            if (path.startsWith("/public/") || path.startsWith("public/")) {
                val quotePos = match.value.indexOf('\'').takeIf { it >= 0 }
                    ?: match.value.indexOf('"').takeIf { it >= 0 } ?: return@forEach
                val pathStart = xmlText.textRange.startOffset + match.range.first + quotePos + 1
                val pathEnd = pathStart + path.length
                addReferenceIfInside(pathStart, pathEnd) { range -> PlayTemplateStaticAssetReference(element, range, path) }
            }
        }

        return refs
    }

    private fun extractExpressionTokens(
        bodyText: String,
        bodyStartOffset: Int,
        skipAssignmentLhs: Boolean = false
    ): List<ExpressionToken> {
        val tokens = mutableListOf<ExpressionToken>()
        val identifierRegex = Regex("""[A-Za-z_]\w*""")
        identifierRegex.findAll(bodyText).forEach { match ->
            val name = match.value
            if (name in GROOVY_KEYWORDS) return@forEach
            val localStart = match.range.first
            val localEnd = match.range.last + 1
            val previous = previousNonWhitespace(bodyText, localStart - 1)
            val next = nextNonWhitespace(bodyText, localEnd)
            // Skip assignment LHS: identifier followed by '=' but not '=='
            if (skipAssignmentLhs && previous != '.' && isAssignmentLhs(bodyText, localEnd)) return@forEach
            if (previous == '.') {
                val qualifierName = findDirectQualifierName(bodyText, localStart - 1) ?: return@forEach
                tokens += ExpressionToken(
                    name = name,
                    start = bodyStartOffset + localStart,
                    end = bodyStartOffset + localEnd,
                    qualifierName = qualifierName,
                    methodCall = next == '('
                )
            } else {
                tokens += ExpressionToken(
                    name = name,
                    start = bodyStartOffset + localStart,
                    end = bodyStartOffset + localEnd
                )
            }
        }
        return tokens
    }

    private fun isAssignmentLhs(text: String, fromIndex: Int): Boolean {
        var idx = fromIndex
        while (idx < text.length && text[idx].isWhitespace()) idx++
        if (idx >= text.length || text[idx] != '=') return false
        val afterEq = idx + 1
        return afterEq >= text.length || text[afterEq] != '='
    }

    private fun previousNonWhitespace(text: String, startIndex: Int): Char? {
        var index = startIndex
        while (index >= 0) {
            val c = text[index]
            if (!c.isWhitespace()) return c
            index--
        }
        return null
    }

    private fun nextNonWhitespace(text: String, startIndex: Int): Char? {
        var index = startIndex
        while (index < text.length) {
            val c = text[index]
            if (!c.isWhitespace()) return c
            index++
        }
        return null
    }

    private fun findDirectQualifierName(text: String, dotIndex: Int): String? {
        var index = dotIndex - 1
        while (index >= 0 && text[index].isWhitespace()) {
            index--
        }
        if (index < 0 || (!text[index].isLetterOrDigit() && text[index] != '_')) return null
        val end = index + 1
        while (index >= 0 && (text[index].isLetterOrDigit() || text[index] == '_')) {
            index--
        }
        return text.substring(index + 1, end)
    }

    companion object {
        private val GROOVY_KEYWORDS = setOf(
            "new", "null", "true", "false", "def", "if", "else", "return",
            "in", "as", "instanceof", "class", "this", "super"
        )
    }
}
