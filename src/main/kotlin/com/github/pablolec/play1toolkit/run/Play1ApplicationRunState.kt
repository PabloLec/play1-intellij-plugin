package com.github.pablolec.play1toolkit.run

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.project.Play1LibraryManager
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import java.nio.file.Paths

class Play1ApplicationRunState(
    environment: ExecutionEnvironment,
    private val config: Play1ApplicationRunConfiguration,
    private val targetModule: Module?
) : JavaCommandLineState(environment) {

    override fun createJavaParameters(): JavaParameters {
        val settings = Play1Settings.getInstance()
        val playHome = settings.playHome.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("Play Home is not configured. Go to Settings > Tools > Play v1 Toolkit.")

        val playHomePath = Paths.get(playHome)
        val frameworkDir = playHomePath.resolve("framework")
        val playJar = Play1HomeValidator.findPlayJar(frameworkDir)
            ?: throw ExecutionException("play-*.jar not found in $frameworkDir")

        val params = JavaParameters()
        params.mainClass = "play.server.Server"
        params.workingDirectory = config.applicationPath
        params.jdk = Play1RunConfigurationSupport.resolveSdk(config.project, targetModule)
            ?: throw ExecutionException(
                "No Java SDK configured for Play v1 App. Configure a module SDK or a project SDK."
            )

        // Add play jar first, then project lib/*.jar, then remaining framework libs.
        // This lets project overrides (e.g. slf4j-api 1.7.x) win over Play's bundled jars.
        params.classPath.add(playJar.toAbsolutePath().toString())
        val classpathJars = Play1LibraryManager.buildProjectClasspathJars(playHomePath, config.applicationPath)
        classpathJars.projectJars.forEach { params.classPath.add(it.toAbsolutePath().toString()) }
        classpathJars.frameworkJars.forEach { params.classPath.add(it.toAbsolutePath().toString()) }

        // VM options
        params.vmParametersList.add("-Dapplication.path=${config.applicationPath}")
        params.vmParametersList.add("-Dplay.id=${config.playId}")
        params.vmParametersList.add("-Dhttp.port=${config.httpPort}")
        params.env = config.envVars

        if (config.jvmOptions.isNotBlank()) {
            Play1RunConfigurationSupport.applyJvmOptions(params, config.jvmOptions)
        }

        if (Play1RunConfigurationSupport.isDebugExecutor(environment)) {
            Play1RunConfigurationSupport.configureDebugRunnerSettings(environment.runnerSettings, config.debugPort)
        }

        return params
    }

    override fun createRemoteConnection(environment: ExecutionEnvironment): RemoteConnection? {
        return if (Play1RunConfigurationSupport.isDebugExecutor(environment)) {
            Play1RunConfigurationSupport.createDebugRemoteConnection(config.debugPort)
        } else {
            super.createRemoteConnection(environment)
        }
    }
}
