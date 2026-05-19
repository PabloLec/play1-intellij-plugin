package com.github.pablolec.play1toolkit.actions

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.detection.Play1ProjectDetector
import com.github.pablolec.play1toolkit.model.RepairReport
import com.github.pablolec.play1toolkit.project.Play1LibraryManager
import com.github.pablolec.play1toolkit.project.Play1RunConfigManager
import com.github.pablolec.play1toolkit.project.Play1SourceRootManager
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import java.io.File
import java.nio.file.Paths

class RepairProjectSetupAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Repairing Play 1 Project Setup", false
        ) {
            override fun run(indicator: ProgressIndicator) {
                val report = RepairReport(project.name)

                indicator.text = "Detecting Play 1 project..."
                val basePath = project.basePath
                if (basePath == null) {
                    report.error("Project root", "Cannot determine project root")
                    showReport(project, report)
                    return
                }

                val detector = Play1ProjectDetector()
                val detection = detector.detect(Paths.get(basePath))
                if (!detection.isPlay1) {
                    report.error("Play project", "Not detected — missing: ${detection.missingCriteria.joinToString()}")
                    showReport(project, report)
                    return
                }
                report.ok("Play project", "detected")

                indicator.text = "Validating Play Home..."
                val settings = Play1Settings.getInstance()
                val playHomePath = settings.playHome

                if (playHomePath.isBlank()) {
                    showSettingsRequired(project)
                    return
                }

                val playHome = Paths.get(playHomePath)
                val validation = Play1HomeValidator.validate(playHome)

                if (!validation.valid) {
                    report.error("Play home", validation.error ?: "Invalid")
                    showReport(project, report)
                    return
                }

                report.ok("Play home", playHomePath)
                report.ok("Play version", validation.playVersion ?: "unknown")

                indicator.text = "Configuring Project SDK..."
                configureProjectSdk(project, report)

                indicator.text = "Attaching Play libraries..."
                Play1LibraryManager.attachLibraries(project, playHome, report)

                indicator.text = "Configuring source roots..."
                Play1SourceRootManager.configureSourceRoots(project, report)

                indicator.text = "Creating run configuration..."
                Play1RunConfigManager.createRunConfiguration(project, report)

                val confDir = Paths.get(basePath, "conf")
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

                Play1ProjectService.getInstance(project).refresh()

                showReport(project, report)
            }
        })
    }

    private fun showReport(project: com.intellij.openapi.project.Project, report: RepairReport) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            Messages.showMessageDialog(
                project,
                report.toText(),
                "Play 1 Toolkit — Repair Report",
                if (report.hasErrors) Messages.getErrorIcon() else Messages.getInformationIcon()
            )
        }
    }

    private fun showSettingsRequired(project: com.intellij.openapi.project.Project) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
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

    private fun configureProjectSdk(project: com.intellij.openapi.project.Project, report: RepairReport) {
        val rootManager = ProjectRootManager.getInstance(project)
        if (rootManager.projectSdk != null) {
            report.skipped("Project SDK", "already configured (${rootManager.projectSdk?.name})")
            return
        }

        val javaSdkType = JavaSdk.getInstance()

        // Use any already-registered JDK first
        val existingJdk = ProjectJdkTable.getInstance().allJdks.firstOrNull { it.sdkType is JavaSdk }
        if (existingJdk != null) {
            WriteAction.runAndWait<Exception> { rootManager.projectSdk = existingJdk }
            report.ok("Project SDK", existingJdk.name)
            return
        }

        // Fall back to the JVM running the IDE
        val rawHome = System.getProperty("java.home")
        val javaHome = rawHome?.let {
            val f = File(it)
            // If inside a jre/ subdirectory, step up to the JDK root
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

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
