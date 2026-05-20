package com.github.pablolec.play1toolkit.playmessages.psi

import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesFileType
import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class PlayMessagesFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, PlayMessagesLanguage) {
    override fun getFileType() = PlayMessagesFileType
    override fun toString() = "PlayMessagesFile"

    fun getProperties(): List<PlayMessagesProperty> = children.filterIsInstance<PlayMessagesProperty>()

    /** Locale derived from filename: "messages" → null, "messages.fr" → "fr", "messages.en-US" → "en-US" */
    val locale: String? get() {
        val name = virtualFile?.name ?: return null
        val dot = name.indexOf('.')
        return if (dot < 0) null else name.substring(dot + 1)
    }
}
