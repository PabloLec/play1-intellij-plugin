package com.github.pablolec.play1toolkit.project

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

data class DepsResult(
    val success: Boolean,
    val skipped: Boolean = false,
    val message: String = "",
)

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
        onLine: (line: String, isError: Boolean) -> Unit = { _, _ -> },
    ): DepsResult {
        if (!supportsDepCommand(playVersion)) {
            return DepsResult(
                success = false, skipped = true,
                message = "play deps requires Play 1.2+ (detected: ${playVersion ?: "unknown"})"
            )
        }

        val depsFile = Paths.get(projectPath, "conf", "dependencies.yml")
        if (!depsFile.toFile().exists()) {
            return DepsResult(success = false, skipped = true, message = "conf/dependencies.yml not found")
        }

        val libDir = Paths.get(projectPath, "lib")
        val alreadyHasJars = Files.isDirectory(libDir) &&
            Files.list(libDir).use { it.anyMatch { f -> f.toString().endsWith(".jar") } }
        if (alreadyHasJars) {
            return DepsResult(success = false, skipped = true, message = "lib/ already contains JARs — skipping")
        }

        val playScript = Paths.get(playHome, "play").toFile()
        val command = buildCommand(playScript)
            ?: return DepsResult(
                success = false, skipped = true,
                message = "could not find a Python interpreter for the play script"
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

    private fun buildCommand(playScript: File): List<String>? {
        if (!playScript.exists()) return null
        return if (isPythonScript(playScript)) {
            val interpreter = if (requiresPython2(playScript)) findPython2() else findPython3() ?: findPython()
            interpreter ?: return null
            listOf(interpreter, playScript.absolutePath)
        } else {
            listOf(playScript.absolutePath)
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
