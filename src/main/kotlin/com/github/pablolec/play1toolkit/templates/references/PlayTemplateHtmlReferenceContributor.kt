package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.XmlText
import com.intellij.util.ProcessingContext

class PlayTemplateHtmlReferenceContributor : PsiReferenceContributor() {

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
}
