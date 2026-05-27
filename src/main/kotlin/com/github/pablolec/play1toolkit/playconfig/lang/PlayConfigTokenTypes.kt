package com.github.pablolec.play1toolkit.playconfig.lang

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class PlayConfigTokenType(private val tokenName: String) : IElementType(tokenName, PlayConfigLanguage) {
    override fun toString(): String = "PlayConfigTokenType.$tokenName"
}

object PlayConfigTokenTypes {
    @JvmField val COMMENT = PlayConfigTokenType("COMMENT")
    @JvmField val KEY = PlayConfigTokenType("KEY")
    @JvmField val SEPARATOR = PlayConfigTokenType("SEPARATOR")
    @JvmField val VALUE = PlayConfigTokenType("VALUE")
    @JvmField val ENV_PLACEHOLDER = PlayConfigTokenType("ENV_PLACEHOLDER")
    @JvmField val NEWLINE = PlayConfigTokenType("NEWLINE")
    @JvmField val WHITESPACE = PlayConfigTokenType("WHITESPACE")
    @JvmField val BAD_CHARACTER = PlayConfigTokenType("BAD_CHARACTER")

    @JvmField val WHITESPACE_SET = TokenSet.create(WHITESPACE)
    @JvmField val COMMENT_SET = TokenSet.create(COMMENT)
}
