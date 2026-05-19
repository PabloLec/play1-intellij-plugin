package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JPanel

class ProjectStatusPanel(private val project: Project) : JBPanel<ProjectStatusPanel>(GridBagLayout()) {

    private val playDetectedLabel = JBLabel()
    private val playHomeLabel = JBLabel()
    private val playVersionLabel = JBLabel()
    private val runConfigLabel = JBLabel()
    private val configureButton = JButton("Configure Play Home…")

    init {
        border = JBUI.Borders.emptyTop(4)
        buildLayout()
        configureButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Play 1 Toolkit")
        }
        refresh()
    }

    fun refresh() {
        val service = Play1ProjectService.getInstance(project)
        service.refresh()

        val isPlay1 = service.isPlay1Project
        playDetectedLabel.text = if (isPlay1) "✓  Play 1 project detected" else "✗  Not a Play 1 project"

        val settings = Play1Settings.getInstance()
        val playHome = settings.playHome

        if (playHome.isNullOrBlank()) {
            playHomeLabel.text = "Play Home: not configured"
            playVersionLabel.text = ""
            configureButton.isVisible = true
        } else {
            playHomeLabel.text = "Play Home: $playHome"
            val validation = Play1HomeValidator.validate(Paths.get(playHome))
            playVersionLabel.text = if (validation.valid) {
                "Version: Play ${validation.playVersion}"
            } else {
                "Version: invalid — ${validation.error}"
            }
            configureButton.isVisible = false
        }

        val runManager = com.intellij.execution.RunManager.getInstance(project)
        val hasPlayConfig = runManager.allConfigurationsList.any {
            it.type.id == "PLAY1_APPLICATION"
        }
        runConfigLabel.text = if (hasPlayConfig) "Run config: ✓ Play 1 App" else "Run config: not configured"

        revalidate()
        repaint()
    }

    private fun buildLayout() {
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            insets = JBUI.insets(2, 8)
        }

        fun addRow(component: javax.swing.JComponent) {
            gbc.gridy = (gbc.gridy) + 1
            add(component, gbc.clone() as GridBagConstraints)
        }

        gbc.gridy = -1
        addRow(playDetectedLabel)
        addRow(playHomeLabel)
        addRow(playVersionLabel)
        addRow(runConfigLabel)
        addRow(configureButton)

        // Filler to push content to top
        val filler = JPanel()
        gbc.gridy++
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        add(filler, gbc)
    }
}
