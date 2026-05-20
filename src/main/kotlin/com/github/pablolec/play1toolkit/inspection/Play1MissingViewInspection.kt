package com.github.pablolec.play1toolkit.inspection

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.templates.inspection.CreateMissingTemplateQuickFix
import com.github.pablolec.play1toolkit.templates.references.PlayTemplateJavaContextDetector
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class Play1MissingViewInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Missing Play 1 view for render() or renderTemplate() call"
    override fun getGroupDisplayName(): String = "Play v1 Toolkit"
    override fun getShortName(): String = "Play1MissingView"
    override fun isEnabledByDefault(): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                val methodName = expression.methodExpression.referenceName ?: return
                if (methodName != "render" && methodName != "renderTemplate") return

                val containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java) ?: return
                val containingClass = containingMethod.containingClass ?: return
                if (!Play1ViewUtils.isPlayController(containingClass)) return

                if (methodName == "renderTemplate") {
                    val firstArg = expression.argumentList.expressions.firstOrNull() as? PsiLiteralExpression ?: return
                    val templatePath = firstArg.value as? String ?: return
                    if (!PlayTemplateJavaContextDetector.isRenderTemplatePathContext(firstArg)) return
                    if (PlayTemplateFileUtils.resolveTemplatePath(expression.project, templatePath) == null) {
                        holder.registerProblem(
                            firstArg,
                            "View not found: $templatePath",
                            ProblemHighlightType.WARNING,
                            CreateMissingTemplateQuickFix(templatePath)
                        )
                    }
                    return
                }

                val controllerName = containingClass.name ?: return
                val actionName = containingMethod.name
                val viewPath = Play1ViewUtils.implicitViewPath(controllerName, actionName)
                if (Play1ViewUtils.findViewFile(expression.project, controllerName, actionName) == null) {
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
