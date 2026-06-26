package com.github.pablolec.play1toolkit.project

import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object Play1PythonRuntimeResolver {

    data class Resolution(
        val commandPrefix: List<String>?,
        val requiredMajor: Int?,
        val description: String?,
        val reason: Play1CliResultReason = Play1CliResultReason.NONE,
        val detail: String? = null,
    )

    enum class Requirement {
        PYTHON_2,
        PYTHON_3,
        ANY_PYTHON,
    }

    fun describe(script: File): String {
        val requirement = detectRequirement(script)
        val local = resolveLocal(requirement)
        if (local != null) return local.description ?: local.commandPrefix?.joinToString(" ").orEmpty()
        return when (requirement) {
            Requirement.PYTHON_2 ->
                if (Play1ManagedPythonRuntime.detectArtifactForCurrentPlatform() != null) {
                    "Managed PyPy 2.7 fallback"
                } else {
                    "Python 2 unavailable"
                }
            Requirement.PYTHON_3 -> "Python 3 unavailable"
            Requirement.ANY_PYTHON ->
                if (Play1ManagedPythonRuntime.detectArtifactForCurrentPlatform() != null) {
                    "Python unavailable; managed PyPy 2.7 fallback available"
                } else {
                    "Python unavailable"
                }
        }
    }

    fun resolve(
        script: File,
        indicator: ProgressIndicator?,
        onLine: (line: String, isError: Boolean) -> Unit,
    ): Resolution {
        val requirement = detectRequirement(script)
        val local = resolveLocal(requirement)
        if (local != null) return local

        if (requirement == Requirement.PYTHON_3) {
            return Resolution(
                commandPrefix = null,
                requiredMajor = 3,
                description = "Python 3 unavailable",
                reason = Play1CliResultReason.PYTHON_INTERPRETER_MISSING,
                detail = "The Play launcher requires Python 3, but no Python 3 interpreter was found.",
            )
        }

        val managedRuntime = Play1ManagedPythonRuntime.ensurePyPy2(indicator, onLine)
        val executable = managedRuntime.executable?.toAbsolutePath()?.toString()
        return Resolution(
            commandPrefix = executable?.let { listOf(it) },
            requiredMajor = 2,
            description = managedRuntime.executable?.let { "Managed PyPy 2.7 ($it)" },
            reason = if (executable == null) Play1CliResultReason.MANAGED_RUNTIME_UNAVAILABLE else Play1CliResultReason.NONE,
            detail = managedRuntime.errorMessage,
        )
    }

    internal fun detectRequirement(script: File): Requirement {
        val lines = runCatching {
            script.bufferedReader().use { reader ->
                generateSequence { reader.readLine() }.take(120).toList()
            }
        }.getOrDefault(emptyList())
        val firstLine = lines.firstOrNull().orEmpty().lowercase(Locale.ROOT)
        if (firstLine.contains("python3")) return Requirement.PYTHON_3
        if (firstLine.contains("python2") || firstLine.contains("pypy")) return Requirement.PYTHON_2
        if (lines.any(::looksPython2Only)) return Requirement.PYTHON_2
        if (lines.any(::looksPython3Only)) return Requirement.PYTHON_3
        return Requirement.ANY_PYTHON
    }

    private fun resolveLocal(requirement: Requirement): Resolution? {
        val candidates = when (requirement) {
            Requirement.PYTHON_2 -> python2Candidates() + genericPythonCandidates()
            Requirement.PYTHON_3 -> python3Candidates() + genericPythonCandidates()
            Requirement.ANY_PYTHON -> python3Candidates() + genericPythonCandidates() + python2Candidates()
        }

        val seen = linkedSetOf<String>()
        for (candidate in candidates) {
            val key = candidate.command.joinToString("\u0000")
            if (!seen.add(key)) continue
            val version = detectVersion(candidate.command) ?: continue
            if (candidate.expectedMajor != null && version.major != candidate.expectedMajor) continue
            if (requirement == Requirement.PYTHON_2 && version.major != 2) continue
            if (requirement == Requirement.PYTHON_3 && version.major != 3) continue
            return Resolution(
                commandPrefix = candidate.command,
                requiredMajor = when (requirement) {
                    Requirement.PYTHON_2 -> 2
                    Requirement.PYTHON_3 -> 3
                    Requirement.ANY_PYTHON -> version.major
                },
                description = "Python ${version.major} (${candidate.command.joinToString(" ")})",
            )
        }
        return null
    }

    private data class Candidate(
        val command: List<String>,
        val expectedMajor: Int?,
    )

    private data class PythonVersion(val major: Int)

    private fun python3Candidates(): List<Candidate> = listOf(
        Candidate(listOf("py", "-3"), 3),
        Candidate(listOf("python3"), 3),
        Candidate(listOf("python3.13"), 3),
        Candidate(listOf("python3.12"), 3),
        Candidate(listOf("python3.11"), 3),
        Candidate(listOf("python3.10"), 3),
        Candidate(listOf("python3.9"), 3),
        Candidate(listOf("python3.8"), 3),
    )

    private fun python2Candidates(): List<Candidate> = listOf(
        Candidate(listOf("py", "-2"), 2),
        Candidate(listOf("python2"), 2),
        Candidate(listOf("python2.7"), 2),
        Candidate(listOf("python2.6"), 2),
    )

    private fun genericPythonCandidates(): List<Candidate> = listOf(
        Candidate(listOf("python"), null),
    )

    private fun detectVersion(command: List<String>): PythonVersion? {
        return try {
            val process = ProcessBuilder(command + "--version")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            val output = process.inputStream.bufferedReader().readText()
            if (process.exitValue() != 0) return null
            parseVersion(output)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVersion(output: String): PythonVersion? {
        val match = Regex("""Python\s+(\d+)\.""").find(output) ?: return null
        return PythonVersion(match.groupValues[1].toInt())
    }

    private fun looksPython2Only(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("#")) return false
        if (Regex("""\bprint\s+[ruRU]?["']""").containsMatchIn(trimmed)) return true
        if (Regex("""\bexcept\s+\w+\s*,""").containsMatchIn(trimmed)) return true
        if (Regex("""\bxrange\s*\(""").containsMatchIn(trimmed)) return true
        return false
    }

    private fun looksPython3Only(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("#")) return false
        if (Regex("""\bf["']""").containsMatchIn(trimmed)) return true
        return false
    }
}
