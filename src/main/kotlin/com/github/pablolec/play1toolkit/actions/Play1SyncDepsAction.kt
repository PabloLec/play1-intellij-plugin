package com.github.pablolec.play1toolkit.actions

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.project.Play1CliCommandId
import com.github.pablolec.play1toolkit.project.Play1CliRequest
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class Play1SyncDepsAction : AnAction("Sync Dependencies") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = Play1Settings.getInstance()

        if (settings.playHome.isBlank()) {
            ApplicationManager.getApplication().invokeLater {
                val result = Messages.showOkCancelDialog(
                    project,
                    "Play Home is not configured.\nPlease configure it before syncing dependencies.",
                    "Play Home Required",
                    "Open Settings",
                    "Cancel",
                    Messages.getWarningIcon()
                )
                if (result == Messages.OK) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "play1toolkit.settings")
                }
            }
            return
        }

        syncDeps(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    companion object {
        fun syncDeps(project: Project) {
            Play1CliActionSupport.execute(project, Play1CliRequest(Play1CliCommandId.DEPS))
        }
    }
}
