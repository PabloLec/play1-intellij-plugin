package com.github.pablolec.play1toolkit.playmessages.lang

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesFile
import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class PlayMessagesParserDefinition : ParserDefinition {

    companion object {
        @JvmField val FILE = IFileElementType(PlayMessagesLanguage)
    }

    override fun createLexer(project: Project?) = PlayMessagesLexer()
    override fun createParser(project: Project?): PsiParser = PlayMessagesParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = PlayMessagesTokenTypes.WHITESPACE_SET
    override fun getCommentTokens(): TokenSet = PlayMessagesTokenTypes.COMMENT_SET
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        PlayMessagesElementTypes.PROPERTY -> PlayMessagesProperty(node)
        else -> ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = PlayMessagesFile(viewProvider)
}

private class PlayMessagesParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()

        while (!builder.eof()) {
            when (builder.tokenType) {
                PlayMessagesTokenTypes.KEY         -> parseProperty(builder)
                PlayMessagesTokenTypes.NEWLINE,
                PlayMessagesTokenTypes.COMMENT,
                PlayMessagesTokenTypes.WHITESPACE,
                PlayMessagesTokenTypes.BAD_CHARACTER -> builder.advanceLexer()
                else -> builder.advanceLexer()
            }
        }

        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseProperty(builder: PsiBuilder) {
        val propMarker = builder.mark()

        builder.advanceLexer() // KEY

        while (builder.tokenType == PlayMessagesTokenTypes.WHITESPACE) builder.advanceLexer()

        if (builder.tokenType == PlayMessagesTokenTypes.SEPARATOR) {
            builder.advanceLexer()
        }

        while (builder.tokenType == PlayMessagesTokenTypes.WHITESPACE) builder.advanceLexer()

        // VALUE tokens interleaved with PLACEHOLDER
        while (builder.tokenType != null &&
            builder.tokenType != PlayMessagesTokenTypes.NEWLINE &&
            !builder.eof()
        ) {
            builder.advanceLexer()
        }

        propMarker.done(PlayMessagesElementTypes.PROPERTY)
    }
}
