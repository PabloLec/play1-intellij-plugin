package com.github.pablolec.play1toolkit.templates.documentation

import com.github.pablolec.play1toolkit.response.PlayActionResponseService
import com.github.pablolec.play1toolkit.response.PlayResponsePresentation
import com.github.pablolec.play1toolkit.templates.references.PlayTemplateJavaContextDetector
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateVariableResolver
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.project.DumbService
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlText

class PlayTemplateDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (DumbService.isDumb(file.project)) return emptyList()
        val element = file.findElementAt(offset) ?: return emptyList()
        return buildTarget(element, offset)?.let(::listOf) ?: emptyList()
    }

    private fun buildTarget(element: PsiElement, offset: Int): DocumentationTarget? {
        val file = element.containingFile ?: return null
        val project = file.project

        val literal = element.parent as? PsiLiteralExpression
        if (literal != null && PlayTemplateJavaContextDetector.isRenderTemplatePathContext(literal)) {
            val path = literal.value as? String ?: return null
            val resolved = PlayTemplateFileUtils.resolveTemplatePath(project, path)
            return PlayTemplateDocumentationTarget(
                label = path,
                pointer = SmartPointerManager.createPointer(literal),
                html = buildTemplateDoc(project, path, resolved?.name)
            )
        }

        if (!PlayTemplateFileUtils.isInViewsDirectory(file)) return null

        val xmlText = element.parent as? XmlText ?: element as? XmlText ?: return null
        val localOffset = offset - xmlText.textRange.startOffset

        xmlText.references.firstOrNull { it.rangeInElement.contains(localOffset) }?.let { reference ->
            val resolved = reference.resolve()
            return when (reference) {
                is com.github.pablolec.play1toolkit.templates.references.PlayTemplatePathReference -> {
                    PlayTemplateDocumentationTarget(
                        label = reference.canonicalText,
                        pointer = SmartPointerManager.createPointer(xmlText),
                        html = buildTemplateDoc(project, reference.canonicalText, resolved?.containingFile?.name ?: resolved?.text)
                    )
                }
                is com.github.pablolec.play1toolkit.templates.references.PlayTemplateTagFileReference -> {
                    PlayTemplateDocumentationTarget(
                        label = reference.canonicalText,
                        pointer = SmartPointerManager.createPointer(xmlText),
                        html = buildCustomTagDoc(project, reference.canonicalText, resolved as? PsiFile)
                    )
                }
                is com.github.pablolec.play1toolkit.templates.references.PlayTemplateRouteReference -> {
                    val fullRoute = PlayTemplatePatterns.REVERSE_ROUTE.findAll(xmlText.text)
                        .firstOrNull { localOffset in it.range }
                        ?.groupValues?.get(1)
                        ?: reference.canonicalText
                    PlayTemplateDocumentationTarget(
                        label = fullRoute,
                        pointer = SmartPointerManager.createPointer(xmlText),
                        html = buildRouteDoc(project, fullRoute)
                    )
                }
                is com.github.pablolec.play1toolkit.templates.references.PlayTemplateStaticAssetReference -> {
                    PlayTemplateDocumentationTarget(
                        label = reference.canonicalText,
                        pointer = SmartPointerManager.createPointer(xmlText),
                        html = buildAssetDoc(reference.canonicalText, resolved as? PsiFile)
                    )
                }
                else -> null
            }
        }

        matchBuiltInTag(xmlText.text, localOffset)?.let { tagName ->
            val doc = PlayTemplatePatterns.BUILTIN_TAG_DOCS[tagName] ?: return@let
            return PlayTemplateDocumentationTarget(
                label = tagName,
                pointer = SmartPointerManager.createPointer(xmlText),
                html = buildString {
                    append(DocumentationMarkup.DEFINITION_START)
                    append("<b>Play built-in tag</b> #{$tagName}")
                    append(DocumentationMarkup.DEFINITION_END)
                    append(DocumentationMarkup.CONTENT_START)
                    append(doc)
                    append(DocumentationMarkup.CONTENT_END)
                }
            )
        }

        matchVariable(xmlText.text, localOffset)?.let { variableName ->
            return PlayTemplateDocumentationTarget(
                label = variableName,
                pointer = SmartPointerManager.createPointer(xmlText),
                html = buildVariableDoc(file, variableName)
            )
        }

        return null
    }

    private fun buildTemplateDoc(project: com.intellij.openapi.project.Project, path: String, resolvedName: String?): String {
        val resolved = PlayTemplateFileUtils.resolveTemplatePath(project, path)
        val usages = resolved?.let {
            try {
                com.intellij.psi.search.searches.ReferencesSearch.search(
                    com.intellij.psi.PsiManager.getInstance(project).findFile(it) ?: return@let 0
                ).findAll().size
            } catch (_: Exception) {
                0
            }
        } ?: 0

        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>Play template</b> $path")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<table>")
            append("<tr><td><b>Resolved file:</b></td><td>${escape(resolvedName ?: "unresolved")}</td></tr>")
            append("<tr><td><b>Usages:</b></td><td>$usages</td></tr>")
            append("</table>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildCustomTagDoc(project: com.intellij.openapi.project.Project, tagName: String, file: PsiFile?): String {
        val service = PlayTemplateService.getInstance(project)
        val info = service.findTag(tagName) ?: service.findTagBySimpleName(tagName)
        val usages = file?.let {
            try {
                com.intellij.psi.search.searches.ReferencesSearch.search(it).findAll().size
            } catch (_: Exception) {
                0
            }
        } ?: 0
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>Custom Play tag</b> #{$tagName}")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<table>")
            append("<tr><td><b>File:</b></td><td>${escape(info?.logicalPath ?: "unresolved")}</td></tr>")
            append("<tr><td><b>Parameters:</b></td><td>${escape(info?.parameters?.sorted()?.joinToString(", ") ?: "—")}</td></tr>")
            append("<tr><td><b>Usages:</b></td><td>$usages</td></tr>")
            append("</table>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildRouteDoc(project: com.intellij.openapi.project.Project, routeText: String): String {
        val dotIndex = routeText.lastIndexOf('.')
        if (dotIndex < 0) return routeText
        val controllerName = routeText.substring(0, dotIndex)
        val actionName = routeText.substring(dotIndex + 1)
        val routes = com.github.pablolec.play1toolkit.render.Play1ViewUtils.findRoutesForAction(
            project,
            controllerName.substringAfterLast('.'),
            actionName
        )
        val method = com.github.pablolec.play1toolkit.routes.RoutesControllerResolver.resolveMethod(project, controllerName, actionName)
        val response = method?.let { PlayActionResponseService.getInstance(project).analyze(it) }
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>Reverse route</b> @{${escape(routeText)}(...)}")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<table>")
            if (routes.isNotEmpty()) {
                append("<tr><td><b>Route:</b></td><td>${escape(routes.first().getHttpMethod()?.text ?: "?")} ${escape(routes.first().getPath() ?: "?")}</td></tr>")
            }
            append("<tr><td><b>Action:</b></td><td>${escape(routeText)}</td></tr>")
            if (response != null) {
                append("<tr><td><b>Response:</b></td><td>${escape(PlayResponsePresentation.shortLabel(response.kind))}</td></tr>")
            }
            append("</table>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildAssetDoc(assetPath: String, file: PsiFile?): String = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        append("<b>Static asset</b> ${escape(assetPath)}")
        append(DocumentationMarkup.DEFINITION_END)
        append(DocumentationMarkup.CONTENT_START)
        append("<table>")
        append("<tr><td><b>Resolved file:</b></td><td>${escape(file?.virtualFile?.path ?: "unresolved")}</td></tr>")
        append("</table>")
        append(DocumentationMarkup.CONTENT_END)
    }

    private fun buildVariableDoc(file: PsiFile, variableName: String): String {
        val variables = PlayTemplateVariableResolver.getInstance(file.project).resolveVariables(file)
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>Template variable</b> ${escape(variableName)}")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append(
                if (variableName in variables) {
                    "Available in this template render context."
                } else {
                    "Variable is not known in this template render context."
                }
            )
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun matchBuiltInTag(text: String, offset: Int): String? =
        PlayTemplatePatterns.TAG_NAME_AT.findAll(text)
            .firstOrNull { offset in (it.range.first + 2)..(it.range.first + 1 + it.groupValues[1].length) }
            ?.groupValues?.get(1)
            ?.takeIf { it in PlayTemplatePatterns.BUILTIN_TAGS }

    private fun matchVariable(text: String, offset: Int): String? =
        PlayTemplatePatterns.GROOVY_EXPR.findAll(text)
            .firstOrNull { offset in it.range }
            ?.groupValues?.get(1)

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

private class PlayTemplateDocumentationTarget(
    private val label: String,
    private val pointer: com.intellij.psi.SmartPsiElementPointer<out PsiElement>,
    private val html: String
) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> =
        Pointer { pointer.element?.let { PlayTemplateDocumentationTarget(label, pointer, html) } }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(label).presentation()

    override fun computeDocumentation(): DocumentationResult =
        DocumentationResult.documentation(html)
}
