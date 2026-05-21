package com.github.pablolec.play1toolkit.playjobs.inspection

import com.github.pablolec.play1toolkit.playjobs.model.PlayJobTriggerKind
import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiWhileStatement
import com.intellij.psi.util.PsiTreeUtil

class PlayJobBlockingStartupInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val triggers = PlayJobUtils.findTriggers(aClass)
                val startup = triggers.firstOrNull { it.kind == PlayJobTriggerKind.ON_APPLICATION_START } ?: return
                if (startup.async) return
                val executionMethods = PlayJobUtils.findExecutionMethods(aClass)
                if (executionMethods.isEmpty()) return
                executionMethods.forEach { method ->
                    val body = method.psiMethod.body ?: return@forEach
                    val blocking = findBlockingSignals(body)
                    if (blocking.isNotEmpty()) {
                        val target = blocking.first()
                        holder.registerProblem(
                            target,
                            "Startup job may perform blocking work",
                            ProblemHighlightType.INFORMATION
                        )
                    }
                }
            }
        }
    }

    private fun findBlockingSignals(body: PsiElement): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java).forEach { call ->
            val method = call.methodExpression
            val name = method.referenceName ?: return@forEach
            if (name == "sleep") {
                val qualifier = method.qualifierExpression?.text
                if (qualifier == "Thread" || qualifier?.endsWith(".Thread") == true) {
                    result += call
                }
            }
            if (name == "openConnection") {
                result += call
            }
        }
        PsiTreeUtil.findChildrenOfType(body, PsiNewExpression::class.java).forEach { newExpr ->
            val canonical = newExpr.classReference?.qualifiedName ?: return@forEach
            if (canonical == "java.net.URL" || canonical == "URL" || canonical == "java.net.HttpURLConnection") {
                result += newExpr
            }
        }
        PsiTreeUtil.findChildrenOfType(body, PsiTypeElement::class.java).forEach { typeElement ->
            val text = typeElement.text
            if (text == "HttpURLConnection" || text.endsWith(".HttpURLConnection")) {
                result += typeElement
            }
        }
        PsiTreeUtil.findChildrenOfType(body, PsiWhileStatement::class.java).forEach { whileStmt ->
            val condition = whileStmt.condition
            if (condition is PsiLiteralExpression && condition.value == true) {
                result += whileStmt
            }
        }
        PsiTreeUtil.findChildrenOfType(body, PsiForStatement::class.java).forEach { forStmt ->
            if (forStmt.condition == null && forStmt.initialization == null) {
                result += forStmt
            }
        }
        return result
    }
}
