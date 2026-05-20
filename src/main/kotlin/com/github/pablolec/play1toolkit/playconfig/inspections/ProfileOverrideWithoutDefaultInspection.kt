package com.github.pablolec.play1toolkit.playconfig.inspections

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.codeInspection.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*

class ProfileOverrideWithoutDefaultInspection : LocalInspectionTool() {
    override fun getDisplayName() = "Profile override without default value"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "ProfileOverrideWithoutDefault"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (holder.file.language != PlayConfigLanguage) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val prop = element as? PlayConfigProperty ?: return
                if (prop.profile == null) return

                val svc = PlayConfigService.getInstance(element.project)
                val hasDefault = svc.keysForLogical(prop.logicalKey).any { it.profile == null }

                if (!hasDefault) {
                    holder.registerProblem(
                        prop.nameIdentifier ?: prop,
                        "Profile override '%${prop.profile}.${prop.logicalKey}' has no default value",
                        ProblemHighlightType.INFORMATION
                    )
                }
            }
        }
    }
}
