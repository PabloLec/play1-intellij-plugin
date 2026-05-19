package com.github.pablolec.play1toolkit.inspection

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class Play1MissingViewInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Missing Play 1 view for render() call"
    override fun getGroupDisplayName(): String = "Play v1 Toolkit"
    override fun getShortName(): String = "Play1MissingView"
    override fun isEnabledByDefault(): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                val methodName = expression.methodExpression.referenceName ?: return
                if (methodName != "render") return

                val containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java) ?: return
                val containingClass = containingMethod.containingClass ?: return
                if (!Play1ViewUtils.isPlayController(containingClass)) return

                val controllerName = containingClass.name ?: return
                val actionName = containingMethod.name
                val viewFile = Play1ViewUtils.findViewFile(expression.project, controllerName, actionName)

                if (viewFile == null) {
                    val viewPath = Play1ViewUtils.implicitViewPath(controllerName, actionName)
                    holder.registerProblem(
                        expression,
                        "View not found: $viewPath",
                        ProblemHighlightType.WARNING,
                        CreateMissingViewQuickFix(controllerName, actionName, viewPath)
                    )
                }
            }
        }
    }
}
