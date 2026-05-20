package com.github.pablolec.play1toolkit.playmessages.inspections

import com.github.pablolec.play1toolkit.playmessages.references.PlayMessagesContextDetector
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression

class UnknownPlayMessageKeyJavaInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitLiteralExpression(expression: PsiLiteralExpression) {
                if (DumbService.isDumb(expression.project)) return
                val value = expression.value as? String ?: return
                if (value.isBlank()) return
                if (!PlayMessagesContextDetector.isMessagesKeyContext(expression)) return
                val svc = PlayMessagesService.getInstance(expression.project)
                if (svc.entriesForKey(value).isEmpty()) {
                    holder.registerProblem(
                        expression,
                        "Unknown Play message key '$value'",
                        CreatePlayMessageKeyQuickFix(value)
                    )
                }
            }
        }
    }
}
