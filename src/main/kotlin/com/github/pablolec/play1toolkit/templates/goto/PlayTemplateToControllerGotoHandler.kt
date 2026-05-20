package com.github.pablolec.play1toolkit.templates.goto

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement

class PlayTemplateToControllerGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        if (DumbService.isDumb(element.project)) return null
        val file = element.containingFile?.virtualFile ?: return null
        if (!PlayTemplateFileUtils.isInViewsDirectory(file)) return null
        val methods = PlayTemplateService.getInstance(element.project).findLikelyRenderingMethods(file)
        if (methods.isEmpty()) return null
        return methods.toTypedArray()
    }

    override fun getActionText(context: DataContext): String = "Go to Play v1 Rendering Action"
}
