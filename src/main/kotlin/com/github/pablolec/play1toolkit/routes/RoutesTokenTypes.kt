package com.github.pablolec.play1toolkit.routes

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class RoutesTokenType(private val typeName: String) : IElementType(typeName, RoutesLanguage) {
    override fun toString(): String = "RoutesTokenType.$typeName"
}

object RoutesTokenTypes {
    @JvmField val HTTP_METHOD = RoutesTokenType("HTTP_METHOD")
    @JvmField val WHITESPACE = RoutesTokenType("WHITESPACE")
    @JvmField val PATH = RoutesTokenType("PATH")
    @JvmField val PATH_PARAM = RoutesTokenType("PATH_PARAM")
    @JvmField val CONTROLLER_NAME = RoutesTokenType("CONTROLLER_NAME")
    @JvmField val DOT = RoutesTokenType("DOT")
    @JvmField val ACTION_NAME = RoutesTokenType("ACTION_NAME")
    @JvmField val STATIC_REF = RoutesTokenType("STATIC_REF")
    @JvmField val MODULE_REF = RoutesTokenType("MODULE_REF")
    @JvmField val COMMENT = RoutesTokenType("COMMENT")
    @JvmField val NEWLINE = RoutesTokenType("NEWLINE")
    @JvmField val BAD_CHARACTER = RoutesTokenType("BAD_CHARACTER")

    @JvmField val WHITESPACE_SET = TokenSet.create(WHITESPACE, NEWLINE)
    @JvmField val COMMENT_SET = TokenSet.create(COMMENT)
}
