package com.github.pablolec.play1toolkit.playconfig.inspections

import com.github.pablolec.play1toolkit.playconfig.references.PlayConfigContextDetector
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.codeInspection.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*

class UnresolvedPlayConfigKeyInspection : LocalInspectionTool() {
    override fun getDisplayName() = "Unresolved Play configuration key"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "UnresolvedPlayConfigKey"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR

        return object : JavaElementVisitor() {
            override fun visitLiteralExpression(expression: PsiLiteralExpression) {
                val key = expression.value as? String ?: return
                if (key.isBlank() || !key.contains('.')) return
                if (!PlayConfigContextDetector.isConfigKeyContext(expression)) return

                val svc = PlayConfigService.getInstance(expression.project)
                val keys = svc.keysForLogical(key)
                if (keys.isEmpty()) {
                    holder.registerProblem(
                        expression,
                        "Play configuration key '$key' not found in application.conf",
                        ProblemHighlightType.WEAK_WARNING,
                        CreatePlayConfigKeyQuickFix(key)
                    )
                }
            }
        }
    }
}
