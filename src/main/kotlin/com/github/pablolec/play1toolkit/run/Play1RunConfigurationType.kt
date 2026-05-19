package com.github.pablolec.play1toolkit.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class Play1RunConfigurationType : ConfigurationType {

    private val factory = Play1ApplicationConfigurationFactory(this)

    override fun getDisplayName(): String = "Play v1 Application"
    override fun getConfigurationTypeDescription(): String = "Run a Play Framework 1.x application"
    override fun getIcon(): Icon = AllIcons.RunConfigurations.Application
    override fun getId(): String = "PLAY1_APPLICATION"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}
