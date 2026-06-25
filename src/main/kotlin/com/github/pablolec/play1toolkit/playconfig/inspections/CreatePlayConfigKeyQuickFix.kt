package com.github.pablolec.play1toolkit.playconfig.inspections

import com.github.pablolec.play1toolkit.services.Play1ProjectPaths
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigFile
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

class CreatePlayConfigKeyQuickFix(private val key: String) : LocalQuickFix {

    override fun getName() = "Create property '$key' in application.conf"
    override fun getFamilyName() = "Play v1 Toolkit"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val baseDir = project.basePathAsVirtualFile() ?: return
        val confVf = baseDir.findFileByRelativePath("conf/application.conf") ?: return
        val psiFile = PsiManager.getInstance(project).findFile(confVf) as? PlayConfigFile ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                .getDocument(confVf) ?: return@runWriteCommandAction
            val text = doc.text
            val newLine = "\n$key=\n"
            doc.insertString(text.length, newLine)
        }
    }

    private fun Project.basePathAsVirtualFile() =
        Play1ProjectPaths.applicationPath(this)
            ?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
}
