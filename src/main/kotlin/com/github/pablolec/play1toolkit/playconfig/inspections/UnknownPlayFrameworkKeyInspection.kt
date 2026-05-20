package com.github.pablolec.play1toolkit.playconfig.inspections

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigKnownKeys
import com.intellij.codeInspection.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*

class UnknownPlayFrameworkKeyInspection : LocalInspectionTool() {
    override fun getDisplayName() = "Unknown Play framework configuration key"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "UnknownPlayFrameworkKey"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (holder.file.language != PlayConfigLanguage) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val prop = element as? PlayConfigProperty ?: return
                val key = prop.logicalKey

                // Already known — no warning
                if (PlayConfigKnownKeys.isKnownKey(key)) return

                // Check if it looks like a Play framework key (starts with application., http., db., etc.)
                val frameworkPrefixes = listOf(
                    "application.", "http.", "db.", "jpa.", "mail.", "memcached.", "play.",
                    "hibernate.", "evolutions.", "jpda.", "https."
                )
                val looksLikeFrameworkKey = frameworkPrefixes.any { key.startsWith(it) }
                if (!looksLikeFrameworkKey) return

                // Find the closest known key
                val closest = PlayConfigKnownKeys.allKnownKeys()
                    .filter { it.startsWith(key.substringBefore('.') + ".") }
                    .minByOrNull { levenshtein(key, it) }

                if (closest != null && levenshtein(key, closest) in 1..2) {
                    holder.registerProblem(
                        prop.nameIdentifier ?: prop,
                        "Unknown Play framework key '$key'. Did you mean '$closest'?",
                        ProblemHighlightType.WEAK_WARNING
                    )
                }
            }
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in dp.indices) dp[i][0] = i
        for (j in dp[0].indices) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }
}
