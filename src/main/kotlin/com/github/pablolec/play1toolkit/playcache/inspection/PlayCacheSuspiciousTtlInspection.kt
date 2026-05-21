package com.github.pablolec.play1toolkit.playcache.inspection

import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression

class PlayCacheSuspiciousTtlInspection : AbstractBaseJavaLocalInspectionTool() {

    private val WRITE_METHODS = setOf("set", "add", "safeAdd", "replace", "safeReplace", "getOrElse")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitAnnotation(annotation: PsiAnnotation) {
                val qn = annotation.qualifiedName
                val simple = annotation.nameReferenceElement?.referenceName
                if (qn != "play.cache.CacheFor" && simple != "CacheFor") return
                val literal = annotation.findAttributeValue("value") as? PsiLiteralExpression ?: return
                val value = literal.value as? String ?: return
                if (value.isNotEmpty()) return
                holder.registerProblem(
                    literal,
                    "Suspicious cache TTL (empty value)",
                    ProblemHighlightType.WEAK_WARNING
                )
            }

            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                if (!PlayCacheArgExtractor.isCacheCall(call)) return
                val name = call.methodExpression.referenceName ?: return
                if (name !in WRITE_METHODS) return
                val arguments = call.argumentList.expressions
                if (arguments.size < 3) return
                val ttlArg = arguments[2] as? PsiLiteralExpression ?: return
                val value = ttlArg.value as? String ?: return
                if (value.isNotEmpty()) return
                holder.registerProblem(
                    ttlArg,
                    "Suspicious cache TTL (empty value)",
                    ProblemHighlightType.WEAK_WARNING
                )
            }
        }
    }
}
