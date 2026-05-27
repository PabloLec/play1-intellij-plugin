package com.github.pablolec.play1toolkit.playconfig.lang

import com.intellij.psi.tree.IElementType

class PlayConfigElementType(private val elementName: String) : IElementType(elementName, PlayConfigLanguage) {
    override fun toString(): String = "PlayConfigElementType.$elementName"
}

object PlayConfigElementTypes {
    @JvmField val PROPERTY = PlayConfigElementType("PROPERTY")
}
