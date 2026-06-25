package com.github.pablolec.play1toolkit.config

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class Play1ProjectSettingsConfigurable(private val project: Project) : Configurable {

    private var panel: Play1ProjectSettingsPanel? = null

    override fun getDisplayName(): String = "Project"

    override fun createComponent(): JComponent {
        panel = Play1ProjectSettingsPanel(project)
        return panel!!.component
    }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
    }

    override fun reset() {
        panel?.reset()
    }

    override fun disposeUIResources() {
        panel = null
    }
}
