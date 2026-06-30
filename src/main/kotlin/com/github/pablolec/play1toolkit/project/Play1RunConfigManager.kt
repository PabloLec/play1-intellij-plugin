package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.model.RepairReport
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.github.pablolec.play1toolkit.run.Play1ApplicationRunConfiguration
import com.github.pablolec.play1toolkit.run.Play1RunConfigurationSupport
import com.github.pablolec.play1toolkit.run.Play1RunConfigurationType
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import javax.swing.SwingUtilities

object Play1RunConfigManager {

    private const val RUN_CONFIG_NAME = "Play v1 App"

    fun createRunConfiguration(project: Project, report: RepairReport, applicationPath: String? = project.basePath) {
        if (!SwingUtilities.isEventDispatchThread()) {
            ApplicationManager.getApplication().invokeAndWait {
                createRunConfiguration(project, report, applicationPath)
            }
            return
        }

        val runManager = RunManager.getInstance(project)

        val availableProfiles = availableProfiles(project)
        val preferredProfile = Play1RunConfigurationSupport.selectInitialProfile(
            configuredDefault = Play1Settings.getInstance().defaultPlayId,
            availableProfiles = availableProfiles,
        )

        val existing = runManager.allSettings.find {
            it.type is Play1RunConfigurationType && it.name == RUN_CONFIG_NAME
        }

        if (existing != null) {
            (existing.configuration as? Play1ApplicationRunConfiguration)?.let { configuration ->
                maybeUpdateProfile(configuration, preferredProfile, availableProfiles)
            }
            report.ok("Run configuration", "already exists")
            return
        }

        val configurationType = ConfigurationTypeUtil.findConfigurationType(Play1RunConfigurationType::class.java)
        val factory = configurationType.configurationFactories.firstOrNull()
        if (factory == null) {
            report.error("Run configuration", "Configuration factory not found")
            return
        }

        val settings = runManager.createConfiguration(RUN_CONFIG_NAME, factory)
        (settings.configuration as? Play1ApplicationRunConfiguration)?.let { configuration ->
            configuration.applicationPath = applicationPath ?: project.basePath.orEmpty()
            configuration.playId = preferredProfile
        }
        runManager.addConfiguration(settings)

        if (runManager.selectedConfiguration == null) {
            runManager.selectedConfiguration = settings
        }

        report.ok("Run configuration", "created")
    }

    private fun availableProfiles(project: Project): List<String> = try {
        PlayConfigService.getInstance(project).availableProfiles()
    } catch (_: Exception) {
        emptyList()
    }

    private fun maybeUpdateProfile(
        configuration: Play1ApplicationRunConfiguration,
        preferredProfile: String,
        availableProfiles: Collection<String>,
    ) {
        if (preferredProfile.isBlank() || configuration.playId == preferredProfile) return
        val currentProfile = configuration.playId.trim()
        val configuredDefault = Play1Settings.getInstance().defaultPlayId.trim()
        val currentLooksLikeDefault = currentProfile.isBlank() || currentProfile == configuredDefault
        val currentIsKnown = currentProfile in availableProfiles
        if (currentLooksLikeDefault && !currentIsKnown) {
            configuration.playId = preferredProfile
        }
    }
}
