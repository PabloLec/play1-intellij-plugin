package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.intellij.psi.ElementManipulators
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

class PlayTemplatePathReference(
    element: PsiElement,
    range: TextRange,
    private val path: String
) : PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? {
        val vf = PlayTemplateFileUtils.resolveTemplatePath(element.project, path) ?: return null
        return PsiManager.getInstance(element.project).findFile(vf)
    }

    override fun getVariants(): Array<Any> {
        val svc = PlayTemplateService.getInstance(element.project)
        return svc.getAllTemplates()
            .map { it.logicalPath }
            .toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val updatedPath = when {
            newElementName.contains('/') -> newElementName
            path.contains('/') -> path.substringBeforeLast('/') + "/" + newElementName
            else -> newElementName
        }
        return ElementManipulators.handleContentChange(element, rangeInElement, updatedPath)
    }
}
