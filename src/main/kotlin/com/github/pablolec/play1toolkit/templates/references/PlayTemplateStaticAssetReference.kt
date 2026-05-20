package com.github.pablolec.play1toolkit.templates.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.*

class PlayTemplateStaticAssetReference(
    element: PsiElement,
    range: TextRange,
    private val publicPath: String
) : PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? {
        val vf = com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils.resolvePublicAsset(element.project, publicPath)
            ?: return null
        return PsiManager.getInstance(element.project).findFile(vf)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        val updatedPath = when {
            publicPath.contains('/') -> publicPath.substringBeforeLast('/') + "/" + newElementName
            else -> newElementName
        }
        return ElementManipulators.handleContentChange(element, rangeInElement, updatedPath)
    }
}
