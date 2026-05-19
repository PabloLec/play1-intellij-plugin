package com.github.pablolec.play1toolkit.actions

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.model.RepairReport
import com.github.pablolec.play1toolkit.project.Play1CliCommandId
import com.github.pablolec.play1toolkit.project.Play1CliRequest
import com.github.pablolec.play1toolkit.project.Play1CliResult
import com.github.pablolec.play1toolkit.project.Play1CliResultReason
import com.github.pablolec.play1toolkit.project.Play1CliRunner
import com.github.pablolec.play1toolkit.project.Play1LibraryManager
import com.github.pablolec.play1toolkit.services.Play1CommandExecutionService
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.nio.file.Paths

object Play1CliActionSupport {

    fun execute(
        project: Project,
        request: Play1CliRequest,
        onFinished: ((Play1CliResult) -> Unit)? = null,
    ) {
        val executionService = Play1CommandExecutionService.getInstance(project)
        if (!executionService.start(request.commandId)) {
            notifyAlreadyRunning(project, executionService.currentCommandId)
            return
        }
        val settings = Play1Settings.getInstance()
        val playHome = settings.playHome
        val basePath = project.basePath
        if (playHome.isBlank() || basePath == null) {
            executionService.finish()
            return
        }

        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val title = "Play v1: ${request.commandId.displayName}"
        val descriptor = RunContentDescriptor(console, null, console.component, title)

        ApplicationManager.getApplication().invokeLater {
            val executor = com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance()
            RunContentManager.getInstance(project).showRunContent(executor, descriptor)
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                fun print(text: String, type: ConsoleViewContentType = ConsoleViewContentType.NORMAL_OUTPUT) {
                    ApplicationManager.getApplication().invokeLater {
                        console.print("$text\n", type)
                    }
                }

                val requestedVersion = Play1HomeValidator.validate(Paths.get(playHome)).playVersion
                val plan = Play1CliRunner.plan(
                    request = request,
                    projectPath = basePath,
                    playHome = playHome,
                    projectPlayVersion = requestedVersion,
                )

                print("Play v1 Toolkit — ${request.commandId.displayName}")
                print("Project: ${project.name}")
                print("Play Home: $playHome")
                plan.effectivePlayHome?.takeIf { it != playHome }?.let {
                    print("Effective Play Home: $it")
                }
                plan.effectivePlayVersion?.let { print("Play Version: $it") }
                plan.runtimeDescription?.let { print("Runtime: $it") }
                print("")

                if (!plan.available) {
                    val result = Play1CliResult(
                        request = request,
                        success = false,
                        skipped = true,
                        message = plan.message,
                        reason = plan.reason,
                        effectivePlayHome = plan.effectivePlayHome,
                        effectivePlayVersion = plan.effectivePlayVersion,
                        runtimeDescription = plan.runtimeDescription,
                        requiredPythonMajor = plan.requiredPythonMajor,
                        detail = plan.detail,
                    )
                    finish(project, request.commandId, result, { text, type -> print(text, type) }, playHome)
                    executionService.finish()
                    onFinished?.let { callback -> ApplicationManager.getApplication().invokeLater { callback(result) } }
                    return
                }

                if (request.commandId == Play1CliCommandId.DEPS && plan.effectivePlayHome != null && plan.effectivePlayHome != playHome) {
                    print("⚠  Project Play ${requestedVersion ?: "unknown"} doesn't support 'play deps'.", ConsoleViewContentType.LOG_WARNING_OUTPUT)
                    print("→  Using ${plan.effectivePlayHome} (Play ${plan.effectivePlayVersion ?: "unknown"}) for dependency resolution.")
                }

                val result = Play1CliRunner.run(
                    request = request,
                    projectPath = basePath,
                    playHome = playHome,
                    projectPlayVersion = requestedVersion,
                    indicator = indicator,
                    onProcessStarted = { executionService.attachProcess(it) },
                    shouldStop = { executionService.stopRequested },
                    onLine = { line, isErr ->
                        ApplicationManager.getApplication().invokeLater {
                            console.print(
                                "$line\n",
                                if (isErr) ConsoleViewContentType.ERROR_OUTPUT
                                else ConsoleViewContentType.NORMAL_OUTPUT
                            )
                        }
                    },
                )

                finish(project, request.commandId, result, { text, type -> print(text, type) }, playHome)
                executionService.finish()
                onFinished?.let { callback -> ApplicationManager.getApplication().invokeLater { callback(result) } }
            }

            override fun onCancel() {
                executionService.requestStop()
            }
        })
    }

    fun stopCurrent(project: Project) {
        Play1CommandExecutionService.getInstance(project).requestStop()
    }

    private fun finish(
        project: Project,
        commandId: Play1CliCommandId,
        result: Play1CliResult,
        print: (String, ConsoleViewContentType) -> Unit,
        playHome: String,
    ) {
        print("", ConsoleViewContentType.NORMAL_OUTPUT)
        when {
            result.skipped -> {
                print("⚠  Skipped: ${result.message}", ConsoleViewContentType.LOG_WARNING_OUTPUT)
                result.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    print("↳  Cause: $detail", ConsoleViewContentType.LOG_WARNING_OUTPUT)
                }
                notifyFailure(project, commandId, result, warning = true)
            }
            result.success -> {
                print("✓  ${result.message}", ConsoleViewContentType.LOG_INFO_OUTPUT)
                if (commandId == Play1CliCommandId.DEPS) {
                    val report = RepairReport(project.name)
                    Play1LibraryManager.attachLibraries(project, Paths.get(playHome), report)
                }
                notifySuccess(project, commandId)
            }
            else -> {
                print("✗  ${result.message}", ConsoleViewContentType.ERROR_OUTPUT)
                result.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    print("↳  Cause: $detail", ConsoleViewContentType.ERROR_OUTPUT)
                }
                notifyFailure(project, commandId, result, warning = false)
            }
        }
    }

    private fun notifySuccess(project: Project, commandId: Play1CliCommandId) {
        val message = when (commandId) {
            Play1CliCommandId.DEPS -> "Dependencies synced — ${project.name} classpath updated."
            else -> "${commandId.displayName} completed successfully."
        }
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Play v1 Toolkit")
                ?.createNotification("Play v1 Toolkit", message, NotificationType.INFORMATION)
                ?.notify(project)
        }
    }

    private fun notifyFailure(project: Project, commandId: Play1CliCommandId, result: Play1CliResult, warning: Boolean) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Play v1 Toolkit") ?: return
        val type = if (warning) NotificationType.WARNING else NotificationType.ERROR
        val content = when (result.reason) {
            Play1CliResultReason.UNSUPPORTED_PLAY_VERSION ->
                "Dependency resolution unavailable — project Play version < 1.2. Configure a Play 1.2+ installation in Settings to enable it."
            Play1CliResultReason.PYTHON_INTERPRETER_MISSING ->
                "${commandId.displayName} failed — Python ${result.requiredPythonMajor ?: ""} is not available."
            Play1CliResultReason.MANAGED_RUNTIME_UNAVAILABLE ->
                buildString {
                    append("${commandId.displayName} failed — could not provision the managed PyPy 2.7 runtime.")
                    result.detail?.takeIf { it.isNotBlank() }?.let { append(" Cause: $it") }
                }
            Play1CliResultReason.MANAGED_PLAY_HOME_UNAVAILABLE ->
                "Dependency sync failed — could not download or validate the managed Play ${com.github.pablolec.play1toolkit.project.Play1VersionDownloader.RECOMMENDED_FOR_DEPS.version} runtime."
            Play1CliResultReason.EXECUTION_CANCELLED ->
                "${commandId.displayName} was stopped."
            else ->
                "${commandId.displayName} failed — see \"Play v1: ${commandId.displayName}\" console for details."
        }

        ApplicationManager.getApplication().invokeLater {
            group.createNotification("Play v1 Toolkit", content, type).apply {
                if (result.reason == Play1CliResultReason.UNSUPPORTED_PLAY_VERSION) {
                    addAction(object : com.intellij.openapi.actionSystem.AnAction("Open Settings") {
                        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                            ShowSettingsUtil.getInstance().showSettingsDialog(project, "play1toolkit.settings")
                        }
                    })
                }
            }.notify(project)
        }
    }

    private fun notifyAlreadyRunning(project: Project, currentCommandId: Play1CliCommandId?) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Play v1 Toolkit")
                ?.createNotification(
                    "Play v1 Toolkit",
                    "A Play command is already running${currentCommandId?.let { ": ${it.displayName}" } ?: ""}. Stop it before starting another one.",
                    NotificationType.WARNING
                )
                ?.notify(project)
        }
    }
}
