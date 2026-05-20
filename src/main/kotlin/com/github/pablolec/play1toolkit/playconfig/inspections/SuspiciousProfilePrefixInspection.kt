package com.github.pablolec.play1toolkit.playconfig.inspections

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.codeInspection.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*

class SuspiciousProfilePrefixInspection : LocalInspectionTool() {
    override fun getDisplayName() = "Suspicious Play configuration profile prefix"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "SuspiciousProfilePrefix"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (holder.file.language != PlayConfigLanguage) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val prop = element as? PlayConfigProperty ?: return
                val profile = prop.profile ?: return

                val svc = PlayConfigService.getInstance(element.project)
                val knownProfiles = svc.availableProfiles().filter { it != profile }

                val similar = knownProfiles.firstOrNull { levenshtein(profile, it) == 1 }
                if (similar != null) {
                    holder.registerProblem(
                        prop.nameIdentifier ?: prop,
                        "Profile prefix '$profile' looks similar to '$similar' — possible typo?",
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
