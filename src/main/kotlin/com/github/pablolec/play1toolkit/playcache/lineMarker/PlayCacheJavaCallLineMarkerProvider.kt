package com.github.pablolec.play1toolkit.playcache.lineMarker

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsageKind
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression

class PlayCacheJavaCallLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) return null
        val ref = element.parent as? PsiReferenceExpression ?: return null
        if (ref.referenceNameElement !== element) return null
        val call = ref.parent as? PsiMethodCallExpression ?: return null
        if (call.methodExpression !== ref) return null
        if (!PlayCacheArgExtractor.isCacheCall(call)) return null
        val kind = PlayCacheArgExtractor.methodKind(call) ?: return null

        val arguments = call.argumentList.expressions
        val key = if (kind != PlayCacheUsageKind.JAVA_CLEAR)
            PlayCacheArgExtractor.extractKey(arguments.firstOrNull())
        else PlayCacheKey.Missing
        val ttl = when (kind) {
            PlayCacheUsageKind.JAVA_WRITE,
            PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT,
            PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT,
            PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> arguments.getOrNull(2)
                ?.let { PlayCacheArgExtractor.extractTtl(it) }
                ?: PlayCacheTtl.Absent
            else -> PlayCacheTtl.Absent
        }
        val tooltip = buildTooltip(kind, key, ttl)
        val icon = iconFor(kind)
        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { tooltip }
        )
    }

    private fun buildTooltip(kind: PlayCacheUsageKind, key: PlayCacheKey, ttl: PlayCacheTtl): String {
        val keyLabel = when (key) {
            is PlayCacheKey.Static -> key.value
            is PlayCacheKey.Pattern -> "pattern ${key.value}"
            is PlayCacheKey.Dynamic -> "dynamic key"
            PlayCacheKey.Missing -> "—"
        }
        val ttlLabel = when (ttl) {
            is PlayCacheTtl.Static -> if (ttl.value.isEmpty()) "no expiration" else "ttl ${ttl.value}"
            is PlayCacheTtl.Dynamic -> "dynamic ttl"
            PlayCacheTtl.Absent -> "no expiration"
        }
        val verb = when (kind) {
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
        return if (kind == PlayCacheUsageKind.JAVA_CLEAR) verb else "$verb · key $keyLabel · $ttlLabel"
    }

    private fun iconFor(kind: PlayCacheUsageKind) = when (kind) {
        PlayCacheUsageKind.JAVA_READ -> AllIcons.Actions.Find
        PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> AllIcons.Actions.Find
        PlayCacheUsageKind.JAVA_WRITE -> AllIcons.Actions.MenuSaveall
        PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT -> AllIcons.Actions.MenuSaveall
        PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT -> AllIcons.Actions.MenuSaveall
        PlayCacheUsageKind.JAVA_INVALIDATION -> AllIcons.Actions.GC
        PlayCacheUsageKind.JAVA_CLEAR -> AllIcons.General.Warning
        PlayCacheUsageKind.JAVA_MUTATION -> AllIcons.Actions.Edit
        else -> AllIcons.General.Information
    }
}
