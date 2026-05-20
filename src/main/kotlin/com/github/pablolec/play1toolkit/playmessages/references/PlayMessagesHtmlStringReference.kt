package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.xml.XmlText

class PlayMessagesHtmlStringReference(
    element: XmlText,
    private val key: String,
    range: TextRange
) : PsiReferenceBase.Poly<XmlText>(element, range, /* soft= */ true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val svc = PlayMessagesService.getInstance(element.project)
        return svc.entriesForKey(key)
            .map { PsiElementResolveResult(it.property) }
            .toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val doc = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile)
            ?: return element
        val absStart = element.textRange.startOffset + rangeInElement.startOffset
        val absEnd = element.textRange.startOffset + rangeInElement.endOffset
        WriteCommandAction.runWriteCommandAction(element.project) {
            doc.replaceString(absStart, absEnd, newElementName)
        }
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element !is PlayMessagesProperty) return false
        return element.key == key
    }
}
