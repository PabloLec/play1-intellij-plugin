package com.github.pablolec.play1toolkit.playconfig.lang

import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigFile
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
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

class PlayConfigParserDefinition : ParserDefinition {

    companion object {
        @JvmField val FILE = IFileElementType(PlayConfigLanguage)
    }

    override fun createLexer(project: Project?) = PlayConfigLexer()
    override fun createParser(project: Project?): PsiParser = PlayConfigParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = PlayConfigTokenTypes.WHITESPACE_SET
    override fun getCommentTokens(): TokenSet = PlayConfigTokenTypes.COMMENT_SET
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        PlayConfigElementTypes.PROPERTY -> PlayConfigProperty(node)
        else -> ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = PlayConfigFile(viewProvider)
}

private class PlayConfigParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()

        while (!builder.eof()) {
            when (builder.tokenType) {
                PlayConfigTokenTypes.KEY -> parseProperty(builder)
                PlayConfigTokenTypes.NEWLINE,
                PlayConfigTokenTypes.COMMENT,
                PlayConfigTokenTypes.WHITESPACE -> builder.advanceLexer()
                PlayConfigTokenTypes.BAD_CHARACTER -> builder.advanceLexer()
                else -> builder.advanceLexer()
            }
        }

        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseProperty(builder: PsiBuilder) {
        val propMarker = builder.mark()

        // KEY
        builder.advanceLexer()

        // optional whitespace around =
        while (builder.tokenType == PlayConfigTokenTypes.WHITESPACE) builder.advanceLexer()

        // SEPARATOR (=)
        if (builder.tokenType == PlayConfigTokenTypes.SEPARATOR) {
            builder.advanceLexer()
        }

        // optional whitespace after =
        while (builder.tokenType == PlayConfigTokenTypes.WHITESPACE) builder.advanceLexer()

        // VALUE tokens (may be interspersed with ENV_PLACEHOLDER)
        while (builder.tokenType != null &&
            builder.tokenType != PlayConfigTokenTypes.NEWLINE &&
            !builder.eof()
        ) {
            builder.advanceLexer()
        }

        propMarker.done(PlayConfigElementTypes.PROPERTY)
    }
}
