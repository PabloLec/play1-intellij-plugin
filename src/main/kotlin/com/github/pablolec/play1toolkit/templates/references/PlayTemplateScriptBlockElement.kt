package com.github.pablolec.play1toolkit.templates.references

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement

class PlayTemplateScriptBlockElement(
    private val file: PsiFile,
    private val offset: Int,
    private val variableName: String
) : FakePsiElement() {

    override fun getParent(): PsiElement = file

    override fun navigate(requestFocus: Boolean) {
        val vf = file.virtualFile ?: return
        OpenFileDescriptor(file.project, vf, offset).navigate(requestFocus)
    }

    override fun canNavigate() = true
    override fun canNavigateToSource() = true
    override fun getName(): String = variableName
    override fun getPresentableText(): String = variableName
    override fun getLocationString(): String? = null
    override fun getIcon(open: Boolean) = null
}
