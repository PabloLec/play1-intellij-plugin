package com.github.pablolec.play1toolkit.playcache.inspection

import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression

class PlayCacheJavaSetWithoutExpirationInspection : AbstractBaseJavaLocalInspectionTool() {

    private val WRITE_METHODS = setOf("set", "add", "safeAdd", "replace", "safeReplace")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                if (!PlayCacheArgExtractor.isCacheCall(call)) return
                val name = call.methodExpression.referenceName ?: return
                if (name !in WRITE_METHODS) return
                val arguments = call.argumentList.expressions
                if (arguments.size >= 3) return
                val anchor = call.methodExpression.referenceNameElement ?: call
                holder.registerProblem(
                    anchor,
                    "Cache write has no explicit expiration",
                    ProblemHighlightType.INFORMATION
                )
            }
        }
    }
}
