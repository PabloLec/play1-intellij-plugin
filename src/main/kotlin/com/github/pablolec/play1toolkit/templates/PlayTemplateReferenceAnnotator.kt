package com.github.pablolec.play1toolkit.templates

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

class PlayTemplateReferenceAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return
        val references = element.references
        if (references.isEmpty()) return

        references.forEach { reference ->
            if (reference.resolve() == null) return@forEach
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(reference.rangeInElement.shiftRight(element.textRange.startOffset))
                .textAttributes(PlayTemplateTextAttributes.NAVIGABLE_REFERENCE)
                .create()
        }
    }
}
