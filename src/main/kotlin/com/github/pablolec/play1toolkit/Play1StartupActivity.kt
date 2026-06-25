package com.github.pablolec.play1toolkit

import com.github.pablolec.play1toolkit.actions.RepairProjectSetupAction
import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.project.Play1LibraryManager
import com.github.pablolec.play1toolkit.project.Play1LibWatcher
import com.github.pablolec.play1toolkit.project.Play1SourceRootManager
import com.github.pablolec.play1toolkit.model.RepairReport
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.startup.ProjectActivity

class Play1StartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val projectService = Play1ProjectService.getInstance(project)
        projectService.refresh()

        if (!projectService.isPlay1Project) return

        // Start watching lib/ for new JARs (triggers library refresh after play deps)
        project.service<Play1LibWatcher>().start()
        Play1SourceRootManager.configureSourceRoots(
            project = project,
            report = RepairReport(project.name),
            applicationPath = projectService.playApplicationPath,
        )

        val settings = Play1Settings.getInstance()

        if (settings.playHome.isBlank()) {
            showDetectionNotification(project)
            return
        }

        if (!isFrameworkLibraryAttached(project)) {
            RepairProjectSetupAction.runRepair(project, silent = true)
        }
    }

    private fun isFrameworkLibraryAttached(project: Project): Boolean {
        val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: return false
        return ModuleRootManager.getInstance(module).orderEntries
            .filterIsInstance<LibraryOrderEntry>()
            .any { it.libraryName in Play1LibraryManager.managedLibraryNames() }
    }

    private fun showDetectionNotification(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Play v1 Toolkit")
            ?.createNotification(
                "Play 1 Project Detected",
                "Configure Play v1 Toolkit to attach libraries and set up your project.",
                NotificationType.INFORMATION
            )
            ?.addAction(com.intellij.notification.NotificationAction.createSimple("Configure") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "play1toolkit.settings")
            })
            ?.notify(project)
    }
}
