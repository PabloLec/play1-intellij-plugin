package com.github.pablolec.play1toolkit.lineMarker

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier

/**
 * Suppresses the "unused" gray-out on public static action methods in Play 1 controllers.
 * IntelliJ's UnusedDeclarationInspection doesn't count custom-language references as real usages,
 * so without this, every routed action would appear grayed out even with routes pointing to it.
 */
class Play1ControllerImplicitUsageProvider : ImplicitUsageProvider {

    override fun isImplicitUsage(element: PsiElement): Boolean {
        if (element !is PsiMethod) return false
        if (!element.hasModifierProperty(PsiModifier.PUBLIC)) return false
        if (!element.hasModifierProperty(PsiModifier.STATIC)) return false
        val containingClass = element.containingClass ?: return false
        return Play1ViewUtils.isPlayControllerClass(containingClass)
    }

    override fun isImplicitRead(element: PsiElement) = false
    override fun isImplicitWrite(element: PsiElement) = false
}
