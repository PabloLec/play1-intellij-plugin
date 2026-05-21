package com.github.pablolec.play1toolkit.playcache.util

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsageKind
import com.github.pablolec.play1toolkit.playconfig.references.PlayConfigContextDetector
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.JavaTokenType

object PlayCacheArgExtractor {
    private val TEMPLATE_INTERPOLATION = Regex("""\$\{[^}]+}""")
    private val TEMPLATE_INTERPOLATION_ONLY = Regex("""\s*\$\{[^}]+}\s*""")

    private val CACHE_QUALIFIERS = setOf("Cache", "play.cache.Cache")

    val CACHE_METHODS: Map<String, PlayCacheUsageKind> = mapOf(
        "get" to PlayCacheUsageKind.JAVA_READ,
        "getOrElse" to PlayCacheUsageKind.JAVA_READ_OR_COMPUTE,
        "set" to PlayCacheUsageKind.JAVA_WRITE,
        "add" to PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT,
        "safeAdd" to PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT,
        "replace" to PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT,
        "safeReplace" to PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT,
        "delete" to PlayCacheUsageKind.JAVA_INVALIDATION,
        "safeDelete" to PlayCacheUsageKind.JAVA_INVALIDATION,
        "clear" to PlayCacheUsageKind.JAVA_CLEAR,
        "incr" to PlayCacheUsageKind.JAVA_MUTATION,
        "decr" to PlayCacheUsageKind.JAVA_MUTATION
    )

    fun isCacheCall(call: PsiMethodCallExpression): Boolean {
        val name = call.methodExpression.referenceName ?: return false
        if (name !in CACHE_METHODS) return false
        val qualifier = call.methodExpression.qualifierExpression?.text?.trim() ?: return false
        if (qualifier in CACHE_QUALIFIERS) return true
        return qualifier.endsWith(".Cache")
    }

    fun methodKind(call: PsiMethodCallExpression): PlayCacheUsageKind? {
        val name = call.methodExpression.referenceName ?: return null
        return CACHE_METHODS[name]
    }

    fun extractKey(expr: PsiExpression?): PlayCacheKey {
        if (expr == null) return PlayCacheKey.Missing
        return when (expr) {
            is PsiLiteralExpression -> {
                val value = expr.value as? String
                if (value != null) PlayCacheKey.Static(value) else PlayCacheKey.Dynamic(expr.text)
            }
            is PsiPolyadicExpression -> if (isStringConcat(expr)) {
                PlayCacheKey.Pattern(renderConcat(expr))
            } else {
                PlayCacheKey.Dynamic(expr.text)
            }
            is PsiBinaryExpression -> if (isStringConcat(expr)) {
                PlayCacheKey.Pattern(renderConcat(expr))
            } else {
                PlayCacheKey.Dynamic(expr.text)
            }
            else -> PlayCacheKey.Dynamic(expr.text)
        }
    }

    fun extractTtl(expr: PsiExpression?): PlayCacheTtl {
        if (expr == null) return PlayCacheTtl.Absent
        if (expr is PsiLiteralExpression) {
            val value = expr.value as? String
            return if (value != null) PlayCacheTtl.Static(value) else PlayCacheTtl.Dynamic(expr.text)
        }
        return PlayCacheTtl.Dynamic(expr.text)
    }

    /**
     * Parse a raw template argument like `'dashboard'`, `"${cacheName}"`, `someExpr`.
     */
    fun parseCacheArg(rawValue: String): PlayCacheKey {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return PlayCacheKey.Missing
        val unquoted = unquote(trimmed)
        if (unquoted != null) {
            if (!unquoted.contains("\${")) return PlayCacheKey.Static(unquoted)
            return if (TEMPLATE_INTERPOLATION_ONLY.matches(unquoted)) {
                PlayCacheKey.Dynamic(unquoted)
            } else {
                PlayCacheKey.Pattern(TEMPLATE_INTERPOLATION.replace(unquoted) { "\${...}" })
            }
        }
        return PlayCacheKey.Dynamic(trimmed)
    }

    fun parseCacheTtlArg(rawValue: String?): PlayCacheTtl {
        if (rawValue.isNullOrBlank()) return PlayCacheTtl.Absent
        val trimmed = rawValue.trim()
        val unquoted = unquote(trimmed)
        if (unquoted != null) {
            if (unquoted.isEmpty()) return PlayCacheTtl.Static("")
            return if (unquoted.contains("\${")) {
                PlayCacheTtl.Dynamic(unquoted)
            } else {
                PlayCacheTtl.Static(unquoted)
            }
        }
        return PlayCacheTtl.Dynamic(trimmed)
    }

    fun extractConfigKey(expr: PsiExpression?): String? {
        val call = expr as? PsiMethodCallExpression ?: return null
        val literal = call.argumentList.expressions.firstOrNull() as? PsiLiteralExpression ?: return null
        val key = literal.value as? String ?: return null
        return key.takeIf { PlayConfigContextDetector.isDirectPlayConfigCall(literal) }
    }

    private fun unquote(text: String): String? {
        if (text.length < 2) return null
        val first = text.first()
        val last = text.last()
        if ((first == '\'' || first == '"') && first == last) {
            return text.substring(1, text.length - 1)
        }
        return null
    }

    private fun isStringConcat(expr: PsiBinaryExpression): Boolean {
        if (expr.operationTokenType != JavaTokenType.PLUS) return false
        return hasStringOperand(expr.lOperand) || hasStringOperand(expr.rOperand)
    }

    private fun isStringConcat(expr: PsiPolyadicExpression): Boolean {
        if (expr.operationTokenType != JavaTokenType.PLUS) return false
        return expr.operands.any { hasStringOperand(it) }
    }

    private fun hasStringOperand(expr: PsiExpression?): Boolean {
        if (expr == null) return false
        if (expr is PsiLiteralExpression && expr.value is String) return true
        val type = expr.type ?: return false
        return type.equalsToText("java.lang.String")
    }

    private fun renderConcat(expr: PsiPolyadicExpression): String {
        val parts = expr.operands.map { renderOperand(it) }
        return parts.joinToString("")
    }

    private fun renderConcat(expr: PsiBinaryExpression): String {
        val l = expr.lOperand
        val r = expr.rOperand
        return renderOperand(l) + (r?.let { renderOperand(it) } ?: "")
    }

    private fun renderOperand(expr: PsiExpression): String {
        if (expr is PsiLiteralExpression) {
            val value = expr.value
            if (value is String) return value
        }
        return "\${...}"
    }
}
