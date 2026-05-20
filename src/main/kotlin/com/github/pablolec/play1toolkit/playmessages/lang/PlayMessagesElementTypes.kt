package com.github.pablolec.play1toolkit.playmessages.lang

import com.intellij.psi.tree.IElementType

class PlayMessagesElementType(debugName: String) : IElementType(debugName, PlayMessagesLanguage)

object PlayMessagesElementTypes {
    @JvmField val PROPERTY = PlayMessagesElementType("PROPERTY")
}
