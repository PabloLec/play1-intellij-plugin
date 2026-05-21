package com.github.pablolec.play1toolkit.playcache.documentation

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsageKind
import com.github.pablolec.play1toolkit.playcache.model.PlayCachedActionInfo
import com.github.pablolec.play1toolkit.playcache.model.PlayCachedTemplateFragment
import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateValueResolver
import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.response.PlayActionResponseService
import com.github.pablolec.play1toolkit.response.PlayResponsePresentation
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil

class PlayCacheDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (DumbService.isDumb(file.project)) return emptyList()
        val element = file.findElementAt(offset) ?: return emptyList()

        cacheCallTarget(element)?.let { return listOf(it) }
        cacheForTarget(element)?.let { return listOf(it) }
        templateCacheTarget(element)?.let { return listOf(it) }
        return emptyList()
    }

    private fun cacheCallTarget(element: PsiElement): DocumentationTarget? {
        val call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java) ?: return null
        if (!PlayCacheArgExtractor.isCacheCall(call)) return null
        val kind = PlayCacheArgExtractor.methodKind(call) ?: return null
        val service = PlayCacheService.getInstance(call.project)
        val arguments = call.argumentList.expressions
        val key = if (kind == PlayCacheUsageKind.JAVA_CLEAR) PlayCacheKey.Missing
        else PlayCacheArgExtractor.extractKey(arguments.firstOrNull())
        val ttl = when (kind) {
            PlayCacheUsageKind.JAVA_WRITE,
            PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT,
            PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT,
            PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> arguments.getOrNull(2)
                ?.let { PlayCacheArgExtractor.extractTtl(it) }
                ?: PlayCacheTtl.Absent
            else -> PlayCacheTtl.Absent
        }
        val relatedCounts = if (key is PlayCacheKey.Static) {
            val related = service.getUsagesByStaticKey(key.value)
            Counts(
                reads = related.count { it.kind == PlayCacheUsageKind.JAVA_READ || it.kind == PlayCacheUsageKind.JAVA_READ_OR_COMPUTE },
                writes = related.count {
                    it.kind == PlayCacheUsageKind.JAVA_WRITE ||
                        it.kind == PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT ||
                        it.kind == PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT
                },
                invalidations = related.count { it.kind == PlayCacheUsageKind.JAVA_INVALIDATION }
            )
        } else null

        val valueExpr = arguments.getOrNull(1)
        val html = buildJavaCallDoc(
            kind = kind,
            key = key,
            ttl = ttl,
            valueText = valueExpr?.text,
            valueType = valueExpr?.type?.presentableText,
            keyConfigurationKey = PlayCacheArgExtractor.extractConfigKey(arguments.firstOrNull()),
            ttlConfigurationKey = arguments.getOrNull(2)?.let { PlayCacheArgExtractor.extractConfigKey(it) },
            counts = relatedCounts
        )
        return PlayCacheDocTarget(SmartPointerManager.createPointer(call), "Play cache", html)
    }

    private fun cacheForTarget(element: PsiElement): DocumentationTarget? {
        val annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation::class.java) ?: return null
        val qn = annotation.qualifiedName
        val simple = annotation.nameReferenceElement?.referenceName
        if (qn != "play.cache.CacheFor" && simple != "CacheFor") return null
        val method = PsiTreeUtil.getParentOfType(annotation, PsiMethod::class.java) ?: return null
        val service = PlayCacheService.getInstance(annotation.project)
        val info = service.findCachedAction(method) ?: return null
        val html = buildCachedActionDoc(annotation.project, info)
        return PlayCacheDocTarget(SmartPointerManager.createPointer(annotation), "Cached Play action", html)
    }

    private fun templateCacheTarget(element: PsiElement): DocumentationTarget? {
        val file = element.containingFile ?: return null
        val service = PlayCacheService.getInstance(file.project)
        val fragments = service.getTemplateFragments().filter { it.templateFile == file }
        if (fragments.isEmpty()) return null
        val offset = element.textOffset
        val fragment = fragments.firstOrNull { it.openTagRange.containsOffset(offset) } ?: return null
        val html = buildTemplateFragmentDoc(fragment)
        return PlayCacheDocTarget(SmartPointerManager.createPointer(element), "Cached template fragment", html)
    }

    private fun buildJavaCallDoc(
        kind: PlayCacheUsageKind,
        key: PlayCacheKey,
        ttl: PlayCacheTtl,
        valueText: String?,
        valueType: String?,
        keyConfigurationKey: String?,
        ttlConfigurationKey: String?,
        counts: Counts?
    ): String = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        append("<b>${escape(title(kind))}</b>")
        append(DocumentationMarkup.DEFINITION_END)
        append(DocumentationMarkup.CONTENT_START)
        append("<table>")
        if (kind != PlayCacheUsageKind.JAVA_CLEAR) {
            append("<tr><td><b>Key:</b></td><td>${escape(keyLabel(key))}</td></tr>")
            if (!keyConfigurationKey.isNullOrBlank()) {
                append("<tr><td><b>Key from configuration:</b></td><td>${escape(keyConfigurationKey)}</td></tr>")
            }
            append("<tr><td><b>TTL:</b></td><td>${escape(ttlLabel(ttl))}</td></tr>")
            if (!ttlConfigurationKey.isNullOrBlank()) {
                append("<tr><td><b>TTL from configuration:</b></td><td>${escape(ttlConfigurationKey)}</td></tr>")
            }
            if (!valueText.isNullOrBlank()) {
                append("<tr><td><b>Value:</b></td><td><code>${escape(valueText)}</code></td></tr>")
            }
            if (!valueType.isNullOrBlank()) {
                append("<tr><td><b>Value type:</b></td><td>${escape(valueType)}</td></tr>")
            }
            if (counts != null) {
                append("<tr><td><b>Related usages:</b></td><td>reads: ${counts.reads} · writes: ${counts.writes} · invalidations: ${counts.invalidations}</td></tr>")
            }
        } else {
            append("<tr><td colspan=\"2\">This clears the whole Play cache backend.</td></tr>")
        }
        append("</table>")
        append(DocumentationMarkup.CONTENT_END)
    }

    private fun buildCachedActionDoc(project: Project, info: PlayCachedActionInfo): String {
        val controllerName = info.controllerClass.name ?: "?"
        val methodName = info.actionMethod.name
        val routes = info.routes.ifEmpty { Play1ViewUtils.findRoutesForAction(project, controllerName, methodName) }
        val response = info.responseInfo ?: runCatching { PlayActionResponseService.getInstance(project).analyze(info.actionMethod) }.getOrNull()
        val viewFile = Play1ViewUtils.findViewFile(project, controllerName, methodName)
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>Play cached action</b>")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<table>")
            append("<tr><td><b>Action:</b></td><td>${escape("$controllerName.$methodName")}</td></tr>")
            append("<tr><td><b>TTL:</b></td><td>${escape(ttlLabel(info.ttl))}</td></tr>")
            if (routes.isNotEmpty()) {
                val routeText = routes.joinToString("<br/>") { route ->
                    val httpMethod = route.getHttpMethod()?.text?.trim().orEmpty()
                    val path = route.getPath()?.trim().orEmpty()
                    escape("$httpMethod $path".trim())
                }
                append("<tr><td><b>Routes:</b></td><td>$routeText</td></tr>")
            }
            if (response != null) {
                append("<tr><td><b>Response:</b></td><td>${escape(PlayResponsePresentation.shortLabel(response.kind))}</td></tr>")
                if (PlayResponsePresentation.shortLabel(response.kind) == "HTML" && viewFile != null) {
                    append("<tr><td><b>Template:</b></td><td>${escape(viewFile.path.substringAfterLast("/app/views/"))}</td></tr>")
                }
            }
            append("</table>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildTemplateFragmentDoc(fragment: PlayCachedTemplateFragment): String = buildString {
        val keyInfo = PlayCacheTemplateValueResolver.resolveKey(fragment.templateFile.project, fragment)
        val ttlInfo = PlayCacheTemplateValueResolver.resolveTtl(fragment.templateFile.project, fragment)
        val guardInfo = PlayCacheTemplateValueResolver.resolveGuard(fragment.templateFile.project, fragment.templateFile)
        append(DocumentationMarkup.DEFINITION_START)
        append("<b>Play template cache fragment</b>")
        append(DocumentationMarkup.DEFINITION_END)
        append(DocumentationMarkup.CONTENT_START)
        append("<table>")
        append("<tr><td><b>Key:</b></td><td>${escape(keyLabel(fragment.key))}</td></tr>")
        if (keyInfo.resolvedValue != null || keyInfo.configurationKey != null) {
            append("<tr><td><b>Resolved key:</b></td><td>${escape(keyInfo.displayText)}</td></tr>")
        }
        append("<tr><td><b>Expiration:</b></td><td>${escape(ttlLabel(fragment.ttl))}</td></tr>")
        if (ttlInfo.resolvedValue != null || ttlInfo.configurationKey != null || ttlInfo.configurationValue != null) {
            append("<tr><td><b>Resolved expiration:</b></td><td>${escape(ttlInfo.displayText)}</td></tr>")
        }
        if (guardInfo?.booleanValue != null) {
            append("<tr><td><b>Cache guard:</b></td><td>${if (guardInfo.booleanValue) "enabled" else "disabled"}${guardInfo.configurationKey?.let { " (${escape(it)}=${escape(guardInfo.configurationValue ?: "?")})" } ?: ""}</td></tr>")
        }
        val template = fragment.templateFile.virtualFile?.path?.substringAfterLast("/app/views/")
            ?: fragment.templateFile.name
        append("<tr><td><b>Template:</b></td><td>${escape(template)}</td></tr>")
        if (fragment.includedTemplatePaths.isNotEmpty()) {
            append("<tr><td><b>Includes:</b></td><td>${escape(fragment.includedTemplatePaths.joinToString(", "))}</td></tr>")
        }
        append("</table>")
        append(DocumentationMarkup.CONTENT_END)
    }

    private fun title(kind: PlayCacheUsageKind): String = when (kind) {
        PlayCacheUsageKind.JAVA_READ -> "Play cache read"
        PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> "Play cache read or compute"
        PlayCacheUsageKind.JAVA_WRITE -> "Play cache write"
        PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT -> "Play cache write if absent"
        PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT -> "Play cache write if present"
        PlayCacheUsageKind.JAVA_INVALIDATION -> "Play cache invalidation"
        PlayCacheUsageKind.JAVA_CLEAR -> "Play global cache clear"
        PlayCacheUsageKind.JAVA_MUTATION -> "Play cache mutation"
        else -> "Play cache"
    }

    private fun keyLabel(key: PlayCacheKey): String = when (key) {
        is PlayCacheKey.Static -> key.value
        is PlayCacheKey.Pattern -> "pattern ${key.value}"
        is PlayCacheKey.Dynamic -> "dynamic ${key.expressionText}"
        PlayCacheKey.Missing -> "—"
    }

    private fun ttlLabel(ttl: PlayCacheTtl): String = when (ttl) {
        is PlayCacheTtl.Static -> if (ttl.value.isEmpty()) "no expiration" else ttl.value
        is PlayCacheTtl.Dynamic -> "dynamic ${ttl.expressionText}"
        PlayCacheTtl.Absent -> "no expiration"
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private data class Counts(val reads: Int, val writes: Int, val invalidations: Int)
}

private class PlayCacheDocTarget(
    private val pointer: SmartPsiElementPointer<out PsiElement>,
    private val title: String,
    private val html: String
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> =
        Pointer { PlayCacheDocTarget(pointer, title, html) }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(title).presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(html)

    override fun computeDocumentationHint(): String? = null

    @Suppress("unused")
    private fun ignored(): PsiElement? = pointer.element
}
