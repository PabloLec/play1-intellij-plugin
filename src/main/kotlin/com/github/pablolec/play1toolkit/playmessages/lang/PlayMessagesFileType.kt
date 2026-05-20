package com.github.pablolec.play1toolkit.playmessages.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object PlayMessagesFileType : LanguageFileType(PlayMessagesLanguage) {
    override fun getName() = "PlayMessages"
    override fun getDescription() = "Play 1 i18n messages file"
    override fun getDefaultExtension() = ""
    override fun getIcon(): Icon = AllIcons.FileTypes.Properties
}
