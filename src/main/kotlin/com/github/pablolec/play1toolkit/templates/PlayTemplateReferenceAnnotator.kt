package com.github.pablolec.play1toolkit.templates

import com.github.pablolec.play1toolkit.templates.references.PlayTemplateReference
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class PlayTemplateReferenceAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return

        element.references
            .filterIsInstance<PlayTemplateReference>()
            .forEach { reference ->
                val psiRef = reference as PsiReference
                if (psiRef.resolve() == null) return@forEach
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(psiRef.rangeInElement.shiftRight(element.textRange.startOffset))
                    .enforcedTextAttributes(PlayTemplateTextAttributes.NAVIGABLE_REFERENCE)
                    .create()
            }
    }
}
