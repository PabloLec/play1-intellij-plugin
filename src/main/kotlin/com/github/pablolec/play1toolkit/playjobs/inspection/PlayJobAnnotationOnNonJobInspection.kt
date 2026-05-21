package com.github.pablolec.play1toolkit.playjobs.inspection

import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil

private val JOB_ANNOTATIONS = setOf("OnApplicationStart", "OnApplicationStop", "Every", "On")

class PlayJobAnnotationOnNonJobInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitAnnotation(annotation: PsiAnnotation) {
                val simpleName = annotation.qualifiedName?.substringAfterLast('.')
                    ?: annotation.nameReferenceElement?.referenceName
                if (simpleName !in JOB_ANNOTATIONS) return
                val ownerClass = PsiTreeUtil.getParentOfType(annotation, PsiClass::class.java) ?: return
                if (PlayJobUtils.extendsPlayJob(ownerClass)) return
                val anchor = annotation.nameReferenceElement ?: annotation
                holder.registerProblem(
                    anchor,
                    "Class annotated as Play job but does not extend play.jobs.Job"
                )
            }
        }
    }
}
