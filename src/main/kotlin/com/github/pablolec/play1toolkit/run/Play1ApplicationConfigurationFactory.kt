package com.github.pablolec.play1toolkit.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

class Play1ApplicationConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "Play1Application"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        Play1ApplicationRunConfiguration(project, this, "Play v1 App")
}
