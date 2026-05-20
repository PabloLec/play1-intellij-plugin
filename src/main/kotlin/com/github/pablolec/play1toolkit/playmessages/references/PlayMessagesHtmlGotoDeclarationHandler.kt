package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement

class PlayMessagesHtmlGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        sourceElement ?: return null
        if (DumbService.isDumb(sourceElement.project)) return null

        val file = sourceElement.containingFile ?: return null
        val path = file.virtualFile?.path ?: return null
        if (!path.contains("/app/views/")) return null

        val fileText = file.text ?: return null

        for (match in PlayMessagesHtmlReferenceContributor.MESSAGES_PATTERN.findAll(fileText)) {
            if (offset < match.range.first || offset > match.range.last + 1) continue
            val key = match.groupValues[1]
            if (key.isBlank()) continue
            val svc = PlayMessagesService.getInstance(sourceElement.project)
            val entries = svc.entriesForKey(key)
            if (entries.isEmpty()) return null
            return entries.map { it.property }.toTypedArray()
        }
        return null
    }
}
