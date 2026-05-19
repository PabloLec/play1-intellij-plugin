package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

object Play1CliRunner {

    fun describeRuntime(playHome: String): String {
        val script = Paths.get(playHome, "play").toFile()
        if (!script.exists()) return "play script not found"
        return if (isPythonScript(script)) {
            if (requiresPython2(script)) {
                findPython2()?.let { "Python 2 ($it)" }
                    ?: Play1ManagedPythonRuntime.detectArtifactForCurrentPlatform()?.let {
                        "Managed PyPy 2.7 fallback"
                    }
                    ?: "Python 2 unavailable"
            } else {
                findPython3()?.let { "Python 3 ($it)" }
                    ?: findPython()?.let { "python ($it)" }
                    ?: "Python 3 unavailable"
            }
        } else {
            "native play script"
        }
    }

    fun plan(
        request: Play1CliRequest,
        projectPath: String,
        playHome: String,
        projectPlayVersion: String? = null,
        depsPlayHome: String = Play1Settings.getInstance().depsPlayHome,
    ): Play1CliCommandPlan {
        val playHomePath = Paths.get(playHome)
        val validation = Play1HomeValidator.validate(playHomePath)
        if (!validation.valid) {
            return Play1CliCommandPlan(
                request = request,
                available = false,
                message = validation.error ?: "Invalid Play Home",
                reason = Play1CliResultReason.PLAY_HOME_INVALID,
            )
        }

        val projectVersion = projectPlayVersion ?: validation.playVersion
        val effectiveHomeValidation = resolveEffectiveHomeForPlan(request, playHome, projectVersion, depsPlayHome)
            ?: return unsupportedDepsPlan(request, projectVersion)

        val effectiveHome = effectiveHomeValidation.first
        val effectiveVersion = effectiveHomeValidation.second.playVersion
        val managedDownloadPending = !effectiveHomeValidation.second.valid

        if (request.commandId == Play1CliCommandId.DEPS && managedDownloadPending) {
            return Play1CliCommandPlan(
                request = request,
                available = true,
                message = "Ready",
                effectivePlayHome = effectiveHome,
                effectivePlayVersion = Play1VersionDownloader.RECOMMENDED_FOR_DEPS.version,
                commandName = "deps",
                args = listOf("deps"),
                runtimeDescription = "Managed Play ${Play1VersionDownloader.RECOMMENDED_FOR_DEPS.version} download on first run",
                requiredPythonMajor = 2,
            )
        }

        val capabilities = Play1CliCapabilitiesDetector.detect(Paths.get(effectiveHome))
        val commandName = Play1CliCapabilitiesDetector.resolveCommandName(request.commandId, capabilities.commands, effectiveVersion)
            ?: return Play1CliCommandPlan(
                request = request,
                available = false,
                message = "${request.commandId.displayName} is not supported by Play ${effectiveVersion ?: "unknown"}",
                reason = Play1CliResultReason.COMMAND_UNSUPPORTED,
                effectivePlayHome = effectiveHome,
                effectivePlayVersion = effectiveVersion,
                runtimeDescription = describeRuntime(effectiveHome),
            )

        when (request.commandId) {
            Play1CliCommandId.DEPS -> {
                val depsFile = Paths.get(projectPath, "conf", "dependencies.yml")
                if (!depsFile.toFile().exists()) {
                    return Play1CliCommandPlan(
                        request = request,
                        available = false,
                        message = "conf/dependencies.yml not found",
                        reason = Play1CliResultReason.DEPENDENCIES_FILE_MISSING,
                        effectivePlayHome = effectiveHome,
                        effectivePlayVersion = effectiveVersion,
                        runtimeDescription = describeRuntime(effectiveHome),
                    )
                }

                val libDir = Paths.get(projectPath, "lib")
                val alreadyHasJars = Files.isDirectory(libDir) &&
                    Files.list(libDir).use { it.anyMatch { f -> f.toString().endsWith(".jar") } }
                if (alreadyHasJars) {
                    return Play1CliCommandPlan(
                        request = request,
                        available = false,
                        message = "lib/ already contains JARs — skipping",
                        reason = Play1CliResultReason.LIB_ALREADY_POPULATED,
                        effectivePlayHome = effectiveHome,
                        effectivePlayVersion = effectiveVersion,
                        runtimeDescription = describeRuntime(effectiveHome),
                    )
                }
            }
            Play1CliCommandId.WAR -> {
                val warOutput = request.warOutputPath?.trim().orEmpty()
                if (warOutput.isEmpty()) {
                    return Play1CliCommandPlan(
                        request = request,
                        available = false,
                        message = "WAR output path is required",
                        reason = Play1CliResultReason.INVALID_COMMAND_OPTIONS,
                        effectivePlayHome = effectiveHome,
                        effectivePlayVersion = effectiveVersion,
                        runtimeDescription = describeRuntime(effectiveHome),
                    )
                }
                if (isParentOf(projectPath, warOutput)) {
                    return Play1CliCommandPlan(
                        request = request,
                        available = false,
                        message = "WAR output path must be outside the project directory",
                        reason = Play1CliResultReason.INVALID_COMMAND_OPTIONS,
                        effectivePlayHome = effectiveHome,
                        effectivePlayVersion = effectiveVersion,
                        runtimeDescription = describeRuntime(effectiveHome),
                    )
                }
            }
            else -> Unit
        }

        val args = buildArgs(request, commandName)
        val playScript = Paths.get(effectiveHome, "play").toFile()
        return if (isPythonScript(playScript)) {
            val requiredPythonMajor = if (requiresPython2(playScript)) 2 else 3
            val runtime = describeRuntime(effectiveHome)
            Play1CliCommandPlan(
                request = request,
                available = true,
                message = "Ready",
                effectivePlayHome = effectiveHome,
                effectivePlayVersion = effectiveVersion,
                commandName = commandName,
                args = args,
                runtimeDescription = runtime,
                requiredPythonMajor = requiredPythonMajor,
            )
        } else {
            Play1CliCommandPlan(
                request = request,
                available = true,
                message = "Ready",
                effectivePlayHome = effectiveHome,
                effectivePlayVersion = effectiveVersion,
                commandName = commandName,
                args = args,
                runtimeDescription = "native play script",
            )
        }
    }

    fun run(
        request: Play1CliRequest,
        projectPath: String,
        playHome: String,
        projectPlayVersion: String? = null,
        indicator: ProgressIndicator? = null,
        onLine: (line: String, isError: Boolean) -> Unit = { _, _ -> },
        onProcessStarted: (Process) -> Unit = {},
        shouldStop: () -> Boolean = { false },
    ): Play1CliResult {
        val plan = if (request.commandId == Play1CliCommandId.DEPS) {
            val materialized = materializeDepsHome(playHome, projectPlayVersion, indicator, onLine)
                ?: return Play1CliResult(
                    request = request,
                    success = false,
                    skipped = true,
                    message = "could not provision a managed Play 1.2+ home for dependency resolution",
                    reason = Play1CliResultReason.MANAGED_PLAY_HOME_UNAVAILABLE,
                    detail = "Failed to download or validate Play ${Play1VersionDownloader.RECOMMENDED_FOR_DEPS.version}",
                )
            plan(
                request = request,
                projectPath = projectPath,
                playHome = playHome,
                projectPlayVersion = projectPlayVersion,
                depsPlayHome = materialized,
            )
        } else {
            plan(request, projectPath, playHome, projectPlayVersion)
        }
        if (!plan.available) {
            return Play1CliResult(
                request = request,
                success = false,
                skipped = true,
                message = plan.message,
                reason = plan.reason,
                effectivePlayHome = plan.effectivePlayHome,
                effectivePlayVersion = plan.effectivePlayVersion,
                runtimeDescription = plan.runtimeDescription,
                requiredPythonMajor = plan.requiredPythonMajor,
                detail = plan.detail,
            )
        }

        val playScript = Paths.get(plan.effectivePlayHome!!, "play").toFile()
        val commandResult = buildBaseCommand(playScript, indicator, onLine)
        val baseCommand = commandResult.command ?: return Play1CliResult(
            request = request,
            success = false,
            skipped = true,
            message = when (commandResult.reason) {
                Play1CliResultReason.MANAGED_RUNTIME_UNAVAILABLE ->
                    "could not provision a managed PyPy 2.7 runtime for the play script"
                else -> "could not find a Python interpreter for the play script"
            },
            reason = commandResult.reason,
            effectivePlayHome = plan.effectivePlayHome,
            effectivePlayVersion = plan.effectivePlayVersion,
            runtimeDescription = commandResult.runtimeDescription ?: plan.runtimeDescription,
            requiredPythonMajor = commandResult.requiredPythonMajor,
            detail = commandResult.detail,
        )

        val fullCommand = baseCommand + plan.args
        onLine("$ ${fullCommand.joinToString(" ")}", false)

        val process = try {
            ProcessBuilder(fullCommand)
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()
        } catch (ex: Exception) {
            return Play1CliResult(
                request = request,
                success = false,
                message = "failed to start: ${ex.message}",
                reason = Play1CliResultReason.START_FAILURE,
                effectivePlayHome = plan.effectivePlayHome,
                effectivePlayVersion = plan.effectivePlayVersion,
                runtimeDescription = commandResult.runtimeDescription ?: plan.runtimeDescription,
                requiredPythonMajor = commandResult.requiredPythonMajor,
                detail = ex.message,
            )
        }
        onProcessStarted(process)

        val readerThread = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { onLine(it, false) }
            }
        }.apply {
            name = "play1-cli-${request.commandId.name.lowercase()}"
            isDaemon = true
            start()
        }

        var cancelled = false
        while (process.isAlive) {
            if (shouldStop() || indicator?.isCanceled == true) {
                cancelled = true
                process.destroy()
                process.waitFor(1, TimeUnit.SECONDS)
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                break
            }
            process.waitFor(200, TimeUnit.MILLISECONDS)
        }

        val exitCode = process.waitFor()
        readerThread.join(1000)
        return if (cancelled) {
            Play1CliResult(
                request = request,
                success = false,
                skipped = true,
                message = "${request.commandId.displayName} cancelled",
                reason = Play1CliResultReason.EXECUTION_CANCELLED,
                effectivePlayHome = plan.effectivePlayHome,
                effectivePlayVersion = plan.effectivePlayVersion,
                runtimeDescription = commandResult.runtimeDescription ?: plan.runtimeDescription,
                requiredPythonMajor = commandResult.requiredPythonMajor,
            )
        } else if (exitCode == 0) {
            Play1CliResult(
                request = request,
                success = true,
                message = "completed successfully",
                effectivePlayHome = plan.effectivePlayHome,
                effectivePlayVersion = plan.effectivePlayVersion,
                runtimeDescription = commandResult.runtimeDescription ?: plan.runtimeDescription,
                requiredPythonMajor = commandResult.requiredPythonMajor,
            )
        } else {
            Play1CliResult(
                request = request,
                success = false,
                message = "${plan.commandName} exited with code $exitCode",
                effectivePlayHome = plan.effectivePlayHome,
                effectivePlayVersion = plan.effectivePlayVersion,
                runtimeDescription = commandResult.runtimeDescription ?: plan.runtimeDescription,
                requiredPythonMajor = commandResult.requiredPythonMajor,
            )
        }
    }

    private fun resolveEffectiveHome(
        request: Play1CliRequest,
        playHome: String,
        playVersion: String?,
        depsPlayHome: String,
    ): Pair<String, Play1HomeValidator.ValidationResult>? {
        if (request.commandId != Play1CliCommandId.DEPS || Play1CliCapabilitiesDetector.supportsDepCommand(playVersion)) {
            return playHome to Play1HomeValidator.validate(Paths.get(playHome))
        }

        if (depsPlayHome.isBlank()) return null
        val validation = Play1HomeValidator.validate(Paths.get(depsPlayHome))
        if (!validation.valid || !Play1CliCapabilitiesDetector.supportsDepCommand(validation.playVersion)) return null
        return depsPlayHome to validation
    }

    private fun resolveEffectiveHomeForPlan(
        request: Play1CliRequest,
        playHome: String,
        playVersion: String?,
        depsPlayHome: String,
    ): Pair<String, Play1HomeValidator.ValidationResult>? {
        resolveEffectiveHome(request, playHome, playVersion, depsPlayHome)?.let { return it }
        if (request.commandId != Play1CliCommandId.DEPS || Play1CliCapabilitiesDetector.supportsDepCommand(playVersion)) {
            return null
        }
        val managedPath = Play1VersionDownloader.cacheDir().resolve("play-${Play1VersionDownloader.RECOMMENDED_FOR_DEPS.version}")
        return managedPath.toString() to Play1HomeValidator.ValidationResult(
            valid = false,
            playVersion = Play1VersionDownloader.RECOMMENDED_FOR_DEPS.version,
            playJar = null,
            error = "Managed Play home not downloaded yet"
        )
    }

    private fun materializeDepsHome(
        playHome: String,
        playVersion: String?,
        indicator: ProgressIndicator?,
        onLine: (line: String, isError: Boolean) -> Unit,
    ): String? {
        val resolved = resolveEffectiveHome(Play1CliRequest(Play1CliCommandId.DEPS), playHome, playVersion, Play1Settings.getInstance().depsPlayHome)
        if (resolved != null) {
            return resolved.first
        }
        val progress = indicator ?: return null
        val release = Play1VersionDownloader.RECOMMENDED_FOR_DEPS
        onLine("~ Project Play ${playVersion ?: "unknown"} does not support dependency resolution.", true)
        onLine("~ Downloading managed Play ${release.version} for dependency resolution.", false)
        val path = Play1VersionDownloader.download(release, progress) ?: return null
        if (Play1Settings.getInstance().depsPlayHome.isBlank()) {
            Play1Settings.getInstance().depsPlayHome = path.toString()
        }
        return path.toString()
    }

    private fun unsupportedDepsPlan(request: Play1CliRequest, projectVersion: String?) = Play1CliCommandPlan(
        request = request,
        available = false,
        message = "play deps requires Play 1.2+ (project: ${projectVersion ?: "unknown"}). Configure a Play 1.2+ home in Settings > Play 1 Toolkit > Dependency Resolution.",
        reason = Play1CliResultReason.UNSUPPORTED_PLAY_VERSION,
    )

    private data class CommandBuildResult(
        val command: List<String>?,
        val requiredPythonMajor: Int?,
        val reason: Play1CliResultReason = Play1CliResultReason.NONE,
        val detail: String? = null,
        val runtimeDescription: String? = null,
    )

    private fun buildBaseCommand(
        playScript: File,
        indicator: ProgressIndicator?,
        onLine: (line: String, isError: Boolean) -> Unit,
    ): CommandBuildResult {
        if (!playScript.exists()) return CommandBuildResult(null, null, Play1CliResultReason.PLAY_HOME_INVALID)
        return if (isPythonScript(playScript)) {
            if (requiresPython2(playScript)) {
                val systemPython2 = findPython2()
                val managedRuntime = if (systemPython2 == null) {
                    Play1ManagedPythonRuntime.ensurePyPy2(indicator, onLine)
                } else {
                    Play1ManagedPythonRuntime.RuntimeProvisionResult(executable = null)
                }
                val interpreter = systemPython2 ?: managedRuntime.executable?.toAbsolutePath()?.toString()
                CommandBuildResult(
                    command = interpreter?.let { listOf(it, playScript.absolutePath) },
                    requiredPythonMajor = 2,
                    reason = if (interpreter == null) Play1CliResultReason.MANAGED_RUNTIME_UNAVAILABLE else Play1CliResultReason.NONE,
                    detail = managedRuntime.errorMessage,
                    runtimeDescription = if (systemPython2 != null) "Python 2 ($systemPython2)" else managedRuntime.executable?.let { "Managed PyPy 2.7 ($it)" },
                )
            } else {
                val interpreter = findPython3() ?: findPython()
                CommandBuildResult(
                    command = interpreter?.let { listOf(it, playScript.absolutePath) },
                    requiredPythonMajor = 3,
                    reason = if (interpreter == null) Play1CliResultReason.PYTHON_INTERPRETER_MISSING else Play1CliResultReason.NONE,
                    runtimeDescription = interpreter?.let { if (it.startsWith("python3")) "Python 3 ($it)" else "python ($it)" },
                )
            }
        } else {
            CommandBuildResult(
                command = listOf(playScript.absolutePath),
                requiredPythonMajor = null,
                runtimeDescription = "native play script",
            )
        }
    }

    private fun buildArgs(request: Play1CliRequest, commandName: String): List<String> = when (request.commandId) {
        Play1CliCommandId.CLEAN -> listOf(commandName)
        Play1CliCommandId.TEST -> listOf(commandName)
        Play1CliCommandId.AUTOTEST -> listOf(commandName)
        Play1CliCommandId.PRECOMPILE -> listOf(commandName)
        Play1CliCommandId.DEPS -> listOf(commandName)
        Play1CliCommandId.WAR -> buildList {
            add(commandName)
            add("--output")
            add(request.warOutputPath!!.trim())
            if (request.warZip) add("--zip")
        }
    }

    private fun isPythonScript(script: File): Boolean {
        val first = script.bufferedReader().use { it.readLine() } ?: return false
        return first.startsWith("#!") && first.contains("python")
    }

    private fun requiresPython2(script: File): Boolean {
        script.bufferedReader().use { reader ->
            repeat(60) {
                val line = reader.readLine() ?: return false
                if (line.matches(Regex(""".*\bprint\s+[^\(].*"""))) return true
                if (line.contains("print r\"") || line.contains("print u\"")) return true
            }
        }
        return false
    }

    private fun findPython2(): String? =
        listOf("python2", "python2.7", "python2.6").firstOrNull { available(it) }

    private fun findPython3(): String? =
        listOf("python3", "python3.12", "python3.11", "python3.10").firstOrNull { available(it) }

    private fun findPython(): String? = "python".takeIf { available(it) }

    private fun available(name: String) = try {
        ProcessBuilder(name, "--version").redirectErrorStream(true).start().waitFor() == 0
    } catch (_: Exception) {
        false
    }

    private fun isParentOf(projectPath: String, candidatePath: String): Boolean {
        val projectDir = Paths.get(projectPath).toAbsolutePath().normalize()
        val candidate = Paths.get(candidatePath).toAbsolutePath().normalize()
        return candidate.startsWith(projectDir)
    }
}
