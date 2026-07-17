package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.services.Play1ProjectPaths
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateModificationTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

@Service(Service.Level.PROJECT)
class Play1ProjectStructureWatcher(private val project: Project) : BulkFileListener {

    private var started = false

    fun start() {
        if (started) return
        started = true
        project.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    override fun after(events: List<VFileEvent>) {
        val knownApplicationPath = Play1ProjectPaths.applicationPath(project)
        val normalizedApplicationPath = knownApplicationPath?.replace('\\', '/')?.removeSuffix("/")
            ?: return
        val shouldRefresh = events.any { event ->
            val normalizedPath = event.path.replace('\\', '/')
            val relativePath = relativePath(normalizedPath, normalizedApplicationPath)
            relativePath?.let(::isPlayProjectStructurePath) == true
        }
        val shouldInvalidateTemplates = events.any { event ->
            val normalizedPath = event.path.replace('\\', '/')
            val relativePath = relativePath(normalizedPath, normalizedApplicationPath)
            relativePath?.let(::isPlayTemplatePath) == true
        }
        if (shouldInvalidateTemplates) {
            PlayTemplateModificationTracker.getInstance(project).incModificationCount()
        }
        if (shouldRefresh) {
            Play1ProjectService.getInstance(project).scheduleRefresh("Play project structure changed")
        }
    }

    private fun relativePath(path: String, basePath: String?): String? {
        if (basePath == null) return null
        return when {
            path == basePath -> ""
            path.startsWith("$basePath/") -> path.removePrefix("$basePath/")
            else -> null
        }
    }

    private fun isPlayProjectStructurePath(relativePath: String): Boolean =
        relativePath == "conf/application.conf" ||
            relativePath == "conf/routes" ||
            relativePath == "app/controllers" ||
            relativePath.startsWith("app/controllers/")

    private fun isPlayTemplatePath(relativePath: String): Boolean =
        relativePath == "app/views" ||
            relativePath.startsWith("app/views/")
}
