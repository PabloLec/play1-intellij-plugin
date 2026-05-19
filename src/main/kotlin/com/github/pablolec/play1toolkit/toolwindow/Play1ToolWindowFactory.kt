package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.actions.RepairProjectSetupAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class Play1ToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val statusPanel = ProjectStatusPanel(project)
        val routesPanel = RoutesTreePanel(project)
        val diagnosticsPanel = DiagnosticsPanel(project)

        val tabs = JBTabbedPane().apply {
            addTab("Status", statusPanel)
            addTab("Routes", routesPanel)
            addTab("Diagnostics", diagnosticsPanel)
        }

        val toolbar = buildToolbar {
            statusPanel.refresh()
            routesPanel.refresh()
            diagnosticsPanel.refresh()
        }

        val content = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(tabs, BorderLayout.CENTER)
        }

        val contentManager = toolWindow.contentManager
        val twContent = contentManager.factory.createContent(content, "", false)
        contentManager.addContent(twContent)
    }

    private fun buildToolbar(onRefresh: () -> Unit): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            border = JBUI.Borders.emptyBottom(2)
        }

        val repairButton = JButton("⚙ Repair").apply {
            toolTipText = "Repair Play 1 project setup"
            addActionListener { e ->
                val action = RepairProjectSetupAction()
                val dataContext = com.intellij.ide.DataManager.getInstance()
                    .getDataContext(e.source as? java.awt.Component)
                val event = AnActionEvent.createFromDataContext(
                    ActionPlaces.TOOLWINDOW_TOOLBAR_BAR,
                    null,
                    dataContext
                )
                ActionUtil.performActionDumbAwareWithCallbacks(action, event)
            }
        }

        val refreshButton = JButton("↺ Refresh").apply {
            toolTipText = "Refresh tool window data"
            addActionListener {
                ApplicationManager.getApplication().invokeLater { onRefresh() }
            }
        }

        panel.add(repairButton)
        panel.add(refreshButton)
        return panel
    }
}
