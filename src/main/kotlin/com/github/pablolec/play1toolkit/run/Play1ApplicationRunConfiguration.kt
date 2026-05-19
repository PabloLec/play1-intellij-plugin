package com.github.pablolec.play1toolkit.run

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jdom.Element

class Play1ApplicationRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<Element>(project, factory, name), ModuleRunProfile, RunConfigurationWithRunnerSettings {

    var applicationPath: String = project.basePath ?: ""
    var playId: String = Play1Settings.getInstance().defaultPlayId
    var httpPort: Int = Play1Settings.getInstance().defaultHttpPort
    var debugPort: Int = Play1Settings.getInstance().defaultDebugPort
    var jvmOptions: String = ""
    var envVars: Map<String, String> = emptyMap()

    override fun getConfigurationEditor(): com.intellij.openapi.options.SettingsEditor<out RunConfiguration> =
        Play1RunConfigurationEditor()

    override fun isSettingsNeeded(): Boolean = true

    override fun getModules(): Array<Module> =
        Play1RunConfigurationSupport.resolveModule(project, applicationPath)?.let { arrayOf(it) } ?: emptyArray()

    override fun getSearchScope(): GlobalSearchScope =
        Play1RunConfigurationSupport.resolveSearchScope(project, applicationPath)

    override fun checkConfiguration() {
        if (applicationPath.isBlank()) {
            throw RuntimeConfigurationError("Application path is not set")
        }
        Play1RunConfigurationSupport.validatePorts(httpPort, debugPort)
        val settings = Play1Settings.getInstance()
        if (settings.playHome.isBlank()) {
            throw RuntimeConfigurationWarning("Play Home is not configured. Go to Settings > Tools > Play 1 Toolkit.")
        }
        val module = Play1RunConfigurationSupport.resolveModule(project, applicationPath)
        val sdk = Play1RunConfigurationSupport.resolveSdk(project, module)
        if (sdk == null) {
            throw RuntimeConfigurationError(
                "No Java SDK configured for Play 1 App. Configure a module SDK or a project SDK."
            )
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        Play1ApplicationRunState(
            environment = environment,
            config = this,
            targetModule = Play1RunConfigurationSupport.resolveModule(project, applicationPath)
        )

    override fun readExternal(element: Element) {
        super<RunConfigurationBase>.readExternal(element)
        applicationPath = element.getAttributeValue("applicationPath") ?: project.basePath ?: ""
        playId = element.getAttributeValue("playId") ?: "dev"
        httpPort = element.getAttributeValue("httpPort")?.toIntOrNull() ?: 9000
        debugPort = element.getAttributeValue("debugPort")?.toIntOrNull() ?: 5005
        jvmOptions = element.getAttributeValue("jvmOptions") ?: ""
    }

    override fun writeExternal(element: Element) {
        super<RunConfigurationBase>.writeExternal(element)
        element.setAttribute("applicationPath", applicationPath)
        element.setAttribute("playId", playId)
        element.setAttribute("httpPort", httpPort.toString())
        element.setAttribute("debugPort", debugPort.toString())
        element.setAttribute("jvmOptions", jvmOptions)
    }
}
