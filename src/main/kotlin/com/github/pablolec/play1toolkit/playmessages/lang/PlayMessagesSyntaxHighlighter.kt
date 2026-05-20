package com.github.pablolec.play1toolkit.playmessages.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

class PlayMessagesSyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        val COMMENT     = TextAttributesKey.createTextAttributesKey("PLAY_MSG_COMMENT",     DefaultLanguageHighlighterColors.LINE_COMMENT)
        val KEY         = TextAttributesKey.createTextAttributesKey("PLAY_MSG_KEY",         DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val SEPARATOR   = TextAttributesKey.createTextAttributesKey("PLAY_MSG_SEPARATOR",   DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val VALUE       = TextAttributesKey.createTextAttributesKey("PLAY_MSG_VALUE",       DefaultLanguageHighlighterColors.STRING)
        val PLACEHOLDER = TextAttributesKey.createTextAttributesKey("PLAY_MSG_PLACEHOLDER", DefaultLanguageHighlighterColors.PARAMETER)
        val BAD_CHAR    = TextAttributesKey.createTextAttributesKey("PLAY_MSG_BAD_CHAR",    DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
    }

    override fun getHighlightingLexer(): Lexer = PlayMessagesLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
        PlayMessagesTokenTypes.COMMENT      -> pack(COMMENT)
        PlayMessagesTokenTypes.KEY          -> pack(KEY)
        PlayMessagesTokenTypes.SEPARATOR    -> pack(SEPARATOR)
        PlayMessagesTokenTypes.VALUE        -> pack(VALUE)
        PlayMessagesTokenTypes.PLACEHOLDER  -> pack(PLACEHOLDER)
        PlayMessagesTokenTypes.BAD_CHARACTER -> pack(BAD_CHAR)
        else -> emptyArray()
    }
}

class PlayMessagesSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) =
        PlayMessagesSyntaxHighlighter()
}
