package com.github.pablolec.play1toolkit.playconfig.inspections

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigTokenTypes
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigKnownKeys
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.github.pablolec.play1toolkit.playconfig.settings.PlayConfigProjectSettings
import com.intellij.codeInspection.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch

class UnusedPlayConfigKeyInspection : LocalInspectionTool() {
    override fun getDisplayName() = "Unused Play configuration key"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "UnusedPlayConfigKey"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (holder.file.language != PlayConfigLanguage) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is PlayConfigProperty) return
                val logicalKey = element.logicalKey

                // Never flag known framework/library keys
                if (PlayConfigKnownKeys.isKnownKey(logicalKey)) return

                // Check additional user-defined prefixes
                val settings = PlayConfigProjectSettings.getInstance(element.project)
                if (settings.additionalKnownKeyPrefixes.any { logicalKey.startsWith(it) }) return

                // Check usages
                val usages = try {
                    ReferencesSearch.search(element).findAll()
                } catch (e: Exception) {
                    return
                }

                if (usages.isEmpty()) {
                    holder.registerProblem(
                        element.nameIdentifier ?: element,
                        "Play configuration key '$logicalKey' appears to have no usages",
                        ProblemHighlightType.INFORMATION
                    )
                }
            }
        }
    }
}
