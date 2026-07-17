package com.github.pablolec.play1toolkit.config

import com.github.pablolec.play1toolkit.detection.Play1ProjectDetector
import com.github.pablolec.play1toolkit.services.Play1ProjectService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
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
                button("Find Play app…") {
                    findDeepCandidates()
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
                "Auto-detection did not find a Play 1 application. Use Find Play app… to scan deeper."
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

    private fun findDeepCandidates() {
        val basePath = project.basePath ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Finding Play 1 Applications", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Scanning project folders..."
                val detection = Play1ProjectDetector().detectDeep(Paths.get(basePath))
                val validCandidates = detection.candidates
                    .filter { it.score >= Play1ProjectDetector.REQUIRED_SCORE }

                ApplicationManager.getApplication().invokeLater {
                    showDeepCandidates(basePath, validCandidates)
                }
            }
        })
    }

    private fun showDeepCandidates(basePath: String, validCandidates: List<Play1ProjectDetector.Candidate>) {
        when {
            validCandidates.isEmpty() -> {
                Messages.showInfoMessage(
                    project,
                    "No Play 1 application was found under the opened IntelliJ project.",
                    "Play Application Not Found",
                )
            }
            validCandidates.size == 1 -> {
                applicationPathField.text = validCandidates.single().root.toString()
                updateStatus()
            }
            else -> {
                val labels = validCandidates.map { candidate ->
                    "${basePath.relativize(candidate.root)}  (${candidate.matchedCriteria.joinToString()})"
                }.toTypedArray()
                val selected = Messages.showEditableChooseDialog(
                    "Select the Play 1 application to use for this IntelliJ project.",
                    "Select Play Application",
                    Messages.getQuestionIcon(),
                    labels,
                    labels.first(),
                    null,
                ) ?: return
                val selectedIndex = labels.indexOf(selected)
                if (selectedIndex >= 0) {
                    applicationPathField.text = validCandidates[selectedIndex].root.toString()
                    updateStatus()
                }
            }
        }
    }

    private fun String.relativize(path: java.nio.file.Path): String {
        return try {
            Paths.get(this).relativize(path).toString()
        } catch (_: Exception) {
            path.toString()
        }
    }
}
