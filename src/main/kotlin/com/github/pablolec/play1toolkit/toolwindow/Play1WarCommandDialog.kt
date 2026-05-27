package com.github.pablolec.play1toolkit.toolwindow

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Paths
import javax.swing.JComponent

class Play1WarCommandDialog(project: Project) : DialogWrapper(project) {

    private val outputField = TextFieldWithBrowseButton()
    private val zipCheckbox = JBCheckBox("Also create zipped .war archive")

    val outputPath: String
        get() = outputField.text.trim()

    val zipAsWar: Boolean
        get() = zipCheckbox.isSelected

    init {
        title = "Build Play v1 WAR"
        val basePath = project.basePath.orEmpty()
        if (basePath.isNotBlank()) {
            val projectDir = Paths.get(basePath)
            outputField.text = projectDir.resolveSibling("${projectDir.fileName}-war").toString()
        }
        outputField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                    title = "WAR Output Directory"
                    description = "Choose a directory outside the project where the WAR structure will be generated."
                },
                project
            )
        )
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Output directory") { cell(outputField).resizableColumn() }
        row { cell(zipCheckbox) }
    }

    override fun doValidate(): ValidationInfo? {
        val path = outputPath
        if (path.isBlank()) {
            return ValidationInfo("Output directory is required", outputField)
        }
        return null
    }
}
