package com.github.pablolec.play1toolkit.actions

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.detection.Play1ProjectDetector
import com.github.pablolec.play1toolkit.model.RepairReport
import com.github.pablolec.play1toolkit.project.Play1DepsRunner
import com.github.pablolec.play1toolkit.project.Play1LibraryManager
import com.github.pablolec.play1toolkit.project.Play1RunConfigManager
import com.github.pablolec.play1toolkit.project.Play1SourceRootManager
import com.github.pablolec.play1toolkit.run.Play1JavaEnvironmentResolver
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import java.io.File
import java.nio.file.Paths

class RepairProjectSetupAction : AnAction() {

    companion object {
        fun runRepair(project: Project, silent: Boolean = false) {
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project, "Repairing Play v1 Project Setup", false
            ) {
                override fun run(indicator: ProgressIndicator) {
                    val report = RepairReport(project.name)
                    performRepair(project, report, indicator)
                    if (!silent) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showMessageDialog(
                                project,
                                report.toText(),
                                "Play v1 Toolkit — Repair Report",
                                if (report.hasErrors) Messages.getErrorIcon() else Messages.getInformationIcon()
                            )
                        }
                    } else if (!report.hasErrors) {
                        ApplicationManager.getApplication().invokeLater {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("Play v1 Toolkit")
                                ?.createNotification(
                                    "Play v1 Toolkit",
                                    "Project setup complete — libraries attached and source roots configured.",
                                    NotificationType.INFORMATION
                                )
                                ?.notify(project)
                        }
                    }
                }
            })
        }

        private fun performRepair(project: Project, report: RepairReport, indicator: ProgressIndicator) {
            val basePath = project.basePath
            if (basePath == null) {
                report.error("Project root", "Cannot determine project root")
                return
            }
            report.ok("IntelliJ project root", basePath)

            indicator.text = "Detecting Play 1 project..."
            val projectService = Play1ProjectService.getInstance(project)
            projectService.refresh()
            val detection = projectService.detectionResult ?: Play1ProjectDetector().detect(Paths.get(basePath))
            if (!detection.isPlay1) {
                report.error("Play project", "Not detected — missing: ${detection.missingCriteria.joinToString()}")
                return
            }
            val applicationPath = detection.projectRoot?.toString() ?: basePath
            report.ok("Play project", "detected at $applicationPath")

            indicator.text = "Validating Play Home..."
            val settings = Play1Settings.getInstance()
            val playHomePath = settings.playHome

            if (playHomePath.isBlank()) return

            val playHome = Paths.get(playHomePath)
            val validation = Play1HomeValidator.validate(playHome)

            if (!validation.valid) {
                report.error("Play home", validation.error ?: "Invalid")
                return
            }

            report.ok("Play home", playHomePath)
            report.ok("Play version", validation.playVersion ?: "unknown")

            indicator.text = "Configuring Project SDK..."
            configureProjectSdk(project, report)
            val javaEnvironment = Play1JavaEnvironmentResolver.resolve(project, applicationPath)
            if (javaEnvironment == null) {
                report.error("Java runtime", "No valid Java SDK configured")
            } else {
                report.ok("Java runtime", javaEnvironment.sdkHomePath)
            }

            indicator.text = "Resolving dependencies..."
            if (javaEnvironment == null) {
                report.skipped("play deps", "No valid Java SDK configured")
            } else {
                val depsResult = Play1DepsRunner.run(
                    projectPath = applicationPath,
                    playHome = playHomePath,
                    playVersion = validation.playVersion,
                    environmentOverrides = javaEnvironment.env,
                    indicator = indicator,
                )

                when {
                    depsResult.success -> report.ok("play deps", depsResult.message)
                    else -> report.skipped(
                        "play deps",
                        listOfNotNull(depsResult.message, depsResult.detail).joinToString(" — ").ifBlank { depsResult.message }
                    )
                }
            }

            indicator.text = "Attaching Play libraries..."
            Play1LibraryManager.attachLibraries(project, playHome, report, applicationPath)

            indicator.text = "Configuring source roots..."
            Play1SourceRootManager.configureSourceRoots(project, report, applicationPath)

            indicator.text = "Creating run configuration..."
            Play1RunConfigManager.createRunConfiguration(project, report, applicationPath)

            val confDir = Paths.get(applicationPath, "conf")
            if (confDir.resolve("routes").toFile().exists()) {
                report.ok("Routes file", "found")
            } else {
                report.error("Routes file", "conf/routes not found")
            }

            if (confDir.resolve("application.conf").toFile().exists()) {
                report.ok("Application config", "found")
            } else {
                report.error("Application config", "conf/application.conf not found")
            }

            projectService.refresh()
        }

        private fun configureProjectSdk(project: Project, report: RepairReport) {
            val rootManager = ProjectRootManager.getInstance(project)
            if (rootManager.projectSdk != null) {
                report.skipped("Project SDK", "already configured (${rootManager.projectSdk?.name})")
                return
            }

            val javaSdkType = JavaSdk.getInstance()
            val existingJdk = ProjectJdkTable.getInstance().allJdks.firstOrNull { it.sdkType is JavaSdk }
            if (existingJdk != null) {
                WriteAction.runAndWait<Exception> { rootManager.projectSdk = existingJdk }
                report.ok("Project SDK", existingJdk.name)
                return
            }

            val rawHome = System.getProperty("java.home")
            val javaHome = rawHome?.let {
                val f = File(it)
                if (f.name == "jre") f.parentFile?.absolutePath ?: it else it
            }

            if (javaHome == null || !javaSdkType.isValidSdkHome(javaHome)) {
                report.error("Project SDK", "No JDK found — please configure manually via File > Project Structure")
                return
            }

            val sdkName = javaSdkType.suggestSdkName(null, javaHome)
            val newJdk = javaSdkType.createJdk(sdkName, javaHome)
            WriteAction.runAndWait<Exception> {
                ProjectJdkTable.getInstance().addJdk(newJdk)
                rootManager.projectSdk = newJdk
            }
            report.ok("Project SDK", sdkName)
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = Play1Settings.getInstance()
        if (settings.playHome.isBlank()) {
            showSettingsRequired(project)
            return
        }
        runRepair(project, silent = false)
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

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
