package com.github.pablolec.play1toolkit.playconfig.references

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLexer
import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigTokenTypes
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

class PlayConfigFindUsagesProvider : FindUsagesProvider {

    private val wordsScanner = DefaultWordsScanner(
        PlayConfigLexer(),
        TokenSet.create(PlayConfigTokenTypes.KEY),
        PlayConfigTokenTypes.COMMENT_SET,
        TokenSet.create(PlayConfigTokenTypes.VALUE, PlayConfigTokenTypes.ENV_PLACEHOLDER)
    )

    override fun getWordsScanner(): WordsScanner = wordsScanner

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean =
        psiElement is PlayConfigProperty

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is PlayConfigProperty -> if (element.profile != null) "profile configuration property" else "configuration property"
        else -> "configuration key"
    }

    override fun getDescriptiveName(element: PsiElement): String = when (element) {
        is PlayConfigProperty -> element.logicalKey
        else -> element.text
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = when (element) {
        is PlayConfigProperty -> "${element.rawKey} = ${element.valueText}"
        else -> element.text
    }
}
