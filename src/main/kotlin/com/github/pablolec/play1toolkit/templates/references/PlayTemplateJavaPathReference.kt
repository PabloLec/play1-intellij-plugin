package com.github.pablolec.play1toolkit.templates.references

import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

class PlayTemplateJavaPathReference(
    element: PsiLiteralExpression,
    range: TextRange,
    private val logicalPath: String
) : PsiReferenceBase<PsiLiteralExpression>(element, range, true) {

    override fun resolve(): PsiElement? {
        val target = PlayTemplateFileUtils.resolveTemplatePath(element.project, logicalPath) ?: return null
        return PsiManager.getInstance(element.project).findFile(target)
    }

    override fun getVariants(): Array<Any> =
        PlayTemplateService.getInstance(element.project).getAllTemplates().map { template ->
            LookupElementBuilder.create(template.logicalPath)
                .withTypeText("template")
        }.toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        val newPath = when {
            newElementName.contains('/') -> newElementName
            logicalPath.contains('/') -> logicalPath.substringBeforeLast('/') + "/" + newElementName
            else -> newElementName
        }
        return ElementManipulators.handleContentChange(element, rangeInElement, newPath)
    }
}
