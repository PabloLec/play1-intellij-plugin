package com.github.pablolec.play1toolkit.config

import com.github.pablolec.play1toolkit.detection.Play1HomeDetector
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.project.Play1VersionDownloader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
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
                    } else {
                        statusLabel.text = "⚠ Play Home not found automatically. Select your Play 1 installation directory."
                    }
                }
                button("Validate now") {
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
        playHomeField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateStatus(playHomeField.text)
            override fun removeUpdate(e: DocumentEvent) = updateStatus(playHomeField.text)
            override fun changedUpdate(e: DocumentEvent) = updateStatus(playHomeField.text)
        })
        reset()
        refreshDepsInstalledLabel()
    }

    private fun updateStatus(path: String) {
        if (path.isBlank()) {
            statusLabel.text = "⚠ Play Home is required. Select the root directory of your Play 1 installation."
            return
        }
        val result = Play1HomeValidator.validate(Paths.get(path))
        statusLabel.text = if (result.valid) {
            "✓ Play ${result.playVersion ?: "1.x"} — valid installation"
        } else {
            "✗ Invalid Play Home — ${result.error}. Select a directory containing framework/ and a Play JAR."
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
        val playHome = playHomeField.text.trim()
        val validation = validatePlayHome(playHome)
        settings.playHome = playHome
        settings.depsPlayHome = depsPlayHomeField.text.trim()
        settings.defaultPlayId = playIdField.text
        settings.defaultHttpPort = httpPortField.text.toIntOrNull() ?: 9000
        settings.defaultDebugPort = debugPortField.text.toIntOrNull() ?: 5005
        settings.autoRepairOnOpen = autoRepairCheckBox.isSelected
        statusLabel.text = "✓ Play ${validation.playVersion ?: "1.x"} — valid installation"
    }

    fun reset() {
        playHomeField.text = settings.playHome
        depsPlayHomeField.text = settings.depsPlayHome
        playIdField.text = settings.defaultPlayId
        httpPortField.text = settings.defaultHttpPort.toString()
        debugPortField.text = settings.defaultDebugPort.toString()
        autoRepairCheckBox.isSelected = settings.autoRepairOnOpen
        updateStatus(playHomeField.text)
        refreshDepsInstalledLabel()
    }

    private fun validatePlayHome(path: String): Play1HomeValidator.ValidationResult {
        if (path.isBlank()) {
            updateStatus(path)
            throw ConfigurationException(
                "Play Home is required. Select the root directory of your Play 1 installation."
            )
        }
        val validation = Play1HomeValidator.validate(Paths.get(path))
        if (!validation.valid) {
            updateStatus(path)
            throw ConfigurationException(
                "Invalid Play Home: ${validation.error ?: "unknown error"}"
            )
        }
        return validation
    }
}
