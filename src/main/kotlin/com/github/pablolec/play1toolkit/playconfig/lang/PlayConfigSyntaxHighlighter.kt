package com.github.pablolec.play1toolkit.playconfig.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

object PlayConfigSyntaxHighlighter : SyntaxHighlighterBase() {

    val COMMENT = TextAttributesKey.createTextAttributesKey(
        "PLAY_CONFIG_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT
    )
    val KEY = TextAttributesKey.createTextAttributesKey(
        "PLAY_CONFIG_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD
    )
    val SEPARATOR = TextAttributesKey.createTextAttributesKey(
        "PLAY_CONFIG_SEPARATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN
    )
    val VALUE = TextAttributesKey.createTextAttributesKey(
        "PLAY_CONFIG_VALUE", DefaultLanguageHighlighterColors.STRING
    )
    val ENV_PLACEHOLDER = TextAttributesKey.createTextAttributesKey(
        "PLAY_CONFIG_ENV_PLACEHOLDER", DefaultLanguageHighlighterColors.PARAMETER
    )
    val BAD_CHAR = TextAttributesKey.createTextAttributesKey(
        "PLAY_CONFIG_BAD_CHAR", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
    )

    private val COMMENT_KEYS = arrayOf(COMMENT)
    private val KEY_KEYS = arrayOf(KEY)
    private val SEPARATOR_KEYS = arrayOf(SEPARATOR)
    private val VALUE_KEYS = arrayOf(VALUE)
    private val ENV_KEYS = arrayOf(ENV_PLACEHOLDER)
    private val BAD_KEYS = arrayOf(BAD_CHAR)
    private val EMPTY = emptyArray<TextAttributesKey>()

    override fun getHighlightingLexer(): Lexer = PlayConfigLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when (tokenType) {
            PlayConfigTokenTypes.COMMENT -> COMMENT_KEYS
            PlayConfigTokenTypes.KEY -> KEY_KEYS
            PlayConfigTokenTypes.SEPARATOR -> SEPARATOR_KEYS
            PlayConfigTokenTypes.VALUE -> VALUE_KEYS
            PlayConfigTokenTypes.ENV_PLACEHOLDER -> ENV_KEYS
            PlayConfigTokenTypes.BAD_CHARACTER -> BAD_KEYS
            else -> EMPTY
        }
}

class PlayConfigSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = PlayConfigSyntaxHighlighter
}
