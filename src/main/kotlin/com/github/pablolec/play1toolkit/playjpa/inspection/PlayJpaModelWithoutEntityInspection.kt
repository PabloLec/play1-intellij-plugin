package com.github.pablolec.play1toolkit.playjpa.inspection

import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor

class PlayJpaModelWithoutEntityInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (!PlayJpaModelUtils.extendsPlayModel(aClass)) return
                if (PlayJpaModelUtils.hasEntityAnnotation(aClass)) return
                val nameIdentifier = aClass.nameIdentifier ?: return
                holder.registerProblem(nameIdentifier, "Play JPA model class should be annotated with @Entity")
            }
        }
    }
}
