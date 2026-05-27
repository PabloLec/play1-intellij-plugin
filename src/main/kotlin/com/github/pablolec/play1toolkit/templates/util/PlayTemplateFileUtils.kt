package com.github.pablolec.play1toolkit.templates.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.nio.file.Paths

object PlayTemplateFileUtils {

    private val TEMPLATE_EXTENSIONS = setOf("html", "xml", "json", "txt")

    fun isInViewsDirectory(element: PsiElement): Boolean =
        element.containingFile?.virtualFile?.path?.contains("/app/views/") == true

    fun isInViewsDirectory(file: VirtualFile): Boolean =
        file.path.contains("/app/views/")

    fun isInTagsDirectory(file: VirtualFile): Boolean =
        file.path.contains("/app/views/tags/")

    fun isPlayTemplateFile(file: VirtualFile): Boolean =
        isInViewsDirectory(file) && !file.isDirectory && file.extension in TEMPLATE_EXTENSIONS

    fun logicalPath(project: Project, virtualFile: VirtualFile): String? {
        val basePath = project.basePath
        if (basePath != null) {
            return logicalPath(basePath, virtualFile)
        }
        val normalized = virtualFile.path.replace('\\', '/')
        val marker = "/app/views/"
        val index = normalized.indexOf(marker)
        return if (index >= 0) normalized.substring(index + marker.length) else null
    }

    fun logicalPath(basePath: String, virtualFile: VirtualFile): String? {
        val path = virtualFile.path
        val prefix = Paths.get(basePath, "app", "views").toString().replace('\\', '/') + "/"
        val normalized = path.replace('\\', '/')
        if (!normalized.startsWith(prefix)) return null
        return normalized.removePrefix(prefix)
    }

    fun tagQualifiedName(logicalPath: String): String {
        val withoutTags = if (logicalPath.startsWith("tags/")) logicalPath.removePrefix("tags/") else return ""
        val withoutExt = withoutTags.substringBeforeLast('.')
        return withoutExt.replace('/', '.')
    }

    fun normalizeTemplatePath(path: String): String = path.removePrefix("/").removePrefix("app/views/")

    fun resolveTemplatePath(project: Project, logicalPath: String): VirtualFile? {
        val normalized = normalizeTemplatePath(logicalPath)
        val basePath = project.basePath
        if (basePath != null) {
            return com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(Paths.get(basePath, "app", "views", normalized).toString())
        }
        val fileName = normalized.substringAfterLast('/')
        return FilenameIndex.getVirtualFilesByName(fileName, true, GlobalSearchScope.projectScope(project))
            .firstOrNull { logicalPath(project, it) == normalized }
    }

    fun resolvePublicAsset(project: Project, publicPath: String): VirtualFile? {
        val relative = publicPath.removePrefix("/")
        val basePath = project.basePath
        if (basePath != null) {
            return com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(Paths.get(basePath, relative).toString())
        }
        val fileName = relative.substringAfterLast('/')
        return FilenameIndex.getVirtualFilesByName(fileName, true, GlobalSearchScope.projectScope(project))
            .firstOrNull { it.path.replace('\\', '/').endsWith("/$relative") }
    }

    fun controllerNameFromLogicalPath(logicalPath: String): String? {
        val parts = logicalPath.split('/')
        if (parts.size < 2) return null
        if (parts[0] == "tags") return null
        // support sub-package paths: comptabilite/PortfolioCtl/action.html → PortfolioCtl
        return parts[parts.size - 2]
    }

    fun actionNameFromLogicalPath(logicalPath: String): String? {
        val parts = logicalPath.split('/')
        if (parts.size < 2) return null
        if (parts[0] == "tags") return null
        return parts.last().substringBeforeLast('.')
    }

    fun titleFromTemplateFileName(fileName: String): String =
        fileName.substringBeforeLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
