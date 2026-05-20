package com.github.pablolec.play1toolkit.playconfig.psi

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigParserDefinition
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class PlayConfigFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, PlayConfigLanguage) {
    override fun getFileType() = com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigFileType
    override fun toString(): String = "PlayConfigFile"

    fun getProperties(): List<PlayConfigProperty> =
        children.filterIsInstance<PlayConfigProperty>()
}
