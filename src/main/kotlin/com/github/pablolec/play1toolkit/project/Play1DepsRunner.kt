package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

data class DepsResult(
    val success: Boolean,
    val skipped: Boolean = false,
    val message: String = "",
    val reason: DepsResultReason = DepsResultReason.NONE,
    val requiredPythonMajor: Int? = null,
    val detail: String? = null,
)

enum class DepsResultReason {
    NONE,
    UNSUPPORTED_PLAY_VERSION,
    DEPENDENCIES_FILE_MISSING,
    LIB_ALREADY_POPULATED,
    PYTHON_INTERPRETER_MISSING,
    MANAGED_RUNTIME_UNAVAILABLE,
}

object Play1DepsRunner {

    /**
     * Runs `play deps` for the given project.
     * Requires Play 1.2+. Play 1.1.x has no dependency resolution command.
     *
     * @param projectPath  absolute path to the Play 1 project root
     * @param playHome     absolute path to the Play 1 installation
     * @param playVersion  version string extracted from the Play JAR (e.g. "1.2.7")
     * @param onLine       receives each output line as it arrives (background thread)
     */
    fun run(
        projectPath: String,
        playHome: String,
        playVersion: String? = null,
        indicator: ProgressIndicator? = null,
        onLine: (line: String, isError: Boolean) -> Unit = { _, _ -> },
    ): DepsResult {
        val effectivePlayHome: String = if (!supportsDepCommand(playVersion)) {
            val depsHome = Play1Settings.getInstance().depsPlayHome
            if (depsHome.isNotBlank()) {
                val v = Play1HomeValidator.validate(Paths.get(depsHome))
                if (v.valid && supportsDepCommand(v.playVersion)) {
                    onLine("⚠  Project Play ${playVersion ?: "unknown"} doesn't support 'play deps'.", true)
                    onLine("→  Using $depsHome (Play ${v.playVersion}) for dependency resolution.", false)
                    depsHome
                } else {
                    return DepsResult(
                        success = false, skipped = true,
                        message = "play deps requires Play 1.2+ (project: ${playVersion ?: "unknown"}). " +
                            "Configure a Play 1.2+ home in Settings > Play 1 Toolkit > Dependency Resolution.",
                        reason = DepsResultReason.UNSUPPORTED_PLAY_VERSION,
                    )
                }
            } else {
                return DepsResult(
                    success = false, skipped = true,
                    message = "play deps requires Play 1.2+ (project: ${playVersion ?: "unknown"}). " +
                        "Configure a Play 1.2+ home in Settings > Play 1 Toolkit > Dependency Resolution.",
                    reason = DepsResultReason.UNSUPPORTED_PLAY_VERSION,
                )
            }
        } else {
            playHome
        }

        val depsFile = Paths.get(projectPath, "conf", "dependencies.yml")
        if (!depsFile.toFile().exists()) {
            return DepsResult(
                success = false,
                skipped = true,
                message = "conf/dependencies.yml not found",
                reason = DepsResultReason.DEPENDENCIES_FILE_MISSING,
            )
        }

        val libDir = Paths.get(projectPath, "lib")
        val alreadyHasJars = Files.isDirectory(libDir) &&
            Files.list(libDir).use { it.anyMatch { f -> f.toString().endsWith(".jar") } }
        if (alreadyHasJars) {
            return DepsResult(
                success = false,
                skipped = true,
                message = "lib/ already contains JARs — skipping",
                reason = DepsResultReason.LIB_ALREADY_POPULATED,
            )
        }

        return executePlay(projectPath, effectivePlayHome, indicator, onLine)
    }

    private fun executePlay(
        projectPath: String,
        playHome: String,
        indicator: ProgressIndicator?,
        onLine: (line: String, isError: Boolean) -> Unit,
    ): DepsResult {
        val playScript = Paths.get(playHome, "play").toFile()
        val commandResult = buildCommand(playScript, indicator, onLine)
        val command = commandResult.command
            ?: return DepsResult(
                success = false, skipped = true,
                message = when (commandResult.reason) {
                    DepsResultReason.MANAGED_RUNTIME_UNAVAILABLE ->
                        "could not provision a managed PyPy 2.7 runtime for the play script"
                    else -> "could not find a Python interpreter for the play script"
                },
                reason = commandResult.reason,
                requiredPythonMajor = commandResult.requiredPythonMajor,
                detail = commandResult.detail,
            )

        onLine("$ ${command.joinToString(" ")} deps", false)

        val process = try {
            ProcessBuilder(command + "deps")
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()
        } catch (ex: Exception) {
            return DepsResult(success = false, message = "failed to start: ${ex.message}")
        }

        val reader = process.inputStream.bufferedReader()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            onLine(line!!, false)
        }

        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            DepsResult(success = true, message = "completed successfully")
        } else {
            DepsResult(success = false, message = "play deps exited with code $exitCode")
        }
    }

    private fun supportsDepCommand(version: String?): Boolean {
        if (version == null) return true // unknown version → attempt anyway
        val parts = version.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return true
        val major = parts[0]
        val minor = parts[1]
        return major > 1 || (major == 1 && minor >= 2)
    }

    private data class CommandBuildResult(
        val command: List<String>?,
        val requiredPythonMajor: Int?,
        val reason: DepsResultReason = DepsResultReason.NONE,
        val detail: String? = null,
    )

    private fun buildCommand(
        playScript: File,
        indicator: ProgressIndicator?,
        onLine: (line: String, isError: Boolean) -> Unit,
    ): CommandBuildResult {
        if (!playScript.exists()) return CommandBuildResult(null, null)
        return if (isPythonScript(playScript)) {
            if (requiresPython2(playScript)) {
                val systemPython2 = findPython2()
                val managedRuntime = if (systemPython2 == null) {
                    Play1ManagedPythonRuntime.ensurePyPy2(indicator, onLine)
                } else {
                    Play1ManagedPythonRuntime.RuntimeProvisionResult(executable = null)
                }
                val interpreter = systemPython2 ?: managedRuntime.executable?.toAbsolutePath()?.toString()
                val reason = when {
                    interpreter != null -> DepsResultReason.NONE
                    Play1ManagedPythonRuntime.detectArtifactForCurrentPlatform() == null ->
                        DepsResultReason.MANAGED_RUNTIME_UNAVAILABLE
                    else -> DepsResultReason.MANAGED_RUNTIME_UNAVAILABLE
                }
                CommandBuildResult(
                    command = interpreter?.let { listOf(it, playScript.absolutePath) },
                    requiredPythonMajor = 2,
                    reason = reason,
                    detail = managedRuntime.errorMessage,
                )
            } else {
                val interpreter = findPython3() ?: findPython()
                CommandBuildResult(
                    command = interpreter?.let { listOf(it, playScript.absolutePath) },
                    requiredPythonMajor = 3,
                    reason = if (interpreter == null) DepsResultReason.PYTHON_INTERPRETER_MISSING else DepsResultReason.NONE,
                )
            }
        } else {
            CommandBuildResult(listOf(playScript.absolutePath), null)
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
    } catch (_: Exception) { false }
}
