package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.actions.Play1CliActionSupport
import com.github.pablolec.play1toolkit.actions.Play1SyncDepsAction
import com.github.pablolec.play1toolkit.actions.RepairProjectSetupAction
import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.playcache.toolwindow.PlayCachePanel
import com.github.pablolec.play1toolkit.playjobs.toolwindow.PlayJobsPanel
import com.github.pablolec.play1toolkit.playjpa.toolwindow.PlayJpaModelsPanel
import com.github.pablolec.play1toolkit.services.Play1CommandExecutionService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class Play1ToolWindowFactory : ToolWindowFactory, DumbAware {

    override suspend fun isApplicableAsync(project: Project): Boolean = true

    override fun isApplicable(project: Project): Boolean = true

    override val isDoNotActivateOnStart: Boolean
        get() = false

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val statusPanel = ProjectStatusPanel(project)
        val routesPanel = RoutesTreePanel(project)
        val templatesPanel = TemplatesTreePanel(project)
        val modelsPanel = PlayJpaModelsPanel(project)
        val jobsPanel = PlayJobsPanel(project)
        val cachePanel = PlayCachePanel(project)
        val diagnosticsPanel = DiagnosticsPanel(project)
        val uiDisposable = Disposer.newDisposable("Play v1 Toolkit tool window")

        val tabs = JBTabbedPane().apply {
            addTab("Status", JBScrollPane(statusPanel))
            addTab("Routes", routesPanel)
            addTab("Templates", templatesPanel)
            addTab("Models", modelsPanel)
            addTab("Jobs", jobsPanel)
            addTab("Cache", cachePanel)
            addTab("Diagnostics", diagnosticsPanel)
        }

        val toolbar = buildToolbar(project, uiDisposable) {
            statusPanel.refresh()
            routesPanel.refresh()
            templatesPanel.refresh()
            modelsPanel.refresh()
            jobsPanel.refresh()
            cachePanel.refresh()
            diagnosticsPanel.refresh()
        }

        val content = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(tabs, BorderLayout.CENTER)
        }

        val contentManager = toolWindow.contentManager
        val twContent = contentManager.factory.createContent(content, "", false)
        twContent.setDisposer(uiDisposable)
        Disposer.register(uiDisposable, statusPanel)
        contentManager.addContent(twContent)
    }

    private fun buildToolbar(project: Project, uiDisposable: com.intellij.openapi.Disposable, onRefresh: () -> Unit): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            border = JBUI.Borders.emptyBottom(2)
        }

        val repairButton = JButton("⚙ Repair").apply {
            toolTipText = "Repair Play v1 project setup"
            addActionListener {
                if (Play1Settings.getInstance().playHome.isBlank()) {
                    showSettingsRequired(project)
                } else {
                    RepairProjectSetupAction.runRepair(project, silent = false)
                }
            }
        }

        val syncDepsButton = JButton("⬇ Sync Deps").apply {
            toolTipText = "Download and attach project dependencies (play deps)"
            addActionListener {
                if (Play1Settings.getInstance().playHome.isBlank()) return@addActionListener
                Play1SyncDepsAction.syncDeps(project)
            }
        }

        val stopButton = JButton("■ Stop").apply {
            toolTipText = "Stop the current Play command"
            isEnabled = false
            addActionListener {
                Play1CliActionSupport.stopCurrent(project)
                ApplicationManager.getApplication().invokeLater { onRefresh() }
            }
        }

        val detachListener = Play1CommandExecutionService.getInstance(project).addListener { state ->
            ApplicationManager.getApplication().invokeLater {
                stopButton.isEnabled = state.isRunning
                onRefresh()
            }
        }
        Disposer.register(uiDisposable) { detachListener.invoke() }

        val refreshButton = JButton("↺ Refresh").apply {
            toolTipText = "Refresh tool window data"
            addActionListener {
                ApplicationManager.getApplication().invokeLater { onRefresh() }
            }
        }

        panel.add(repairButton)
        panel.add(syncDepsButton)
        panel.add(stopButton)
        panel.add(refreshButton)
        return panel
    }

    private fun showSettingsRequired(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val result = Messages.showOkCancelDialog(
                project,
                "Play Home is not configured.\nPlease select your Play 1 installation directory.",
                "Play Home Required",
                "Open Settings",
                "Cancel",
                Messages.getWarningIcon()
            )
            if (result == Messages.OK) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "play1toolkit.settings")
            }
        }
    }
}
