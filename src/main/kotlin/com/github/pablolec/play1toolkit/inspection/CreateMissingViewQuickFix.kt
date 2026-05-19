package com.github.pablolec.play1toolkit.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Paths

class CreateMissingViewQuickFix(
    private val controllerName: String,
    private val actionName: String,
    private val viewPath: String
) : LocalQuickFix {

    override fun getName(): String = "Create view '$viewPath'"
    override fun getFamilyName(): String = "Create Play 1 view"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val basePath = project.basePath ?: return
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return

        val viewsDir = VfsUtil.createDirectoryIfMissing(projectRoot, "app/views/$controllerName") ?: return
        val fileName = "$actionName.html"
        if (viewsDir.findChild(fileName) != null) return

        val newFile = viewsDir.createChildData(this, fileName)
        val title = actionName.replaceFirstChar(Char::uppercaseChar)
        VfsUtil.saveText(
            newFile,
            "#{extends 'main.html' /}\n#{set title:'$title' /}\n\n<h1>$title</h1>\n"
        )

        OpenFileDescriptor(project, newFile).navigate(true)
    }
}
