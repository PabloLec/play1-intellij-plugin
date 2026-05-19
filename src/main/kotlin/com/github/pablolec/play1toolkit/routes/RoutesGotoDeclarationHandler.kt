package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

class RoutesGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        if (element.containingFile !is RoutesFile) return null

        val project = element.project

        return when (element.node?.elementType) {
            RoutesTokenTypes.CONTROLLER_NAME -> {
                val name = element.text.trim()
                val psiClass = RoutesControllerResolver.resolveClass(project, name)
                if (psiClass != null) arrayOf(psiClass) else null
            }
            RoutesTokenTypes.ACTION_NAME -> {
                val actionName = element.text.trim()
                val routeElement = element.parent as? RoutesRouteElement ?: return null
                val controllerName = routeElement.getControllerName()?.text?.trim() ?: return null
                val method = RoutesControllerResolver.resolveMethod(project, controllerName, actionName)
                    ?: RoutesControllerResolver.resolveClass(project, controllerName)
                if (method != null) arrayOf(method) else null
            }
            else -> null
        }
    }
}
