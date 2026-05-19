package com.github.pablolec.play1toolkit.actions

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.model.RepairReport
import com.github.pablolec.play1toolkit.project.Play1DepsRunner
import com.github.pablolec.play1toolkit.project.Play1LibraryManager
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Paths



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

        val basePath = project.basePath ?: return
        syncDeps(project, basePath, settings.playHome)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    companion object {
        fun syncDeps(project: Project, basePath: String, playHome: String) {
            val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
            val descriptor = RunContentDescriptor(console, null, console.component, "Play 1: Sync Dependencies")

            ApplicationManager.getApplication().invokeLater {
                val executor = com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance()
                RunContentManager.getInstance(project).showRunContent(executor, descriptor)
            }

            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project, "Play 1: Syncing dependencies", true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    fun print(text: String, type: ConsoleViewContentType = ConsoleViewContentType.NORMAL_OUTPUT) {
                        ApplicationManager.getApplication().invokeLater {
                            console.print("$text\n", type)
                        }
                    }

                    print("Play 1 Toolkit — Sync Dependencies")
                    print("Project: ${project.name}")
                    print("Play Home: $playHome")
                    print("")

                    val playVersion = Play1HomeValidator.validate(Paths.get(playHome)).playVersion
                    val result = Play1DepsRunner.run(
                        projectPath = basePath,
                        playHome = playHome,
                        playVersion = playVersion,
                        onLine = { line, isErr ->
                            ApplicationManager.getApplication().invokeLater {
                                console.print(
                                    "$line\n",
                                    if (isErr) ConsoleViewContentType.ERROR_OUTPUT
                                    else ConsoleViewContentType.NORMAL_OUTPUT
                                )
                            }
                        }
                    )

                    print("")
                    when {
                        result.skipped -> {
                            print("⚠  Skipped: ${result.message}", ConsoleViewContentType.LOG_WARNING_OUTPUT)
                        }
                        result.success -> {
                            print("✓  ${result.message}", ConsoleViewContentType.LOG_INFO_OUTPUT)

                            // Refresh library attachment so new JARs become visible immediately
                            indicator.text = "Attaching new JARs..."
                            val report = RepairReport(project.name)
                            Play1LibraryManager.attachLibraries(project, Paths.get(playHome), report)

                            ApplicationManager.getApplication().invokeLater {
                                NotificationGroupManager.getInstance()
                                    .getNotificationGroup("Play 1 Toolkit")
                                    ?.createNotification(
                                        "Play 1 Toolkit",
                                        "Dependencies synced — ${project.name} classpath updated.",
                                        NotificationType.INFORMATION
                                    )
                                    ?.notify(project)
                            }
                        }
                        else -> {
                            print("✗  ${result.message}", ConsoleViewContentType.ERROR_OUTPUT)
                            ApplicationManager.getApplication().invokeLater {
                                NotificationGroupManager.getInstance()
                                    .getNotificationGroup("Play 1 Toolkit")
                                    ?.createNotification(
                                        "Play 1 Toolkit",
                                        "Dependency sync failed — see \"Play 1: Sync Dependencies\" console for details.",
                                        NotificationType.ERROR
                                    )
                                    ?.notify(project)
                            }
                        }
                    }
                }
            })
        }
    }
}
