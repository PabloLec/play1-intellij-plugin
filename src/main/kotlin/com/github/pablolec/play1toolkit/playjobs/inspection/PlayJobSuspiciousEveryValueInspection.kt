package com.github.pablolec.play1toolkit.playjobs.inspection

import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression

class PlayJobSuspiciousEveryValueInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitAnnotation(annotation: PsiAnnotation) {
                val simpleName = annotation.qualifiedName?.substringAfterLast('.')
                    ?: annotation.nameReferenceElement?.referenceName
                if (simpleName != "Every") return
                val attrValue = annotation.findAttributeValue("value") as? PsiLiteralExpression ?: return
                val raw = attrValue.value as? String ?: return
                if (PlayJobUtils.parseEveryValueIsValid(raw)) return
                holder.registerProblem(
                    attrValue,
                    "Suspicious Play job schedule value"
                )
            }
        }
    }
}
