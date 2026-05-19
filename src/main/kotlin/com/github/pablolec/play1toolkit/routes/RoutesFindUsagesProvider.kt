package com.github.pablolec.play1toolkit.routes

import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

class RoutesFindUsagesProvider : FindUsagesProvider {

    private val wordsScanner = DefaultWordsScanner(
        RoutesLexer(),
        TokenSet.create(RoutesTokenTypes.CONTROLLER_NAME, RoutesTokenTypes.ACTION_NAME),
        RoutesTokenTypes.COMMENT_SET,
        TokenSet.create(RoutesTokenTypes.PATH, RoutesTokenTypes.PATH_PARAM, RoutesTokenTypes.STATIC_REF, RoutesTokenTypes.MODULE_REF),
    )

    override fun getWordsScanner(): WordsScanner = wordsScanner

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean =
        psiElement is RoutesRouteElement || psiElement.node?.elementType in setOf(
            RoutesTokenTypes.CONTROLLER_NAME,
            RoutesTokenTypes.ACTION_NAME,
        )

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element.node?.elementType) {
        RoutesTokenTypes.CONTROLLER_NAME -> "route controller reference"
        RoutesTokenTypes.ACTION_NAME -> "route action reference"
        else -> "route"
    }

    override fun getDescriptiveName(element: PsiElement): String = element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = element.text
}
