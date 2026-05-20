package com.github.pablolec.play1toolkit.templates.references

import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression

object PlayTemplateJavaContextDetector {

    fun isRenderTemplatePathContext(literal: PsiLiteralExpression): Boolean =
        renderTemplateCall(literal) != null

    fun renderTemplateCall(literal: PsiLiteralExpression): PsiMethodCallExpression? {
        val argumentList = literal.parent as? com.intellij.psi.PsiExpressionList ?: return null
        val call = argumentList.parent as? PsiMethodCallExpression ?: return null
        val methodExpr = call.methodExpression as? PsiReferenceExpression ?: return null
        if (methodExpr.referenceName != "renderTemplate") return null
        if (argumentList.expressions.firstOrNull() !== literal) return null
        return call
    }
}
