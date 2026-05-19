package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.actions.Play1CliActionSupport
import com.github.pablolec.play1toolkit.actions.RepairProjectSetupAction
import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.project.Play1CliCommandGroup
import com.github.pablolec.play1toolkit.project.Play1CliCommandId
import com.github.pablolec.play1toolkit.project.Play1CliRequest
import com.github.pablolec.play1toolkit.project.Play1CliRunner
import com.github.pablolec.play1toolkit.run.Play1RunConfigurationType
import com.github.pablolec.play1toolkit.services.Play1CommandExecutionService
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class ProjectStatusPanel(private val project: Project) : JBPanel<ProjectStatusPanel>(BorderLayout()) {

    private val playDetectedLabel = JBLabel()
    private val playHomeLabel = JBLabel()
    private val playVersionLabel = JBLabel()
    private val cliRuntimeLabel = JBLabel()
    private val depsModeLabel = JBLabel()
    private val runConfigLabel = JBLabel()
    private val lastCommandLabel = JBLabel("Last command: —")
    private val configureButton = JButton("Configure Play Home…")
    private val repairButton = JButton("Repair Project Setup")
    private val stopCommandButton = JButton("Stop Command")
    private val runAppButton = JButton("Run App")
    private val debugAppButton = JButton("Debug App")
    private val runStatusLabel = JBLabel()
    private val debugStatusLabel = JBLabel()
    private val commandButtons = linkedMapOf<Play1CliCommandId, JButton>()
    private val commandStatusLabels = linkedMapOf<Play1CliCommandId, JBLabel>()

    init {
        border = JBUI.Borders.empty(6)
        buildLayout()
        configureButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Play v1 Toolkit")
        }
        repairButton.addActionListener {
            RepairProjectSetupAction.runRepair(project, silent = false)
        }
        stopCommandButton.addActionListener {
            Play1CliActionSupport.stopCurrent(project)
            refresh()
        }
        runAppButton.addActionListener { launchRunConfiguration(debug = false) }
        debugAppButton.addActionListener { launchRunConfiguration(debug = true) }
        refresh()
    }

    fun refresh() {
        val service = Play1ProjectService.getInstance(project)
        service.refresh()

        val isPlay1 = service.isPlay1Project
        playDetectedLabel.text = if (isPlay1) "✓  Play 1 project detected" else "✗  Not a Play 1 project"

        val settings = Play1Settings.getInstance()
        val executionService = Play1CommandExecutionService.getInstance(project)
        val playHome = settings.playHome
        val validation = if (playHome.isBlank()) null else Play1HomeValidator.validate(Paths.get(playHome))

        if (playHome.isBlank()) {
            playHomeLabel.text = "Play Home: not configured"
            playVersionLabel.text = "Version: —"
            cliRuntimeLabel.text = "CLI Runtime: —"
            depsModeLabel.text = "Dependencies: configure Play Home first"
            configureButton.isVisible = true
        } else {
            playHomeLabel.text = "Play Home: $playHome"
            playVersionLabel.text = if (validation?.valid == true) {
                "Version: Play ${validation.playVersion}"
            } else {
                "Version: invalid — ${validation?.error ?: "unknown error"}"
            }
            cliRuntimeLabel.text = if (validation?.valid == true) {
                "CLI Runtime: ${Play1CliRunner.describeRuntime(playHome)}"
            } else {
                "CLI Runtime: unavailable"
            }
            configureButton.isVisible = validation?.valid != true
        }

        val playConfig = findPlayConfiguration()
        runConfigLabel.text = if (playConfig != null) "Run config: ✓ Play v1 App" else "Run config: missing — use Repair Project Setup"

        val projectVersion = validation?.playVersion
        val depsPlan = if (validation?.valid == true) {
            Play1CliRunner.plan(
                request = Play1CliRequest(Play1CliCommandId.DEPS),
                projectPath = project.basePath ?: "",
                playHome = playHome,
                projectPlayVersion = projectVersion,
            )
        } else null
        depsModeLabel.text = when {
            depsPlan == null -> "Dependencies: unavailable"
            depsPlan.effectivePlayHome != null && depsPlan.effectivePlayHome != playHome ->
                "Dependencies: use ${depsPlan.effectivePlayVersion ?: "unknown"} at ${depsPlan.effectivePlayHome}"
            else -> "Dependencies: use project Play Home"
        }

        val commandRunning = executionService.isRunning
        val canLaunch = isPlay1 && !commandRunning
        runAppButton.isEnabled = canLaunch
        debugAppButton.isEnabled = canLaunch
        stopCommandButton.isEnabled = commandRunning
        runStatusLabel.text = if (playConfig != null) "Launch the IntelliJ run configuration" else "Run configuration missing — click to repair"
        debugStatusLabel.text = if (playConfig != null) "Launch the IntelliJ debug configuration" else "Run configuration missing — click to repair"

        val commandsEnabled = isPlay1 && validation?.valid == true && !commandRunning
        Play1CliCommandId.entries.forEach { commandId ->
            val button = commandButtons.getValue(commandId)
            val statusLabel = commandStatusLabels.getValue(commandId)
            val plan = if (commandsEnabled) {
                Play1CliRunner.plan(
                    request = Play1CliRequest(commandId),
                    projectPath = project.basePath ?: "",
                    playHome = playHome,
                    projectPlayVersion = projectVersion,
                )
            } else null

            button.isEnabled = commandsEnabled && plan?.available == true
            statusLabel.text = when {
                !isPlay1 -> "Play 1 project required"
                validation?.valid != true -> validation?.error ?: "Configure a valid Play Home"
                commandRunning -> "Command in progress…"
                plan == null -> "Unavailable"
                plan.available -> buildStatusText(plan.runtimeDescription ?: "Ready", plan.effectivePlayHome, playHome)
                else -> plan.message
            }
        }

        revalidate()
        repaint()
    }

    private fun buildLayout() {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        content.add(section("Project", playDetectedLabel, playHomeLabel, playVersionLabel, cliRuntimeLabel, depsModeLabel, runConfigLabel))
        content.add(buttonRow(configureButton, repairButton, stopCommandButton))

        content.add(commandsSection("Run", listOf(
            Triple(runAppButton, runStatusLabel, "Run App"),
            Triple(debugAppButton, debugStatusLabel, "Debug App"),
        )))

        Play1CliCommandGroup.entries.forEach { group ->
            val rows = Play1CliCommandId.entries
                .filter { it.group == group }
                .map { commandId ->
                    val button = JButton(commandId.displayName).apply {
                        preferredSize = Dimension(140, preferredSize.height)
                        addActionListener { triggerCommand(commandId) }
                    }
                    val status = JBLabel(commandId.description)
                    commandButtons[commandId] = button
                    commandStatusLabels[commandId] = status
                    Triple(button, status, commandId.displayName)
                }
            content.add(commandsSection(group.title, rows))
        }

        content.add(section("Activity", lastCommandLabel))

        add(content, BorderLayout.NORTH)
    }

    private fun section(title: String, vararg components: JComponent): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 0)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
        }
        panel.add(JBLabel(title).apply { border = JBUI.Borders.emptyBottom(6) })
        components.forEach {
            it.alignmentX = LEFT_ALIGNMENT
            panel.add(it)
        }
        panel.maximumSize = Dimension(Int.MAX_VALUE, panel.preferredSize.height + 8)
        return panel
    }

    private fun buttonRow(vararg buttons: JButton): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(6, 0)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
        }
        buttons.forEachIndexed { index, button ->
            if (index > 0) {
                panel.add(JPanel().apply {
                    preferredSize = Dimension(8, 1)
                    maximumSize = Dimension(8, Int.MAX_VALUE)
                    isOpaque = false
                })
            }
            panel.add(button)
        }
        panel.maximumSize = Dimension(Int.MAX_VALUE, panel.preferredSize.height)
        return panel
    }

    private fun commandsSection(title: String, rows: List<Triple<JButton, JBLabel, String>>): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 0)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
        }
        panel.add(JBLabel(title).apply { border = JBUI.Borders.emptyBottom(6) })
        rows.forEach { (button, statusLabel, _) ->
            val row = JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                alignmentX = LEFT_ALIGNMENT
                add(button, BorderLayout.WEST)
                add(statusLabel, BorderLayout.CENTER)
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                border = JBUI.Borders.empty(2, 0)
            }
            panel.add(row)
        }
        panel.maximumSize = Dimension(Int.MAX_VALUE, panel.preferredSize.height + 8)
        return panel
    }

    private fun triggerCommand(commandId: Play1CliCommandId) {
        val request = when (commandId) {
            Play1CliCommandId.WAR -> {
                val dialog = Play1WarCommandDialog(project)
                if (!dialog.showAndGet()) return
                Play1CliRequest(commandId, warOutputPath = dialog.outputPath, warZip = dialog.zipAsWar)
            }
            else -> Play1CliRequest(commandId)
        }

        lastCommandLabel.text = "Last command: running ${commandId.displayName}…"
        refresh()
        Play1CliActionSupport.execute(project, request) { result ->
            lastCommandLabel.text = buildString {
                append("Last command: ${commandId.displayName} — ")
                append(if (result.success) "success" else if (result.skipped) "skipped" else "failed")
                if (result.message.isNotBlank()) append(" (${result.message})")
            }
            refresh()
        }
    }

    private fun launchRunConfiguration(debug: Boolean) {
        val settings = findPlayConfiguration()
        if (settings == null) {
            val result = Messages.showOkCancelDialog(
                project,
                "The Play 1 run configuration is missing.\nRun project repair now?",
                "Run Configuration Missing",
                "Repair Project Setup",
                "Cancel",
                Messages.getWarningIcon()
            )
            if (result == Messages.OK) {
                RepairProjectSetupAction.runRepair(project, silent = false)
            }
            return
        }

        val executor = if (debug) {
            DefaultDebugExecutor.getDebugExecutorInstance()
        } else {
            DefaultRunExecutor.getRunExecutorInstance()
        }
        ProgramRunnerUtil.executeConfiguration(settings, executor)
    }

    private fun findPlayConfiguration() =
        RunManager.getInstance(project).allSettings.firstOrNull { it.type is Play1RunConfigurationType }

    private fun buildStatusText(runtime: String, effectivePlayHome: String?, configuredPlayHome: String): String =
        if (effectivePlayHome != null && effectivePlayHome != configuredPlayHome) {
            "Ready — $runtime via alternate Play Home"
        } else {
            "Ready — $runtime"
        }
}
