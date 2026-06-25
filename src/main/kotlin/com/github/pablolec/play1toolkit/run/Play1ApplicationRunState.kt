package com.github.pablolec.play1toolkit.run

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
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

        applyJavaOpts(command)

        return KillableProcessHandler(command)
    }

    private fun buildCommand(playScript: String, applicationPath: Path): List<String> = buildList {
        add(playScript)
        add("run")
        add(applicationPath.fileName?.toString() ?: applicationPath.toString())
        config.getActiveProfile()?.let { add("--%$it") }
        add("--http.port=${config.httpPort}")
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
