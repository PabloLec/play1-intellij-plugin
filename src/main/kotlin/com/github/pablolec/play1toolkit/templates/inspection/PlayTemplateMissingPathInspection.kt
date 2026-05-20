package com.github.pablolec.play1toolkit.templates.inspection

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInspection.*
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.xml.XmlText

class PlayTemplateMissingPathInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Missing Play 1 template file"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "Play1MissingTemplatePath"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (!PlayTemplateFileUtils.isInViewsDirectory(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val xmlText = element as? XmlText ?: return
                val text = xmlText.text

                fun checkPath(path: String, match: MatchResult) {
                    val vf = PlayTemplateFileUtils.resolveTemplatePath(holder.project, path)
                    if (vf == null) {
                        val quotePos = match.value.indexOfFirst { it == '\'' || it == '"' }
                        if (quotePos < 0) return
                        val start = match.range.first + quotePos + 1
                        val end = start + path.length
                        holder.registerProblem(
                            xmlText,
                            "Template file not found: $path",
                            ProblemHighlightType.WARNING,
                            TextRange(start, end),
                            CreateMissingTemplateQuickFix(path)
                        )
                    }
                }

                PlayTemplatePatterns.TAG_EXTENDS.findAll(text).forEach { checkPath(it.groupValues[1], it) }
                PlayTemplatePatterns.TAG_INCLUDE.findAll(text).forEach { checkPath(it.groupValues[1], it) }
            }
        }
    }
}

class CreateMissingTemplateQuickFix(private val path: String) : LocalQuickFix {
    override fun getName() = "Create template '$path'"
    override fun getFamilyName() = "Create Play 1 template"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val basePath = project.basePath ?: return
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return
        val fullPath = "app/views/$path"
        val dirPath = fullPath.substringBeforeLast('/')
        val fileName = fullPath.substringAfterLast('/')
        val dir = VfsUtil.createDirectoryIfMissing(projectRoot, dirPath) ?: return
        if (dir.findChild(fileName) != null) return
        val newFile = dir.createChildData(this, fileName)
        VfsUtil.saveText(newFile, "#{extends 'main.html' /}\n#{set title:'${PlayTemplateFileUtils.titleFromTemplateFileName(fileName)}' /}\n\n")
        com.intellij.openapi.fileEditor.OpenFileDescriptor(project, newFile).navigate(true)
    }
}
