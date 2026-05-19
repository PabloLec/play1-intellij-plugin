package com.github.pablolec.play1toolkit.config

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class Play1SettingsConfigurable : Configurable {

    private var panel: Play1SettingsPanel? = null

    override fun getDisplayName(): String = "Play v1 Toolkit"

    override fun createComponent(): JComponent {
        panel = Play1SettingsPanel()
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
