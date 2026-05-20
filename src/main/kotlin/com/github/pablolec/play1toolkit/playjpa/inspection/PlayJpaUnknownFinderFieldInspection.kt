package com.github.pablolec.play1toolkit.playjpa.inspection

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaFinderUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression

class PlayJpaUnknownFinderFieldInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                if (!PlayJpaFinderUtils.isFinderCall(expression)) return
                val queryArg = PlayJpaFinderUtils.getFinderQueryArg(expression) ?: return
                val qualifier = expression.methodExpression.qualifierExpression?.text?.trim() ?: return
                val svc = PlayJpaModelService.getInstance(expression.project)
                val model = svc.findModelByName(qualifier) ?: return
                val allFieldNames = svc.getAllFields(qualifier).map { it.name }.toSet()

                val byFields = PlayJpaFinderUtils.parseByFieldPattern(queryArg)
                val jpqlFields = PlayJpaFinderUtils.parseJpqlFields(queryArg)
                val allQueryFields = (byFields + jpqlFields).toSet()

                for (fieldName in allQueryFields) {
                    if (fieldName !in allFieldNames) {
                        val argExpr = expression.argumentList.expressions.firstOrNull() ?: continue
                        holder.registerProblem(argExpr, "Unknown field '$fieldName' in model '${model.className}'")
                    }
                }
            }
        }
    }
}
