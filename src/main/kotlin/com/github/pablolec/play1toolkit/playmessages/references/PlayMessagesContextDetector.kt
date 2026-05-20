package com.github.pablolec.play1toolkit.playmessages.references

import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression

/**
 * Detects whether a PsiLiteralExpression is used as a Play i18n message key.
 * Matches: Messages.get("key"), Messages.get("key", args...), play.i18n.Messages.get(...)
 */
object PlayMessagesContextDetector {

    private val METHOD_NAME = "get"

    fun isMessagesKeyContext(literal: PsiLiteralExpression): Boolean {
        val arg = literal.parent as? PsiExpressionList ?: return false
        val call = arg.parent as? PsiMethodCallExpression ?: return false
        if (call.methodExpression.referenceName != METHOD_NAME) return false
        // Must be the first argument (arg index 0 = the key)
        if (arg.expressions.indexOf(literal) != 0) return false
        val qualText = call.methodExpression.qualifierExpression?.text ?: return false
        return qualText.endsWith("Messages")
    }

    /**
     * Returns the number of format arguments passed (args after the key).
     * Messages.get("key") → 0, Messages.get("key", a, b) → 2
     */
    fun getFormatArgCount(literal: PsiLiteralExpression): Int {
        val arg = literal.parent as? PsiExpressionList ?: return 0
        return maxOf(0, arg.expressions.size - 1)
    }
}
