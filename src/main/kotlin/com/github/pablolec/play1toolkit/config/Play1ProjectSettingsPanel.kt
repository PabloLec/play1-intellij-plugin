package com.github.pablolec.play1toolkit.config

import com.github.pablolec.play1toolkit.detection.Play1ProjectDetector
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import java.nio.file.Paths
import javax.swing.JComponent

class Play1ProjectSettingsPanel(private val project: Project) {

    private val settings = Play1ProjectSettings.getInstance(project)
    private val applicationPathField = TextFieldWithBrowseButton()
    private val statusLabel = JBLabel()

    val component: JComponent = panel {
        group("Play Application") {
            row {
                comment(
                    "Optional project-specific override. Leave empty to auto-detect the Play 1 application under the opened IntelliJ project."
                )
            }
            row("Application path:") {
                cell(applicationPathField)
                    .resizableColumn()
                    .also {
                        applicationPathField.addBrowseFolderListener(
                            TextBrowseFolderListener(
                                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                    .apply { title = "Select Play 1 Application Directory" }
                            )
                        )
                    }
                button("Auto-detect") {
                    val basePath = project.basePath ?: return@button
                    val detection = Play1ProjectDetector().detect(Paths.get(basePath))
                    if (detection.isPlay1 && detection.projectRoot != null) {
                        applicationPathField.text = detection.projectRoot.toString()
                    }
                    updateStatus()
                }
                button("Validate") { updateStatus() }
            }
            row { cell(statusLabel) }
        }
    }

    init {
        reset()
    }

    fun isModified(): Boolean =
        applicationPathField.text.trim() != settings.playApplicationPath

    fun apply() {
        settings.playApplicationPath = applicationPathField.text
        Play1ProjectService.getInstance(project).scheduleRefresh("Play application path setting changed")
    }

    fun reset() {
        applicationPathField.text = settings.playApplicationPath
        updateStatus()
    }

    private fun updateStatus() {
        val path = applicationPathField.text.trim()
        if (path.isBlank()) {
            val basePath = project.basePath
            val detection = basePath?.let { Play1ProjectDetector().detect(Paths.get(it)) }
            statusLabel.text = if (detection?.isPlay1 == true && detection.projectRoot != null) {
                "Auto-detected: ${detection.projectRoot}"
            } else {
                "Auto-detection did not find a Play 1 application yet."
            }
            return
        }

        val detection = Play1ProjectDetector().detectAt(Paths.get(path))
        statusLabel.text = if (detection.isPlay1) {
            "Valid Play 1 application (${detection.matchedCriteria.joinToString()})"
        } else {
            "Not recognized as Play 1 — missing: ${detection.missingCriteria.joinToString()}"
        }
    }
}
