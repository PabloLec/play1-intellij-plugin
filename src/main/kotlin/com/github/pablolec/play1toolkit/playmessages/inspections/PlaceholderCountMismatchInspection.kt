package com.github.pablolec.play1toolkit.playmessages.inspections

import com.github.pablolec.play1toolkit.playmessages.references.PlayMessagesContextDetector
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression

class PlaceholderCountMismatchInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitLiteralExpression(expression: PsiLiteralExpression) {
                if (DumbService.isDumb(expression.project)) return
                val key = expression.value as? String ?: return
                if (key.isBlank()) return
                if (!PlayMessagesContextDetector.isMessagesKeyContext(expression)) return
                val svc = PlayMessagesService.getInstance(expression.project)
                val defaultEntry = svc.defaultEntry(key) ?: return
                val expectedPlaceholders = PlayMessagesService.countPlaceholders(defaultEntry.value)
                if (expectedPlaceholders == 0) return
                val actualArgs = PlayMessagesContextDetector.getFormatArgCount(expression)
                if (actualArgs != expectedPlaceholders) {
                    holder.registerProblem(
                        expression,
                        "Message '$key' expects $expectedPlaceholders argument(s) but $actualArgs provided"
                    )
                }
            }
        }
    }
}
