package com.github.pablolec.play1toolkit.config

import com.github.pablolec.play1toolkit.detection.Play1HomeDetector
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.project.Play1VersionDownloader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.nio.file.Paths
import javax.swing.JComponent

class Play1SettingsPanel {

    private val settings = Play1Settings.getInstance()

    private val playHomeField = TextFieldWithBrowseButton()
    private val playIdField = JBTextField()
    private val httpPortField = JBTextField()
    private val debugPortField = JBTextField()
    private val autoRepairCheckBox = JBCheckBox("Auto-configure project on open")
    private val statusLabel = JBLabel()

    private val depsPlayHomeField = TextFieldWithBrowseButton()
    private val depsStatusLabel = JBLabel()
    private val depsInstalledLabel = JBLabel()

    val component: JComponent = panel {
        group("Play Framework Installation") {
            row("Play Home:") {
                cell(playHomeField)
                    .resizableColumn()
                    .also {
                        playHomeField.addBrowseFolderListener(
                            TextBrowseFolderListener(
                                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                    .apply { title = "Select Play Home Directory" }
                            )
                        )
                    }
                button("Auto-detect") {
                    val detected = Play1HomeDetector.detect()
                    if (detected != null) {
                        playHomeField.text = detected.toString()
                        updateStatus(detected.toString())
                    } else {
                        statusLabel.text = "Play Home not found automatically."
                    }
                }
                button("Validate") {
                    updateStatus(playHomeField.text)
                }
            }
            row {
                cell(statusLabel)
            }
        }

        group("Defaults") {
            row("Default Play ID:") {
                cell(playIdField)
            }
            row("HTTP port:") {
                cell(httpPortField)
            }
            row("Debug port:") {
                cell(debugPortField)
            }
        }

        group("Behavior") {
            row {
                cell(autoRepairCheckBox)
            }
        }

        group("Dependency Resolution") {
            row {
                comment(
                    "Optional: a Play 1.2+ installation used exclusively for running <b>play deps</b>. " +
                    "Required if your project runs on Play 1.1.x (which has no dependency resolution command)."
                )
            }
            row("Play Home for deps:") {
                cell(depsPlayHomeField)
                    .resizableColumn()
                    .also {
                        depsPlayHomeField.addBrowseFolderListener(
                            TextBrowseFolderListener(
                                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                    .apply { title = "Select Play Home for Dependency Resolution" }
                            )
                        )
                    }
                button("Download Play 1.5.3") { downloadRecommended() }
            }
            row { cell(depsStatusLabel) }
            row("Installed:") { cell(depsInstalledLabel) }
        }
    }

    init {
        reset()
        refreshDepsInstalledLabel()
    }

    private fun updateStatus(path: String) {
        if (path.isBlank()) {
            statusLabel.text = ""
            return
        }
        val result = Play1HomeValidator.validate(Paths.get(path))
        statusLabel.text = if (result.valid) {
            "✓ Play ${result.playVersion ?: "1.x"} — valid installation"
        } else {
            "✗ ${result.error}"
        }
    }

    private fun refreshDepsInstalledLabel() {
        val installed = Play1VersionDownloader.listInstalled()
        depsInstalledLabel.text = if (installed.isEmpty()) "none" else installed.joinToString(", ")
    }

    private fun downloadRecommended() {
        val release = Play1VersionDownloader.RECOMMENDED_FOR_DEPS
        depsStatusLabel.text = "Downloading Play ${release.version}…"
        ProgressManager.getInstance().run(object : com.intellij.openapi.progress.Task.Backgroundable(
            null, "Downloading Play ${release.version}", true
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                val path = Play1VersionDownloader.download(release, indicator)
                ApplicationManager.getApplication().invokeLater {
                    if (path != null) {
                        depsPlayHomeField.text = path.toString()
                        depsStatusLabel.text = "✓ Play ${release.version} installed"
                        refreshDepsInstalledLabel()
                    } else {
                        depsStatusLabel.text = "✗ Download failed — check your internet connection"
                    }
                }
            }
        })
    }

    fun isModified(): Boolean {
        return playHomeField.text != settings.playHome ||
                depsPlayHomeField.text != settings.depsPlayHome ||
                playIdField.text != settings.defaultPlayId ||
                httpPortField.text.toIntOrNull() != settings.defaultHttpPort ||
                debugPortField.text.toIntOrNull() != settings.defaultDebugPort ||
                autoRepairCheckBox.isSelected != settings.autoRepairOnOpen
    }

    fun apply() {
        settings.playHome = playHomeField.text
        settings.depsPlayHome = depsPlayHomeField.text
        settings.defaultPlayId = playIdField.text
        settings.defaultHttpPort = httpPortField.text.toIntOrNull() ?: 9000
        settings.defaultDebugPort = debugPortField.text.toIntOrNull() ?: 5005
        settings.autoRepairOnOpen = autoRepairCheckBox.isSelected
    }

    fun reset() {
        playHomeField.text = settings.playHome
        depsPlayHomeField.text = settings.depsPlayHome
        playIdField.text = settings.defaultPlayId
        httpPortField.text = settings.defaultHttpPort.toString()
        debugPortField.text = settings.defaultDebugPort.toString()
        autoRepairCheckBox.isSelected = settings.autoRepairOnOpen
        refreshDepsInstalledLabel()
    }
}
