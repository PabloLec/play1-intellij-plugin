package com.github.pablolec.play1toolkit.playconfig.settings

import com.github.pablolec.play1toolkit.playconfig.model.PlayConfigWrapperMethod
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class PlayConfigConfigurable(private val project: Project) : Configurable {

    private var panel: PlayConfigSettingsPanel? = null

    override fun getDisplayName(): String = "Configuration Intelligence"

    override fun createComponent(): JComponent {
        panel = PlayConfigSettingsPanel(project)
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
