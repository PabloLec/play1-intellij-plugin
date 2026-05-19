package com.github.pablolec.play1toolkit.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class Play1RunConfigurationEditor : SettingsEditor<Play1ApplicationRunConfiguration>() {

    private val applicationPathField = JBTextField()
    private val playIdField = JBTextField()
    private val httpPortField = JBTextField()
    private val debugPortField = JBTextField()
    private val jvmOptionsField = JBTextField()

    private val form = panel {
        row("Application path:") {
            cell(applicationPathField).resizableColumn()
        }
        row("Play ID:") {
            cell(playIdField)
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
    }

    override fun resetEditorFrom(config: Play1ApplicationRunConfiguration) {
        applicationPathField.text = config.applicationPath
        playIdField.text = config.playId
        httpPortField.text = config.httpPort.toString()
        debugPortField.text = config.debugPort.toString()
        jvmOptionsField.text = config.jvmOptions
    }

    override fun applyEditorTo(config: Play1ApplicationRunConfiguration) {
        config.applicationPath = applicationPathField.text
        config.playId = playIdField.text
        config.httpPort = httpPortField.text.toIntOrNull() ?: 9000
        config.debugPort = debugPortField.text.toIntOrNull() ?: 5005
        config.jvmOptions = jvmOptionsField.text
    }

    override fun createEditor(): JComponent = form
}
