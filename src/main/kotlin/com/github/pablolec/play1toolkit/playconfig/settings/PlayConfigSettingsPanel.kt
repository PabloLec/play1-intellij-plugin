package com.github.pablolec.play1toolkit.playconfig.settings

import com.github.pablolec.play1toolkit.playconfig.model.PlayConfigWrapperMethod
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

class PlayConfigSettingsPanel(private val project: Project) {

    private val settings = PlayConfigProjectSettings.getInstance(project)
    private val profileCombo = ComboBox<String>()
    private val prefixesField = JBTextField()

    private val wrapperTableModel = DefaultTableModel(
        arrayOf<Any>("Class (FQN)", "Method", "Key Arg Index"), 0
    )
    private val wrapperTable = JBTable(wrapperTableModel)

    val component: JComponent = buildPanel()

    private fun buildPanel(): JComponent {
        val wrapperDecorator = ToolbarDecorator.createDecorator(wrapperTable)
            .setAddAction { addWrapper() }
            .setRemoveAction { removeSelectedWrapper() }
            .createPanel()
        wrapperDecorator.preferredSize = Dimension(600, 150)

        return panel {
            group("Active Framework Profile") {
                row("Active profile:") {
                    cell(profileCombo).comment("Profile used for config resolution (e.g. docker, prod, dev)")
                }
            }
            group("Custom Config Wrappers") {
                row {
                    cell(wrapperDecorator).label("Methods consuming a Play config key:").align(Align.FILL)
                }.resizableRow()
            }
            group("Additional Known Key Prefixes") {
                row("Extra prefixes (comma-separated):") {
                    cell(prefixesField).resizableColumn()
                        .comment("e.g. myapp., legacy.config.")
                }
            }
        }
    }

    private fun addWrapper() {
        wrapperTableModel.addRow(arrayOf<Any>("com.example.Config", "getString", 0))
    }

    private fun removeSelectedWrapper() {
        val row = wrapperTable.selectedRow
        if (row >= 0) wrapperTableModel.removeRow(row)
    }

    fun reset() {
        val profiles = mutableListOf<String>("")
        try {
            val svc = PlayConfigService.getInstance(project)
            profiles.addAll(svc.availableProfiles())
        } catch (e: Exception) { /* no project context */ }

        profileCombo.model = DefaultComboBoxModel<String>(profiles.toTypedArray())
        profileCombo.selectedItem = settings.activeFrameworkId.ifBlank { "" }

        wrapperTableModel.rowCount = 0
        settings.wrapperMethods.forEach { m ->
            wrapperTableModel.addRow(arrayOf<Any>(m.fqClassName, m.methodName, m.keyArgIndex))
        }

        prefixesField.text = settings.additionalKnownKeyPrefixes.joinToString(", ")
    }

    fun apply() {
        settings.activeFrameworkId = (profileCombo.selectedItem as? String) ?: ""

        val wrappers = mutableListOf<PlayConfigWrapperMethod>()
        for (i in 0 until wrapperTableModel.rowCount) {
            val cls = wrapperTableModel.getValueAt(i, 0) as? String ?: continue
            val meth = wrapperTableModel.getValueAt(i, 1) as? String ?: continue
            val idx = when (val raw = wrapperTableModel.getValueAt(i, 2)) {
                is Int -> raw
                is String -> raw.toIntOrNull() ?: 0
                else -> 0
            }
            if (cls.isNotBlank() && meth.isNotBlank()) {
                wrappers.add(PlayConfigWrapperMethod(cls, meth, idx))
            }
        }
        settings.setWrapperMethods(wrappers)

        val prefixes = prefixesField.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        settings.setAdditionalPrefixes(prefixes)
    }

    fun isModified(): Boolean {
        val currentProfile = (profileCombo.selectedItem as? String) ?: ""
        if (currentProfile != settings.activeFrameworkId) return true
        val currentPrefixes = prefixesField.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return currentPrefixes != settings.additionalKnownKeyPrefixes
    }
}
