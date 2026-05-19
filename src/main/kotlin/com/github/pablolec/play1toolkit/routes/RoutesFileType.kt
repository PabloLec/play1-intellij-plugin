package com.github.pablolec.play1toolkit.routes

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object RoutesFileType : LanguageFileType(RoutesLanguage) {
    override fun getName(): String = "Routes"
    override fun getDescription(): String = "Play 1 conf/routes file"
    override fun getDefaultExtension(): String = ""
    override fun getIcon(): Icon = AllIcons.FileTypes.Text
}
