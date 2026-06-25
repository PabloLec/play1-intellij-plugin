package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.model.RepairReport
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class Play1LibWatcher(private val project: Project) : BulkFileListener {

    fun start() {
        project.messageBus.connect().subscribe(com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES, this)
    }

    override fun after(events: List<VFileEvent>) {
        val projectService = Play1ProjectService.getInstance(project)
        projectService.refresh()
        val basePath = projectService.playApplicationPath ?: return
        val libPath = Paths.get(basePath, "lib").toString()

        val hasNewJar = events.any { event ->
            event is VFileCreateEvent &&
                event.path.endsWith(".jar") &&
                event.path.startsWith(libPath)
        }

        if (!hasNewJar) return

        val settings = Play1Settings.getInstance()
        if (settings.playHome.isBlank()) return

        ApplicationManager.getApplication().invokeLater {
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project, "Play v1: refreshing libraries", false
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Attaching new JARs from lib/..."
                    val report = RepairReport(project.name)
                    Play1LibraryManager.attachLibraries(project, Paths.get(settings.playHome), report, basePath)

                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Play v1 Toolkit")
                            ?.createNotification(
                                "Play v1 Toolkit",
                                "Libraries refreshed — new JARs detected in lib/",
                                NotificationType.INFORMATION
                            )
                            ?.notify(project)
                    }
                }
            })
        }
    }
}
