package com.github.pablolec.play1toolkit.run

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.project.Play1CliResultReason
import com.github.pablolec.play1toolkit.project.Play1PythonRuntimeResolver
import com.github.pablolec.play1toolkit.runtime.Play1ApplicationRuntimeService
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteConnectionCreator
import com.intellij.execution.configurations.RemoteState
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
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
) : CommandLineState(environment), RemoteConnectionCreator, RemoteState {

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

        val playScript = findPlayLauncher(playHomePath)
            ?: throw ExecutionException("play script not found in: $playHomePath")

        val applicationPath = Paths.get(config.applicationPath).toAbsolutePath().normalize()
        val debug = Play1RunConfigurationSupport.isDebugExecutor(environment)
        val command = GeneralCommandLine(buildCommand(playScript, applicationPath, debug))
            .withWorkDirectory(applicationPath.parent?.toString() ?: config.applicationPath)
            .withEnvironment(config.envVars)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        command.withEnvironment(Play1RunConfigurationSupport.buildJavaSdkEnvironment(sdkHomePath, command.environment))
        applyJavaOpts(command, debug)

        val handler = KillableProcessHandler(command)
        installOutputLogFile(handler)
        handler.addProcessListener(debugEnvironmentReporter(command))
        val runtimeService = Play1ApplicationRuntimeService.getInstance(environment.project)
        val sessionId = runtimeService.processStarted(config.name, config.httpPort)
        runtimeService.monitorProcess(
            sessionId = sessionId,
            isRunning = { !handler.isProcessTerminated && !handler.isProcessTerminating },
            exitCode = { handler.exitCode },
        )
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                runtimeService.processTerminated(sessionId, event.exitCode)
            }
        })
        return handler
    }

    private fun installOutputLogFile(handler: ProcessHandler) {
        if (!config.mirrorOutputToFile) {
            return
        }

        val path = try {
            Play1OutputLogFile.resolve(config.outputLogPath, config.name)
        } catch (e: Exception) {
            handler.addProcessListener(startupMessageReporter("Play v1 Toolkit could not resolve output log file: ${e.message}\n"))
            return
        }

        val listener = try {
            Play1OutputLogFileListener.create(path)
        } catch (e: Exception) {
            handler.addProcessListener(
                startupMessageReporter("Play v1 Toolkit could not open output log file $path: ${e.message}\n")
            )
            return
        }

        handler.addProcessListener(listener)
        handler.addProcessListener(startupMessageReporter("Play v1 Toolkit mirrors console output to $path\n"))
    }

    private fun buildCommand(playScript: Path, applicationPath: Path, debug: Boolean): List<String> = buildList {
        addAll(buildLauncherCommand(playScript))
        add("run")
        add(applicationPath.fileName?.toString() ?: applicationPath.toString())
        config.getActiveProfile()?.let { add("--%$it") }
        add("--http.port=${config.httpPort}")
        if (debug) {
            add("--jpda.port=${config.debugPort}")
        }
    }

    private fun buildLauncherCommand(playScript: Path): List<String> {
        val scriptFile = playScript.toFile()
        if (Play1PythonRuntimeResolver.isPythonLauncher(scriptFile)) {
            val resolution = Play1PythonRuntimeResolver.resolve(
                script = scriptFile,
                indicator = null,
                onLine = { _, _ -> },
            )
            val commandPrefix = resolution.commandPrefix
                ?: throw ExecutionException(
                    when (resolution.reason) {
                        Play1CliResultReason.MANAGED_RUNTIME_UNAVAILABLE ->
                            "Could not provision ${resolution.description ?: "a managed Python runtime"}: ${resolution.detail ?: "unknown error"}"
                        else ->
                            "Could not find a compatible Python runtime for $playScript"
                    }
                )
            return commandPrefix + playScript.toString()
        }

        return if (isWindowsBatch(playScript)) {
            listOf("cmd.exe", "/c", playScript.toString())
        } else {
            listOf(playScript.toString())
        }
    }

    private fun findPlayLauncher(playHome: Path): Path? {
        val unixScript = playHome.resolve("play")
        if (Files.isRegularFile(unixScript)) return unixScript
        val windowsScript = playHome.resolve("play.bat")
        if (Files.isRegularFile(windowsScript)) return windowsScript
        val windowsCommandScript = playHome.resolve("play.cmd")
        if (Files.isRegularFile(windowsCommandScript)) return windowsCommandScript
        return null
    }

    private fun isWindowsBatch(script: Path): Boolean {
        val name = script.fileName.toString().lowercase()
        return System.getProperty("os.name").lowercase().contains("win") &&
            (name.endsWith(".bat") || name.endsWith(".cmd"))
    }

    override fun createRemoteConnection(environment: ExecutionEnvironment): RemoteConnection =
        getRemoteConnection()

    override fun getRemoteConnection(): RemoteConnection =
        RemoteConnection(
            true,
            "127.0.0.1",
            config.debugPort.toString(),
            false,
        )

    override fun isPollConnection(): Boolean = true

    private fun debugEnvironmentReporter(command: GeneralCommandLine): ProcessListener =
        object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                if (!Play1RunConfigurationSupport.isDebugExecutor(environment)) return
                event.processHandler.notifyTextAvailable(
                    buildString {
                        appendLine("Play v1 Toolkit debug environment")
                        appendLine("  Command=${command.commandLineString}")
                        appendLine("  Debug transport=Play native JPDA (--jpda.port=${config.debugPort})")
                        appendLine("  JAVA_HOME=${command.environment["JAVA_HOME"] ?: "not set"}")
                        appendLine("  JAVA_OPTS=${command.environment["JAVA_OPTS"] ?: "not set"}")
                        appendLine("  IntelliJ debugger attaches to 127.0.0.1:${config.debugPort}")
                    },
                    ProcessOutputTypes.SYSTEM,
                )
            }
        }

    private fun startupMessageReporter(message: String): ProcessListener =
        object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                event.processHandler.notifyTextAvailable(message, ProcessOutputTypes.SYSTEM)
            }
        }

    private fun resolveSdkHomePath(sdk: Sdk): String {
        val sdkHomePath = sdk.homePath?.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("The configured Java SDK has no home path. Configure a valid module SDK or project SDK.")
        if (!Files.isDirectory(Paths.get(sdkHomePath))) {
            throw ExecutionException("The configured Java SDK home does not exist: $sdkHomePath")
        }
        return sdkHomePath
    }

    private fun applyJavaOpts(command: GeneralCommandLine, debug: Boolean) {
        val parts = mutableListOf<String>()
        command.environment["JAVA_OPTS"]
            ?.takeIf { it.isNotBlank() }
            ?.let { javaOpts ->
                parts.add(if (debug) Play1RunConfigurationSupport.removeDebugJvmOptions(javaOpts) else javaOpts)
            }
        if (!debug && command.environment["JAVA_OPTS"].isNullOrBlank()) {
            System.getenv("JAVA_OPTS")?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        config.jvmOptions
            .takeIf { it.isNotBlank() }
            ?.let { jvmOptions ->
                parts.add(if (debug) Play1RunConfigurationSupport.removeDebugJvmOptions(jvmOptions) else jvmOptions)
            }

        val javaOpts = parts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (javaOpts.isNotBlank()) {
            command.environment["JAVA_OPTS"] = javaOpts
        } else {
            command.environment.remove("JAVA_OPTS")
        }
    }
}
