package com.github.pablolec.play1toolkit.services

import com.github.pablolec.play1toolkit.config.Play1ProjectSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Play1ProjectPaths {

    fun applicationPath(project: Project): String? {
        val service = Play1ProjectService.getInstance(project)
        service.playApplicationPath
            ?.takeIf { isDirectPlayProject(project, it) }
            ?.let { return findRoot(project, it)?.path ?: it }

        val configuredPath = Play1ProjectSettings.getInstance(project).playApplicationPath
        if (configuredPath.isNotBlank() && isDirectPlayProject(project, configuredPath)) {
            return findRoot(project, configuredPath)?.path ?: configuredPath
        }

        val basePath = project.basePath ?: return null
        ProjectRootManager.getInstance(project).contentRoots
            .firstOrNull(::isDirectPlayProject)
            ?.let { return it.path }
        return basePath.takeIf { isDirectPlayProject(project, it) }
    }

    fun refreshAndGetApplicationPath(project: Project): String? {
        val service = Play1ProjectService.getInstance(project)
        service.refresh()
        return applicationPath(project)
    }

    fun applicationScope(project: Project): GlobalSearchScope? {
        val root = applicationRoot(project) ?: return null
        return GlobalSearchScopes.directoryScope(project, root, true)
    }

    fun indexingScope(project: Project): GlobalSearchScope? =
        applicationScope(project) ?: if (ApplicationManager.getApplication().isUnitTestMode) {
            GlobalSearchScope.projectScope(project)
        } else {
            null
        }

    fun applicationRoot(project: Project): VirtualFile? {
        val basePath = applicationPath(project) ?: return null
        return findRoot(project, basePath)
            ?: Play1ProjectSettings.getInstance(project).playApplicationPath
                .takeIf { it.isNotBlank() }
                ?.let { findRoot(project, it) }
    }

    fun isUnderApplicationPath(project: Project, path: String): Boolean {
        val basePath = applicationPath(project)?.replace('\\', '/')?.removeSuffix("/") ?: return false
        val normalized = path.replace('\\', '/')
        return normalized == basePath || normalized.startsWith("$basePath/")
    }

    private fun isDirectPlayProject(project: Project, path: String): Boolean {
        findRoot(project, path)?.let { return isDirectPlayProject(it) }
        return isDirectPlayProject(Paths.get(path))
    }

    private fun findRoot(project: Project, path: String): VirtualFile? {
        return if (path.contains("://")) {
            VirtualFileManager.getInstance().findFileByUrl(path)
        } else {
            LocalFileSystem.getInstance().findFileByPath(path)
                ?: findUnderProjectBase(project, path)
                ?: VirtualFileManager.getInstance().findFileByUrl("temp://${path.removePrefix("/")}")
        }
    }

    private fun findUnderProjectBase(project: Project, path: String): VirtualFile? {
        val basePath = project.basePath?.replace('\\', '/')?.removeSuffix("/") ?: return null
        val normalized = path.replace('\\', '/')
        if (normalized != basePath && !normalized.startsWith("$basePath/")) return null
        val relativePath = normalized.removePrefix(basePath).removePrefix("/")
        return ProjectRootManager.getInstance(project).contentRoots
            .firstOrNull { it.path.replace('\\', '/').removeSuffix("/") == basePath }
            ?.let { root -> if (relativePath.isBlank()) root else root.findFileByRelativePath(relativePath) }
    }

    private fun isDirectPlayProject(root: VirtualFile): Boolean {
        val strongMatches = listOf(
            "conf/application.conf",
            "conf/routes",
            "app/controllers",
        ).count { root.findFileByRelativePath(it) != null }
        return strongMatches >= 2
    }

    private fun isDirectPlayProject(path: Path): Boolean {
        if (!Files.isDirectory(path)) return false
        val strongMatches = listOf(
            "conf/application.conf",
            "conf/routes",
            "app/controllers",
        ).count { Files.exists(path.resolve(it)) }
        return strongMatches >= 2
    }
}
