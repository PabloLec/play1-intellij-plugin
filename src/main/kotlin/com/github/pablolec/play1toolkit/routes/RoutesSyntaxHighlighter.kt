package com.github.pablolec.play1toolkit.routes

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

object RoutesSyntaxHighlighter : SyntaxHighlighterBase() {

    val HTTP_METHOD = TextAttributesKey.createTextAttributesKey(
        "ROUTES_HTTP_METHOD", DefaultLanguageHighlighterColors.KEYWORD
    )
    val PATH = TextAttributesKey.createTextAttributesKey(
        "ROUTES_PATH", DefaultLanguageHighlighterColors.STRING
    )
    val PATH_PARAM = TextAttributesKey.createTextAttributesKey(
        "ROUTES_PATH_PARAM", DefaultLanguageHighlighterColors.PARAMETER
    )
    val CONTROLLER_NAME = TextAttributesKey.createTextAttributesKey(
        "ROUTES_CONTROLLER_NAME", DefaultLanguageHighlighterColors.CLASS_REFERENCE
    )
    val DOT = TextAttributesKey.createTextAttributesKey(
        "ROUTES_DOT", DefaultLanguageHighlighterColors.DOT
    )
    val ACTION_NAME = TextAttributesKey.createTextAttributesKey(
        "ROUTES_ACTION_NAME", DefaultLanguageHighlighterColors.FUNCTION_CALL
    )
    val STATIC_REF = TextAttributesKey.createTextAttributesKey(
        "ROUTES_STATIC_REF", DefaultLanguageHighlighterColors.LINE_COMMENT
    )
    val MODULE_REF = TextAttributesKey.createTextAttributesKey(
        "ROUTES_MODULE_REF", DefaultLanguageHighlighterColors.LINE_COMMENT
    )
    val COMMENT = TextAttributesKey.createTextAttributesKey(
        "ROUTES_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT
    )
    val BAD_CHAR = TextAttributesKey.createTextAttributesKey(
        "ROUTES_BAD_CHAR", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
    )

    private val HTTP_METHOD_KEYS = arrayOf(HTTP_METHOD)
    private val PATH_KEYS = arrayOf(PATH)
    private val PATH_PARAM_KEYS = arrayOf(PATH_PARAM)
    private val CONTROLLER_KEYS = arrayOf(CONTROLLER_NAME)
    private val DOT_KEYS = arrayOf(DOT)
    private val ACTION_KEYS = arrayOf(ACTION_NAME)
    private val STATIC_KEYS = arrayOf(STATIC_REF)
    private val MODULE_KEYS = arrayOf(MODULE_REF)
    private val COMMENT_KEYS = arrayOf(COMMENT)
    private val BAD_CHAR_KEYS = arrayOf(BAD_CHAR)
    private val EMPTY_KEYS = emptyArray<TextAttributesKey>()

    override fun getHighlightingLexer(): Lexer = RoutesLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when (tokenType) {
            RoutesTokenTypes.HTTP_METHOD -> HTTP_METHOD_KEYS
            RoutesTokenTypes.PATH -> PATH_KEYS
            RoutesTokenTypes.PATH_PARAM -> PATH_PARAM_KEYS
            RoutesTokenTypes.CONTROLLER_NAME -> CONTROLLER_KEYS
            RoutesTokenTypes.DOT -> DOT_KEYS
            RoutesTokenTypes.ACTION_NAME -> ACTION_KEYS
            RoutesTokenTypes.STATIC_REF -> STATIC_KEYS
            RoutesTokenTypes.MODULE_REF -> MODULE_KEYS
            RoutesTokenTypes.COMMENT -> COMMENT_KEYS
            RoutesTokenTypes.BAD_CHARACTER -> BAD_CHAR_KEYS
            else -> EMPTY_KEYS
        }
}
