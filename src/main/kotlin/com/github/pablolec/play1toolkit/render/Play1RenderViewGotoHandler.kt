package com.github.pablolec.play1toolkit.render

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.services.Play1ProjectPaths
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class Play1RenderViewGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        // sourceElement is a PsiIdentifier leaf; its parent is the method reference expression
        val refExpr = sourceElement.parent as? PsiReferenceExpression ?: return null
        val methodCall = refExpr.parent as? PsiMethodCallExpression ?: return null
        val methodName = methodCall.methodExpression.referenceName ?: return null

        if (methodName != "render" && methodName != "renderTemplate") return null

        val containingMethod = PsiTreeUtil.getParentOfType(sourceElement, PsiMethod::class.java) ?: return null
        val containingClass = containingMethod.containingClass ?: return null
        if (!Play1ViewUtils.isPlayController(containingClass)) return null

        val project = sourceElement.project
        val viewVf = when (methodName) {
            "renderTemplate" -> {
                val firstArg = methodCall.argumentList.expressions.firstOrNull()
                val templatePath = (firstArg as? PsiLiteralExpression)?.value as? String
                if (templatePath != null) {
                    PlayTemplateFileUtils.resolveTemplatePath(project, templatePath)
                        ?: run {
                            val basePath = Play1ProjectPaths.applicationPath(project) ?: return null
                            VirtualFileManager.getInstance()
                                .findFileByNioPath(java.nio.file.Paths.get(basePath, "app", "views", templatePath))
                        }
                } else {
                    Play1ViewUtils.findViewFile(project, containingClass.name ?: return null, containingMethod.name)
                }
            }
            else -> Play1ViewUtils.findViewFile(project, containingClass.name ?: return null, containingMethod.name)
        } ?: return null

        val psiFile = PsiManager.getInstance(project).findFile(viewVf) ?: return null
        return arrayOf(psiFile)
    }

    override fun getActionText(context: DataContext): String = "Go to Play 1 View"
}
