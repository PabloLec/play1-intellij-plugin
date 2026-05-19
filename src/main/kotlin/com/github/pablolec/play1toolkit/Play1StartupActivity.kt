package com.github.pablolec.play1toolkit

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class Play1StartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = Play1ProjectService.getInstance(project)
        service.refresh()

        if (!service.isPlay1Project) return

        val settings = Play1Settings.getInstance()
        if (settings.playHome.isNotBlank()) return

        showDetectionNotification(project)
    }

    private fun showDetectionNotification(project: Project) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Play 1 Toolkit")
            ?.createNotification(
                "Play 1 Project Detected",
                "Configure Play 1 Toolkit to attach libraries and set up your project.",
                NotificationType.INFORMATION
            )
            ?.addAction(com.intellij.notification.NotificationAction.createSimple("Configure") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "play1toolkit.settings")
            })

        notification?.notify(project)
    }
}
