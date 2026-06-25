package com.github.pablolec.play1toolkit.run

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class Play1RunConfigurationEditor : SettingsEditor<Play1ApplicationRunConfiguration>() {

    private val applicationPathField = JBTextField()
    private val playIdCombo = ComboBox<String>()
    private val httpPortField = JBTextField()
    private val debugPortField = JBTextField()
    private val jvmOptionsField = JBTextField()
    private val envVarsComponent = EnvironmentVariablesComponent()

    private val form = panel {
        row("Application path:") {
            cell(applicationPathField).resizableColumn()
        }
        row("Profile:") {
            cell(playIdCombo).comment("Passed to Play as --%profile; leave empty for default configuration")
        }
        row("HTTP port:") {
            cell(httpPortField)
        }
        row("Debug port:") {
            cell(debugPortField)
        }
        row("JVM options:") {
            cell(jvmOptionsField).resizableColumn()
        }
        row {
            cell(envVarsComponent.component).resizableColumn().label("Environment variables:")
        }
    }

    override fun resetEditorFrom(config: Play1ApplicationRunConfiguration) {
        applicationPathField.text = config.applicationPath

        val profiles = mutableListOf("")
        try {
            val svc = com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
                .getInstance(config.project)
            profiles.addAll(svc.availableProfiles())
        } catch (e: Exception) { /* no project context yet */ }
        if (config.playId.isNotBlank() && config.playId !in profiles) {
            profiles.add(config.playId)
        }
        playIdCombo.model = DefaultComboBoxModel(profiles.toTypedArray())
        playIdCombo.isEditable = true
        playIdCombo.selectedItem = config.playId

        httpPortField.text = config.httpPort.toString()
        debugPortField.text = config.debugPort.toString()
        jvmOptionsField.text = config.jvmOptions
        envVarsComponent.envs = config.envVars
    }

    override fun applyEditorTo(config: Play1ApplicationRunConfiguration) {
        config.applicationPath = applicationPathField.text
        config.playId = (playIdCombo.selectedItem as? String) ?: ""
        config.httpPort = httpPortField.text.toIntOrNull() ?: 9000
        config.debugPort = debugPortField.text.toIntOrNull() ?: 5005
        config.jvmOptions = jvmOptionsField.text
        config.envVars = envVarsComponent.envs
    }

    override fun createEditor(): JComponent = form
}
