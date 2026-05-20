package com.github.pablolec.play1toolkit.playmessages.references

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

class PlayMessagesJavaStringLiteralReference(
    element: PsiLiteralExpression,
    range: TextRange
) : PsiReferenceBase.Poly<PsiLiteralExpression>(element, range, /* soft= */ true) {

    private val key: String get() = element.value?.toString() ?: ""

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val k = key.ifBlank { return ResolveResult.EMPTY_ARRAY }
        val svc = PlayMessagesService.getInstance(element.project)
        return svc.entriesForKey(k)
            .map { PsiElementResolveResult(it.property) }
            .toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val factory = JavaPsiFacade.getElementFactory(element.project)
        return element.replace(factory.createExpressionFromText("\"$newElementName\"", element))
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element !is PlayMessagesProperty) return false
        return element.key == key
    }
}
