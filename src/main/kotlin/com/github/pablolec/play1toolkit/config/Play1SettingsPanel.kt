package com.github.pablolec.play1toolkit.config

import com.github.pablolec.play1toolkit.detection.Play1HomeDetector
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
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
    private val autoRepairCheckBox = JBCheckBox("Auto-repair on project open")
    private val statusLabel = JBLabel()

    val component: JComponent = panel {
        group("Play Framework Installation") {
            row("Play Home:") {
                cell(playHomeField)
                    .resizableColumn()
                    .also {
                        playHomeField.addBrowseFolderListener(
                            "Select Play Home Directory", null, null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
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
    }

    init {
        reset()
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

    fun isModified(): Boolean {
        return playHomeField.text != settings.playHome ||
                playIdField.text != settings.defaultPlayId ||
                httpPortField.text.toIntOrNull() != settings.defaultHttpPort ||
                debugPortField.text.toIntOrNull() != settings.defaultDebugPort ||
                autoRepairCheckBox.isSelected != settings.autoRepairOnOpen
    }

    fun apply() {
        settings.playHome = playHomeField.text
        settings.defaultPlayId = playIdField.text
        settings.defaultHttpPort = httpPortField.text.toIntOrNull() ?: 9000
        settings.defaultDebugPort = debugPortField.text.toIntOrNull() ?: 5005
        settings.autoRepairOnOpen = autoRepairCheckBox.isSelected
    }

    fun reset() {
        playHomeField.text = settings.playHome
        playIdField.text = settings.defaultPlayId
        httpPortField.text = settings.defaultHttpPort.toString()
        debugPortField.text = settings.defaultDebugPort.toString()
        autoRepairCheckBox.isSelected = settings.autoRepairOnOpen
    }
}
