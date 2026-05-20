package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.intellij.psi.ElementManipulators
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

class PlayTemplateTagFileReference(
    element: PsiElement,
    range: TextRange,
    private val tagName: String
) : PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val svc = PlayTemplateService.getInstance(project)
        val tagInfo = svc.findTag(tagName)
            ?: svc.getAllCustomTags().firstOrNull { it.name == tagName }
            ?: return null
        return PsiManager.getInstance(project).findFile(tagInfo.virtualFile)
    }

    override fun getVariants(): Array<Any> {
        val svc = PlayTemplateService.getInstance(element.project)
        return svc.getAllCustomTags().map { tag ->
            LookupElementBuilder.create(tag.qualifiedName)
                .withTypeText(tag.logicalPath)
        }.toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement =
        ElementManipulators.handleContentChange(element, rangeInElement, newElementName.substringBeforeLast('.'))
}
