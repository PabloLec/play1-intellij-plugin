package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesLexer
import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesTokenTypes
import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

class PlayMessagesFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner() = DefaultWordsScanner(
        PlayMessagesLexer(),
        TokenSet.create(PlayMessagesTokenTypes.KEY),
        PlayMessagesTokenTypes.COMMENT_SET,
        TokenSet.create(PlayMessagesTokenTypes.VALUE, PlayMessagesTokenTypes.PLACEHOLDER)
    )

    override fun canFindUsagesFor(element: PsiElement) = element is PlayMessagesProperty

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement) = when (element) {
        is PlayMessagesProperty -> if (element.locale != null) "message (${element.locale})" else "message (default)"
        else -> "message key"
    }

    override fun getDescriptiveName(element: PsiElement) = when (element) {
        is PlayMessagesProperty -> element.key
        else -> element.text
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean) = when (element) {
        is PlayMessagesProperty -> "${element.key} = ${element.valueText}"
        else -> element.text
    }
}
