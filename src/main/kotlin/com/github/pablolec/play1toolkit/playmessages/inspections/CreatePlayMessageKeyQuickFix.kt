package com.github.pablolec.play1toolkit.playmessages.inspections

import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

class CreatePlayMessageKeyQuickFix(private val key: String) : LocalQuickFix {
    override fun getName() = "Create message key '$key' in conf/messages"
    override fun getFamilyName() = "Play v1 Toolkit"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val svc = PlayMessagesService.getInstance(project)
        val defaultFile = svc.getMessagesFiles().firstOrNull { it.locale == null } ?: return
        val vf = defaultFile.virtualFile ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return@runWriteCommandAction
            val text = doc.text
            val suffix = if (text.endsWith("\n") || text.isEmpty()) "" else "\n"
            doc.insertString(doc.textLength, "$suffix$key=\n")
        }
    }
}
