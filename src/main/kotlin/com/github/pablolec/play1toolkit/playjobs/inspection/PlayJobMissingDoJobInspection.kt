package com.github.pablolec.play1toolkit.playjobs.inspection

import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor

class PlayJobMissingDoJobInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (!PlayJobUtils.extendsPlayJob(aClass)) return
                if (PlayJobUtils.findExecutionMethods(aClass).isNotEmpty()) return
                val nameIdentifier = aClass.nameIdentifier ?: return
                holder.registerProblem(
                    nameIdentifier,
                    "Play Job does not declare doJob() or doJobWithResult()"
                )
            }
        }
    }
}
