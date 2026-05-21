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

class PlayCacheKeyReadWithoutWriterInspection : AbstractBaseJavaLocalInspectionTool() {

    private val READ_METHODS = setOf("get", "getOrElse")
    private val WRITE_KINDS = setOf(
        PlayCacheUsageKind.JAVA_WRITE,
        PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT,
        PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                if (!PlayCacheArgExtractor.isCacheCall(call)) return
                if (call.methodExpression.referenceName !in READ_METHODS) return
                val keyArg = call.argumentList.expressions.firstOrNull() ?: return
                val key = PlayCacheArgExtractor.extractKey(keyArg) as? PlayCacheKey.Static ?: return
                val usages = PlayCacheService.getInstance(holder.project).getUsagesByStaticKey(key.value)
                if (usages.any { it.kind in WRITE_KINDS }) return
                holder.registerProblem(
                    keyArg,
                    "Cache key '${key.value}' is read but has no detected writer",
                    ProblemHighlightType.INFORMATION
                )
            }
        }
    }
}
