package com.github.pablolec.play1toolkit.playconfig.lang

import com.intellij.psi.tree.IElementType

class PlayConfigElementType(debugName: String) : IElementType(debugName, PlayConfigLanguage) {
    override fun toString(): String = "PlayConfigElementType.$debugName"
}

object PlayConfigElementTypes {
    @JvmField val PROPERTY = PlayConfigElementType("PROPERTY")
}
