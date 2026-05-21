package com.github.pablolec.play1toolkit.playcache.inspection

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsageKind
import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression

class PlayCacheKeyWrittenWithoutInvalidatorInspection : AbstractBaseJavaLocalInspectionTool() {

    private val WRITE_METHODS = setOf("set", "add", "safeAdd", "replace", "safeReplace")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                if (!PlayCacheArgExtractor.isCacheCall(call)) return
                if (call.methodExpression.referenceName !in WRITE_METHODS) return
                val keyArg = call.argumentList.expressions.firstOrNull() ?: return
                val key = PlayCacheArgExtractor.extractKey(keyArg) as? PlayCacheKey.Static ?: return
                val service = PlayCacheService.getInstance(holder.project)
                val usages = service.getUsagesByStaticKey(key.value)
                val invalidations = usages.any { it.kind == PlayCacheUsageKind.JAVA_INVALIDATION }
                val clears = service.getGlobalClears().isNotEmpty()
                if (invalidations || clears) return
                holder.registerProblem(
                    keyArg,
                    "Cache key '${key.value}' is written but never invalidated",
                    ProblemHighlightType.INFORMATION
                )
            }
        }
    }
}
