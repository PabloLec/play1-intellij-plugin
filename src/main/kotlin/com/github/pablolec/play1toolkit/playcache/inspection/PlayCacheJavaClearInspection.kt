package com.github.pablolec.play1toolkit.playcache.inspection

import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression

class PlayCacheJavaClearInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                if (!PlayCacheArgExtractor.isCacheCall(call)) return
                if (call.methodExpression.referenceName != "clear") return
                val anchor = call.methodExpression.referenceNameElement ?: call
                holder.registerProblem(
                    anchor,
                    "Global cache clear detected",
                    ProblemHighlightType.INFORMATION
                )
            }
        }
    }
}
