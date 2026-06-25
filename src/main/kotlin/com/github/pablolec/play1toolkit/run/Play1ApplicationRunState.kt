package com.github.pablolec.play1toolkit.run

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.runtime.Play1ApplicationRuntimeService
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Play1ApplicationRunState(
    environment: ExecutionEnvironment,
    private val config: Play1ApplicationRunConfiguration,
    @Suppress("unused") private val targetModule: Module?
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val settings = Play1Settings.getInstance()
        val playHome = settings.playHome.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("Play Home is not configured. Go to Settings > Tools > Play v1 Toolkit.")
        val sdk = Play1RunConfigurationSupport.resolveSdk(environment.project, targetModule)
            ?: throw ExecutionException("No Java SDK configured for Play v1 App. Configure a module SDK or a project SDK.")
        val sdkHomePath = resolveSdkHomePath(sdk)

        val playHomePath = Paths.get(playHome)
        val validation = Play1HomeValidator.validate(playHomePath)
        if (!validation.valid) {
            throw ExecutionException(validation.error ?: "Invalid Play Home: $playHome")
        }

        val playScript = playHomePath.resolve("play")
        if (!Files.isRegularFile(playScript)) {
            throw ExecutionException("play script not found: $playScript")
        }

        val applicationPath = Paths.get(config.applicationPath).toAbsolutePath().normalize()
        val command = GeneralCommandLine(buildCommand(playScript.toString(), applicationPath))
            .withWorkDirectory(applicationPath.parent?.toString() ?: config.applicationPath)
            .withEnvironment(config.envVars)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        command.withEnvironment(Play1RunConfigurationSupport.buildJavaSdkEnvironment(sdkHomePath, command.environment))
        applyJavaOpts(command)

        val handler = KillableProcessHandler(command)
        val runtimeService = Play1ApplicationRuntimeService.getInstance(environment.project)
        val sessionId = runtimeService.processStarted(config.name, config.httpPort)
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                runtimeService.processTerminated(sessionId, event.exitCode)
            }
        })
        return handler
    }

    private fun buildCommand(playScript: String, applicationPath: Path): List<String> = buildList {
        add(playScript)
        add("run")
        add(applicationPath.fileName?.toString() ?: applicationPath.toString())
        config.getActiveProfile()?.let { add("--%$it") }
        add("--http.port=${config.httpPort}")
    }

    private fun resolveSdkHomePath(sdk: Sdk): String {
        val sdkHomePath = sdk.homePath?.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("The configured Java SDK has no home path. Configure a valid module SDK or project SDK.")
        if (!Files.isDirectory(Paths.get(sdkHomePath))) {
            throw ExecutionException("The configured Java SDK home does not exist: $sdkHomePath")
        }
        return sdkHomePath
    }

    private fun applyJavaOpts(command: GeneralCommandLine) {
        val parts = mutableListOf<String>()
        command.environment["JAVA_OPTS"]?.takeIf { it.isNotBlank() }?.let(parts::add)
        if (command.environment["JAVA_OPTS"].isNullOrBlank()) {
            System.getenv("JAVA_OPTS")?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        config.jvmOptions.takeIf { it.isNotBlank() }?.let(parts::add)
        if (Play1RunConfigurationSupport.isDebugExecutor(environment)) {
            parts.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${config.debugPort}")
        }
        if (parts.isNotEmpty()) {
            command.environment["JAVA_OPTS"] = parts.joinToString(" ")
        }
    }
}
