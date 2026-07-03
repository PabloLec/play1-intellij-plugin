package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.actions.Play1CliActionSupport
import com.github.pablolec.play1toolkit.actions.RepairProjectSetupAction
import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.project.Play1CliCommandGroup
import com.github.pablolec.play1toolkit.project.Play1CliCommandId
import com.github.pablolec.play1toolkit.project.Play1CliRequest
import com.github.pablolec.play1toolkit.project.Play1CliRunner
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.github.pablolec.play1toolkit.run.Play1ApplicationRunConfiguration
import com.github.pablolec.play1toolkit.run.Play1RunConfigurationType
import com.github.pablolec.play1toolkit.run.Play1RunConfigurationSupport
import com.github.pablolec.play1toolkit.runtime.Play1ApplicationRuntimeService
import com.github.pablolec.play1toolkit.services.Play1CommandExecutionService
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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
import javax.swing.DefaultComboBoxModel
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
    private val runProfileCombo = ComboBox<String>()
    private val runProfileStatusLabel = JBLabel()
    private val testProfileCombo = ComboBox<String>()
    private val testProfileStatusLabel = JBLabel()
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
    private var refreshingRunProfiles = false
    private var refreshingTestProfiles = false
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
        runProfileCombo.addActionListener {
            if (!refreshingRunProfiles) {
                val profile = selectedRunProfile().orEmpty()
                Play1Settings.getInstance().defaultPlayId = profile
                updateRunConfigurationProfile(profile)
                refresh()
            }
        }
        testProfileCombo.addActionListener {
            if (!refreshingTestProfiles) {
                Play1Settings.getInstance().testPlayId = selectedTestProfile().orEmpty()
                refresh()
            }
        }
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

        val isPlay1 = service.isPlay1Project
        val applicationPath = service.playApplicationPath
        playDetectedLabel.text = if (isPlay1) "✓  Play 1 project detected" else "✗  Not a Play 1 project"
        playApplicationPathLabel.text = "Application path: ${applicationPath ?: "—"}"

        val settings = Play1Settings.getInstance()
        val executionService = Play1CommandExecutionService.getInstance(project)
        val playHome = settings.playHome
        val validation = if (playHome.isBlank()) null else Play1HomeValidator.validate(Paths.get(playHome))
        val availableProfiles = if (isPlay1) availableProfiles() else emptyList()
        val playConfig = findPlayConfiguration()
        updateRunProfileCombo(availableProfiles, playConfig)
        updateTestProfileCombo(availableProfiles)

        if (playHome.isBlank()) {
            playHomeLabel.text = "Play Home: not configured"
            playVersionLabel.text = "Version: —"
            cliRuntimeLabel.text = "Python runtime: —"
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
                "Python runtime: ${Play1CliRunner.describeRuntime(playHome)}"
            } else {
                "Python runtime: unavailable"
            }
            configureButton.isVisible = validation?.valid != true
        }

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
        runStatusLabel.text = launchStatusText(playConfig != null, validation)
        debugStatusLabel.text = launchStatusText(playConfig != null, validation)

        Play1CliCommandId.entries.forEach { commandId ->
            val button = commandButtons.getValue(commandId)
            val statusLabel = commandStatusLabels.getValue(commandId)
            val playHomeInvalid = validation?.valid != true
            val plan = if (isPlay1 && validation?.valid == true) {
                Play1CliRunner.plan(
                    request = Play1CliRequest(commandId),
                    projectPath = applicationPath ?: "",
                    playHome = playHome,
                    projectPlayVersion = projectVersion,
                )
            } else null

            val commandRunningHere = runningCommandId == commandId
            button.isEnabled = isPlay1 && !commandRunning && (playHomeInvalid || plan?.available == true)
            statusLabel.text = when {
                !isPlay1 -> "Play 1 project required"
                validation?.valid != true -> "Configure a valid Play Home to enable this command"
                commandRunningHere -> "Command in progress…"
                plan == null -> "Unavailable"
                plan.available -> commandDescription(commandId)
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
            content.add(commandsSection(group.title, rows, if (group == Play1CliCommandGroup.BUILD_TEST) profileSelectors() else null))
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

    private fun commandsSection(
        title: String,
        rows: List<Triple<JButton, JBLabel, String>>,
        headerComponent: JComponent? = null,
    ): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10, 0)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
        }
        panel.add(JBLabel(title).apply { border = JBUI.Borders.emptyBottom(6) })
        headerComponent?.let { panel.add(it) }
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

    private fun runProfileSelector(): JPanel =
        profileSelector("Run profile:", runProfileCombo, runProfileStatusLabel)

    private fun profileSelectors(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(0, 0, 6, 0)
            add(runProfileSelector())
            add(testProfileSelector())
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun testProfileSelector(): JPanel =
        profileSelector("Test profile:", testProfileCombo, testProfileStatusLabel)

    private fun profileSelector(label: String, combo: ComboBox<String>, statusLabel: JBLabel): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(2, 0)
            add(JBLabel(label), BorderLayout.WEST)
            add(combo.apply {
                preferredSize = Dimension(180, preferredSize.height)
                minimumSize = Dimension(140, preferredSize.height)
            }, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
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
        if (!ensureValidPlayHomeConfigured()) return
        val request = when (commandId) {
            Play1CliCommandId.WAR -> {
                val dialog = Play1WarCommandDialog(project)
                if (!dialog.showAndGet()) return
                Play1CliRequest(commandId, warOutputPath = dialog.outputPath, warZip = dialog.zipAsWar)
            }
            Play1CliCommandId.TEST,
            Play1CliCommandId.AUTOTEST -> Play1CliRequest(commandId, profile = selectedTestProfile())
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
        if (!ensureValidPlayHomeConfigured()) return
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

        updateRunConfigurationProfile(selectedRunProfile().orEmpty(), settings)
        val executor = if (debug) {
            DefaultDebugExecutor.getDebugExecutorInstance()
        } else {
            DefaultRunExecutor.getRunExecutorInstance()
        }
        ProgramRunnerUtil.executeConfiguration(settings, executor)
    }

    private fun launchStatusText(hasRunConfig: Boolean, validation: Play1HomeValidator.ValidationResult?): String {
        return when {
            validation == null -> "Configure Play Home before launching"
            !validation.valid -> "Fix Play Home before launching"
            hasRunConfig -> "Launch the IntelliJ run configuration"
            else -> "Run configuration missing — click to repair"
        }
    }

    private fun ensureValidPlayHomeConfigured(): Boolean {
        val playHome = Play1Settings.getInstance().playHome.trim()
        val validation = if (playHome.isBlank()) null else Play1HomeValidator.validate(Paths.get(playHome))
        if (validation?.valid == true) return true

        val message = if (playHome.isBlank()) {
            "Play Home is not configured.\nSelect the root directory of your Play 1 installation."
        } else {
            "Play Home is invalid:\n${validation?.error ?: "unknown error"}\n\nSelect a valid Play 1 installation directory."
        }
        val result = Messages.showOkCancelDialog(
            project,
            message,
            "Play Home Required",
            "Open Settings",
            "Cancel",
            Messages.getWarningIcon(),
        )
        if (result == Messages.OK) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Play v1 Toolkit")
        }
        return false
    }

    private fun findPlayConfiguration() =
        RunManager.getInstance(project).allSettings.firstOrNull { it.type is Play1RunConfigurationType }

    private fun availableProfiles(): List<String> = try {
        PlayConfigService.getInstance(project).availableProfiles()
    } catch (_: Exception) {
        emptyList()
    }

    private fun updateRunProfileCombo(
        availableProfiles: List<String>,
        settings: RunnerAndConfigurationSettings?,
    ) {
        val configuredProfile = (settings?.configuration as? Play1ApplicationRunConfiguration)
            ?.playId
            ?.trim()
            .orEmpty()
        val selected = configuredProfile.takeIf { it.isNotBlank() }
            ?: Play1RunConfigurationSupport.selectInitialProfile(
                configuredDefault = Play1Settings.getInstance().defaultPlayId,
                availableProfiles = availableProfiles,
            )
        refreshingRunProfiles = true
        try {
            val values = profileComboValues(availableProfiles, selected)
            runProfileCombo.model = DefaultComboBoxModel(values.toTypedArray())
            runProfileCombo.selectedItem = selected.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE_LABEL
            runProfileCombo.isEnabled = availableProfiles.isNotEmpty() || selected.isNotBlank()
            runProfileStatusLabel.text = when {
                selected.isNotBlank() -> "--%$selected"
                availableProfiles.isEmpty() -> "No profile detected"
                else -> "Default"
            }
        } finally {
            refreshingRunProfiles = false
        }
    }

    private fun updateTestProfileCombo(availableProfiles: List<String>) {
        val selected = Play1RunConfigurationSupport.selectInitialTestProfile(
            configuredDefault = Play1Settings.getInstance().testPlayId,
            availableProfiles = availableProfiles,
        )
        refreshingTestProfiles = true
        try {
            val values = profileComboValues(availableProfiles, selected)
            testProfileCombo.model = DefaultComboBoxModel(values.toTypedArray())
            testProfileCombo.selectedItem = selected.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE_LABEL
            testProfileCombo.isEnabled = availableProfiles.isNotEmpty()
            testProfileStatusLabel.text = when {
                selected.isNotBlank() -> "--%$selected"
                availableProfiles.isEmpty() -> "No profile detected"
                else -> "Default"
            }
        } finally {
            refreshingTestProfiles = false
        }
    }

    private fun profileComboValues(availableProfiles: List<String>, selected: String): List<String> =
        buildList {
            add(DEFAULT_PROFILE_LABEL)
            addAll(availableProfiles)
            if (selected.isNotBlank() && selected !in availableProfiles) {
                add(selected)
            }
        }

    private fun selectedRunProfile(): String? {
        val selected = runProfileCombo.selectedItem as? String ?: return null
        return selected.takeIf { it != DEFAULT_PROFILE_LABEL }?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun selectedTestProfile(): String? {
        val selected = testProfileCombo.selectedItem as? String ?: return null
        return selected.takeIf { it != DEFAULT_PROFILE_LABEL }?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun updateRunConfigurationProfile(
        profile: String,
        settings: RunnerAndConfigurationSettings? = findPlayConfiguration(),
    ) {
        (settings?.configuration as? Play1ApplicationRunConfiguration)?.playId = profile
    }

    private fun commandDescription(commandId: Play1CliCommandId): String {
        val profile = selectedTestProfile()
        return when {
            commandId in setOf(Play1CliCommandId.TEST, Play1CliCommandId.AUTOTEST) && !profile.isNullOrBlank() ->
                "${commandId.description} with --%$profile"
            else -> commandId.description
        }
    }

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

    companion object {
        private const val DEFAULT_PROFILE_LABEL = "Default"
    }
}
