package com.github.pablolec.play1toolkit.playjobs.inspection

import com.github.pablolec.play1toolkit.playjobs.service.PlayJobService
import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor

class PlayJobUnreferencedInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (DumbService.isDumb(aClass.project)) return
                if (!PlayJobUtils.isUnderAppJobs(aClass)) return
                val service = PlayJobService.getInstance(aClass.project)
                val info = service.findJobForClass(aClass) ?: return
                if (info.triggers.isNotEmpty()) return
                val invocations = runCatching { service.findInvocations(info) }.getOrDefault(emptyList())
                if (invocations.isNotEmpty()) return
                val nameIdentifier = aClass.nameIdentifier ?: return
                holder.registerProblem(
                    nameIdentifier,
                    "Play Job has no detected trigger or manual invocation",
                    ProblemHighlightType.INFORMATION
                )
            }
        }
    }
}
