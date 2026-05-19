package com.github.pablolec.play1toolkit.lineMarker

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*

class JavaToRoutesGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        val identifier = sourceElement as? PsiIdentifier ?: return null
        val project = identifier.project

        return when (val parent = identifier.parent) {
            is PsiClass -> {
                if (!Play1ViewUtils.isPlayControllerClass(parent)) return null
                val name = parent.name ?: return null
                val routes = Play1ViewUtils.findAllRoutesForController(project, name)
                if (routes.isEmpty()) null else routes.toTypedArray()
            }
            is PsiMethod -> {
                if (!parent.hasModifierProperty(PsiModifier.PUBLIC)) return null
                if (!parent.hasModifierProperty(PsiModifier.STATIC)) return null
                val containingClass = parent.containingClass ?: return null
                if (!Play1ViewUtils.isPlayControllerClass(containingClass)) return null
                val controllerName = containingClass.name ?: return null
                val routes = Play1ViewUtils.findRoutesForAction(project, controllerName, parent.name)
                if (routes.isEmpty()) null else routes.toTypedArray()
            }
            else -> null
        }
    }
}
