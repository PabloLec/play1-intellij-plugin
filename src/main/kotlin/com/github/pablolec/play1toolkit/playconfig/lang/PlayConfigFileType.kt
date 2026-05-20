package com.github.pablolec.play1toolkit.playconfig.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object PlayConfigFileType : LanguageFileType(PlayConfigLanguage) {
    override fun getName(): String = "PlayConfig"
    override fun getDescription(): String = "Play 1 application.conf"
    override fun getDefaultExtension(): String = "conf"
    override fun getIcon(): Icon = AllIcons.FileTypes.Properties
}
