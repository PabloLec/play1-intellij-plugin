package com.github.pablolec.play1toolkit.playconfig.inspections

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.codeInspection.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*

class UnresolvedEnvironmentVariableInspection : LocalInspectionTool() {
    override fun getDisplayName() = "Unresolved environment variable in Play configuration"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "UnresolvedEnvironmentVariable"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (holder.file.language != PlayConfigLanguage) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val prop = element as? PlayConfigProperty ?: return
                val svc = PlayConfigService.getInstance(element.project)
                val envVars = svc.extractEnvVarNames(prop.valueText)
                if (envVars.isEmpty()) return

                val runEnv = svc.resolveEnvVarsFromRunConfig()
                for (envVar in envVars) {
                    if (envVar !in runEnv && System.getenv(envVar) == null) {
                        holder.registerProblem(
                            prop.nameIdentifier ?: prop,
                            "Environment variable '\${$envVar}' is not available in the current context",
                            ProblemHighlightType.WEAK_WARNING
                        )
                    }
                }
            }
        }
    }
}
