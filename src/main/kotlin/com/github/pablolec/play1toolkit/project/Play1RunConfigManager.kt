package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.model.RepairReport
import com.github.pablolec.play1toolkit.run.Play1RunConfigurationType
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project

object Play1RunConfigManager {

    private const val RUN_CONFIG_NAME = "Play 1 App"

    fun createRunConfiguration(project: Project, report: RepairReport) {
        val runManager = RunManager.getInstance(project)

        val existing = runManager.allSettings.find {
            it.type is Play1RunConfigurationType && it.name == RUN_CONFIG_NAME
        }

        if (existing != null) {
            report.ok("Run configuration", "already exists")
            return
        }

        val configurationType = ConfigurationTypeUtil.findConfigurationType(Play1RunConfigurationType::class.java)
        if (configurationType == null) {
            report.error("Run configuration", "Play1RunConfigurationType not found")
            return
        }

        val factory = configurationType.configurationFactories.firstOrNull()
        if (factory == null) {
            report.error("Run configuration", "Configuration factory not found")
            return
        }

        val settings = runManager.createConfiguration(RUN_CONFIG_NAME, factory)
        runManager.addConfiguration(settings)

        if (runManager.selectedConfiguration == null) {
            runManager.selectedConfiguration = settings
        }

        report.ok("Run configuration", "created")
    }
}
