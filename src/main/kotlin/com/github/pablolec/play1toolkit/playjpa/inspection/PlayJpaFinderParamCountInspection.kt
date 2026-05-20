package com.github.pablolec.play1toolkit.playjpa.inspection

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaFinderUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression

class PlayJpaFinderParamCountInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                if (!PlayJpaFinderUtils.isFinderCall(expression)) return
                val queryArg = PlayJpaFinderUtils.getFinderQueryArg(expression) ?: return
                val qualifier = expression.methodExpression.qualifierExpression?.text?.trim() ?: return
                val svc = PlayJpaModelService.getInstance(expression.project)
                svc.findModelByName(qualifier) ?: return

                val placeholders = PlayJpaFinderUtils.countQueryPlaceholders(queryArg)
                if (placeholders == 0) return

                // args[0] is the query string; remaining args are parameters
                val paramCount = expression.argumentList.expressions.size - 1
                if (paramCount != placeholders) {
                    holder.registerProblem(
                        expression.argumentList,
                        "Expected $placeholders parameter(s) for query but found $paramCount"
                    )
                }
            }
        }
    }
}
