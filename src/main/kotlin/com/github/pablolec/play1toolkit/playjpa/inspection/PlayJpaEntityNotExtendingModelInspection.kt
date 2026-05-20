package com.github.pablolec.play1toolkit.playjpa.inspection

import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor

class PlayJpaEntityNotExtendingModelInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (!PlayJpaModelUtils.hasEntityAnnotation(aClass)) return
                if (PlayJpaModelUtils.extendsPlayModel(aClass)) return
                if (!PlayJpaModelUtils.isUnderAppModels(aClass)) return
                val nameIdentifier = aClass.nameIdentifier ?: return
                holder.registerProblem(nameIdentifier, "@Entity class in app/models/ should extend Model or GenericModel")
            }
        }
    }
}
