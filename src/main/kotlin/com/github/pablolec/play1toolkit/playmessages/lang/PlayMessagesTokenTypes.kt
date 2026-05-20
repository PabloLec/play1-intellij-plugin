package com.github.pablolec.play1toolkit.playmessages.lang

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class PlayMessagesTokenType(debugName: String) : IElementType(debugName, PlayMessagesLanguage)

object PlayMessagesTokenTypes {
    @JvmField val COMMENT      = PlayMessagesTokenType("COMMENT")
    @JvmField val KEY          = PlayMessagesTokenType("KEY")
    @JvmField val SEPARATOR    = PlayMessagesTokenType("SEPARATOR")
    @JvmField val VALUE        = PlayMessagesTokenType("VALUE")
    @JvmField val PLACEHOLDER  = PlayMessagesTokenType("PLACEHOLDER")
    @JvmField val NEWLINE      = PlayMessagesTokenType("NEWLINE")
    @JvmField val WHITESPACE   = PlayMessagesTokenType("WHITESPACE")
    @JvmField val BAD_CHARACTER = PlayMessagesTokenType("BAD_CHARACTER")

    @JvmField val WHITESPACE_SET = TokenSet.create(WHITESPACE)
    @JvmField val COMMENT_SET    = TokenSet.create(COMMENT)
}
