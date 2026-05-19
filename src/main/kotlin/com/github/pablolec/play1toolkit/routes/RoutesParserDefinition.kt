package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
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
import com.intellij.extapi.psi.ASTWrapperPsiElement

class RoutesParserDefinition : ParserDefinition {

    companion object {
        @JvmField val FILE = IFileElementType(RoutesLanguage)
    }

    override fun createLexer(project: Project?) = RoutesLexer()
    override fun createParser(project: Project?): PsiParser = RoutesParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = RoutesTokenTypes.WHITESPACE_SET
    override fun getCommentTokens(): TokenSet = RoutesTokenTypes.COMMENT_SET
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        RoutesElementTypes.ROUTE -> RoutesRouteElement(node)
        else -> ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = RoutesFile(viewProvider)
}

/**
 * Minimal recursive-descent parser.
 * Builds ROUTE composite nodes grouping HTTP_METHOD + PATH* + action tokens.
 * Whitespace and newlines are auto-skipped by PsiBuilder (declared in getWhitespaceTokens).
 */
private class RoutesParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()

        while (!builder.eof()) {
            when (builder.tokenType) {
                RoutesTokenTypes.HTTP_METHOD -> parseRoute(builder)
                else -> builder.advanceLexer()
            }
        }

        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseRoute(builder: PsiBuilder) {
        val routeMarker = builder.mark()

        // HTTP_METHOD
        builder.advanceLexer()

        // PATH and PATH_PARAM tokens (PsiBuilder skips whitespace automatically)
        while (builder.tokenType == RoutesTokenTypes.PATH ||
            builder.tokenType == RoutesTokenTypes.PATH_PARAM
        ) {
            builder.advanceLexer()
        }

        // Action: CONTROLLER_NAME.ACTION_NAME | staticDir:… | module:…
        when (builder.tokenType) {
            RoutesTokenTypes.CONTROLLER_NAME -> {
                builder.advanceLexer()
                if (builder.tokenType == RoutesTokenTypes.DOT) {
                    builder.advanceLexer()
                    if (builder.tokenType == RoutesTokenTypes.ACTION_NAME) {
                        builder.advanceLexer()
                    }
                }
            }
            RoutesTokenTypes.STATIC_REF -> builder.advanceLexer()
            RoutesTokenTypes.MODULE_REF -> builder.advanceLexer()
            else -> {}
        }

        routeMarker.done(RoutesElementTypes.ROUTE)
    }
}
