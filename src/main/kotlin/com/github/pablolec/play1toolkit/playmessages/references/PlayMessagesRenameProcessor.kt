package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.psi.search.SearchScope

class PlayMessagesRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement) = element is PlayMessagesProperty

    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope
    ) {
        val prop = element as? PlayMessagesProperty ?: return
        val svc = PlayMessagesService.getInstance(prop.project)
        // Rename across all locales automatically
        svc.entriesForKey(prop.key).forEach { entry ->
            if (entry.property !== prop) {
                allRenames[entry.property] = newName
            }
        }
    }
}
