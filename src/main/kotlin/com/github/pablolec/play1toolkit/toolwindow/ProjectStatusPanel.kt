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
import com.github.pablolec.play1toolkit.runtime.Play1ApplicationRuntimeService
import com.github.pablolec.play1toolkit.services.Play1CommandExecutionService
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.time.Duration
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

class ProjectStatusPanel(private val project: Project) : JBPanel<ProjectStatusPanel>(BorderLayout()), Disposable {

    private val playDetectedLabel = JBLabel()
    private val playApplicationPathLabel = JBLabel()
    private val playHomeLabel = JBLabel()
    private val playVersionLabel = JBLabel()
    private val cliRuntimeLabel = JBLabel()
    private val depsModeLabel = JBLabel()
    private val runConfigLabel = JBLabel()
    private val lastCommandLabel = JBLabel("Last command: —")
    private val configureButton = JButton("Configure Play Home…")
    private val repairButton = JButton("Repair Project Setup")
    private val runAppButton = JButton("Run App")
    private val debugAppButton = JButton("Debug App")
    private val runStatusLabel = JBLabel()
    private val debugStatusLabel = JBLabel()
    private val runtimeReadinessLabel = JBLabel()
    private val runtimeTimingLabel = JBLabel()
    private val runtimeUrlLabel = JBLabel()
    private val runtimeMessageLabel = JBTextArea().apply {
        isEditable = false
        isFocusable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        rows = 1
        columns = 28
        border = JBUI.Borders.empty()
    }
    private val commandButtons = linkedMapOf<Play1CliCommandId, JButton>()
    private val commandStatusLabels = linkedMapOf<Play1CliCommandId, JBLabel>()
    private var executionListenerDisposer: (() -> Unit)? = null
    private var runtimeListenerDisposer: (() -> Unit)? = null
    private var lastRuntimeState: Play1ApplicationRuntimeService.State = Play1ApplicationRuntimeService.State()
    private val runtimeTimer = Timer(1_000) {
        refreshRuntimeStatus(lastRuntimeState)
    }

    init {
        border = JBUI.Borders.empty(6)
        buildLayout()
        configureButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Play v1 Toolkit")
        }
        repairButton.addActionListener {
            RepairProjectSetupAction.runRepair(project, silent = false)
        }
        runAppButton.addActionListener { launchRunConfiguration(debug = false) }
        debugAppButton.addActionListener { launchRunConfiguration(debug = true) }
        executionListenerDisposer = Play1CommandExecutionService.getInstance(project).addListener {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { refresh() }
        }
        runtimeListenerDisposer = Play1ApplicationRuntimeService.getInstance(project).addListener {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { refreshRuntimeStatus(it) }
        }
        refresh()
    }

    fun refresh() {
        val service = Play1ProjectService.getInstance(project)
        service.refresh()

        val isPlay1 = service.isPlay1Project
        val applicationPath = service.playApplicationPath
        playDetectedLabel.text = if (isPlay1) "✓  Play 1 project detected" else "✗  Not a Play 1 project"
        playApplicationPathLabel.text = "Application path: ${applicationPath ?: "—"}"

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
                projectPath = applicationPath ?: "",
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
        val runningCommandId = executionService.currentCommandId
        val canLaunch = isPlay1 && !commandRunning
        runAppButton.isEnabled = canLaunch
        debugAppButton.isEnabled = canLaunch
        runStatusLabel.text = if (playConfig != null) "Launch the IntelliJ run configuration" else "Run configuration missing — click to repair"
        debugStatusLabel.text = if (playConfig != null) "Launch the IntelliJ debug configuration" else "Run configuration missing — click to repair"

        Play1CliCommandId.entries.forEach { commandId ->
            val button = commandButtons.getValue(commandId)
            val statusLabel = commandStatusLabels.getValue(commandId)
            val plan = if (isPlay1 && validation?.valid == true) {
                Play1CliRunner.plan(
                    request = Play1CliRequest(commandId),
                    projectPath = applicationPath ?: "",
                    playHome = playHome,
                    projectPlayVersion = projectVersion,
                )
            } else null

            val commandRunningHere = runningCommandId == commandId
            button.isEnabled = isPlay1 && validation?.valid == true && !commandRunning && plan?.available == true
            statusLabel.text = when {
                !isPlay1 -> "Play 1 project required"
                validation?.valid != true -> validation?.error ?: "Configure a valid Play Home"
                commandRunningHere -> "Command in progress…"
                plan == null -> "Unavailable"
                plan.available -> commandId.description
                else -> plan.message
            }
        }

        revalidate()
        repaint()
    }

    private fun refreshRuntimeStatus(state: Play1ApplicationRuntimeService.State) {
        lastRuntimeState = state
        runtimeReadinessLabel.text = runtimeReadinessText(state)
        val timingText = runtimeTimingText(state)
        runtimeTimingLabel.text = timingText
        runtimeTimingLabel.isVisible = timingText.isNotBlank()
        val wakeUpUrl = state.url
        runtimeUrlLabel.text = wakeUpUrl?.let { "↗ Wake-up URL: $it" }.orEmpty()
        runtimeUrlLabel.isVisible = !wakeUpUrl.isNullOrBlank()
        val messageText = runtimeMessageText(state)
        runtimeMessageLabel.text = messageText
        runtimeMessageLabel.isVisible = messageText.isNotBlank()
        if (shouldAnimateRuntimeTimer(state)) {
            if (!runtimeTimer.isRunning) runtimeTimer.start()
        } else {
            runtimeTimer.stop()
        }
        revalidate()
        repaint()
    }

    private fun buildLayout() {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        content.add(commandsSection("Run", listOf(
            Triple(runAppButton, runStatusLabel, "Run App"),
            Triple(debugAppButton, debugStatusLabel, "Debug App"),
        )))
        content.add(readinessSection())

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

        content.add(section("Project", playDetectedLabel, playApplicationPathLabel, playHomeLabel, playVersionLabel, cliRuntimeLabel, depsModeLabel, runConfigLabel))
        content.add(buttonRow(configureButton, repairButton))
        content.add(section("Activity", lastCommandLabel))

        add(content, BorderLayout.NORTH)
    }

    private fun section(title: String, vararg components: JComponent): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10, 0)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
        }
        panel.add(JBLabel(title).apply { border = JBUI.Borders.emptyBottom(6) })
        components.forEach {
            it.alignmentX = LEFT_ALIGNMENT
            panel.add(it)
        }
        panel.maximumSize = Dimension(Int.MAX_VALUE, Short.MAX_VALUE.toInt())
        return panel
    }

    private fun buttonRow(vararg buttons: JButton): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(4, 0, 10, 0)
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
        panel.maximumSize = Dimension(Int.MAX_VALUE, Short.MAX_VALUE.toInt())
        return panel
    }

    private fun commandsSection(title: String, rows: List<Triple<JButton, JBLabel, String>>): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10, 0)
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
        panel.maximumSize = Dimension(Int.MAX_VALUE, Short.MAX_VALUE.toInt())
        return panel
    }

    private fun readinessSection(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10, 0)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
        }

        panel.add(JBLabel("Readiness").apply {
            border = JBUI.Borders.emptyBottom(8)
            font = font.deriveFont(Font.BOLD)
        })
        panel.add(runtimeReadinessLabel.apply {
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(2, 0, 6, 0)
            font = font.deriveFont(Font.BOLD)
        })
        listOf<JComponent>(runtimeTimingLabel, runtimeUrlLabel, runtimeMessageLabel).forEach { component ->
            panel.add(component.apply {
                alignmentX = LEFT_ALIGNMENT
                border = JBUI.Borders.empty(2, 12)
                maximumSize = Dimension(Int.MAX_VALUE, Short.MAX_VALUE.toInt())
            })
        }

        panel.maximumSize = Dimension(Int.MAX_VALUE, Short.MAX_VALUE.toInt())
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

    private fun runtimeReadinessText(state: Play1ApplicationRuntimeService.State): String {
        return when (state.applicationStatus) {
            Play1ApplicationRuntimeService.ApplicationStatus.RUNNING ->
                "✅ Ready - first readiness request succeeded${state.wakeUpStatusCode?.let { " (HTTP $it)" } ?: ""}"
            Play1ApplicationRuntimeService.ApplicationStatus.WAKING ->
                "⏳ Waking - first request is still running"
            Play1ApplicationRuntimeService.ApplicationStatus.WAITING_FOR_SERVER ->
                "🚀 Starting - waiting for the HTTP port"
            Play1ApplicationRuntimeService.ApplicationStatus.FAILED ->
                "⚠️ Failed - wake-up request did not complete"
            Play1ApplicationRuntimeService.ApplicationStatus.UNKNOWN ->
                when (state.serverStatus) {
                    Play1ApplicationRuntimeService.ServerStatus.STOPPED -> "⏹ Stopped"
                    Play1ApplicationRuntimeService.ServerStatus.FAILED -> "⚠️ Failed - process exited"
                    else -> "— Not running"
                }
        }
    }

    private fun runtimeTimingText(state: Play1ApplicationRuntimeService.State): String {
        val startedAt = state.startedAt ?: return ""
        val reference = state.readyAt ?: java.time.Instant.now()
        val startupTime = Duration.between(startedAt, reference).coerceAtLeast(Duration.ZERO)
        return if (state.readyAt != null) {
            "⏱ Startup time: ${formatDuration(startupTime)}"
        } else {
            "⏱ Startup time: ${formatDuration(startupTime)} elapsed"
        }
    }

    private fun runtimeMessageText(state: Play1ApplicationRuntimeService.State): String {
        val message = state.message.trim()
        if (message.isBlank() || message == "No Play application process is running.") return ""
        return when (state.applicationStatus) {
            Play1ApplicationRuntimeService.ApplicationStatus.WAITING_FOR_SERVER,
            Play1ApplicationRuntimeService.ApplicationStatus.WAKING -> "ℹ️ $message"
            Play1ApplicationRuntimeService.ApplicationStatus.FAILED -> "⚠️ $message"
            Play1ApplicationRuntimeService.ApplicationStatus.RUNNING -> ""
            Play1ApplicationRuntimeService.ApplicationStatus.UNKNOWN -> when (state.serverStatus) {
                Play1ApplicationRuntimeService.ServerStatus.FAILED -> "⚠️ $message"
                else -> ""
            }
        }
    }

    private fun formatDuration(duration: Duration): String {
        val millis = duration.toMillis()
        return if (millis < 1_000) {
            "${millis}ms"
        } else {
            "%.1fs".format(java.util.Locale.ROOT, millis / 1_000.0)
        }
    }

    private fun shouldAnimateRuntimeTimer(state: Play1ApplicationRuntimeService.State): Boolean =
        state.startedAt != null &&
            state.readyAt == null &&
            state.applicationStatus in setOf(
                Play1ApplicationRuntimeService.ApplicationStatus.WAITING_FOR_SERVER,
                Play1ApplicationRuntimeService.ApplicationStatus.WAKING,
            )

    override fun dispose() {
        runtimeTimer.stop()
        executionListenerDisposer?.invoke()
        executionListenerDisposer = null
        runtimeListenerDisposer?.invoke()
        runtimeListenerDisposer = null
    }
}
