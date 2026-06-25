package com.github.pablolec.play1toolkit.detection

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Auto-detects a Play Framework 1.x installation directory.
 * Checks environment variables, common installation paths, and PATH.
 */
object Play1HomeDetector {

    fun detect(includeUserShell: Boolean = false): Path? {
        return fromEnvironment()
            ?: fromCommonPaths()
            ?: fromPlayCommand()
            ?: if (includeUserShell) fromUserShell() else null
    }

    private fun fromEnvironment(): Path? {
        val playHome = System.getenv("PLAY_HOME") ?: return null
        val path = Paths.get(playHome)
        return if (Play1HomeValidator.isValidPlayHome(path)) path else null
    }

    private fun fromCommonPaths(): Path? {
        val home = System.getProperty("user.home")
        val candidates = buildList {
            // Linux / macOS common paths
            addAll(
                (1..5).flatMap { minor ->
                    listOf(
                        Paths.get("/opt/play-1.$minor"),
                        Paths.get("/opt/play-1.$minor.0"),
                        Paths.get("/usr/local/play-1.$minor"),
                        Paths.get("/usr/local/share/play-1.$minor"),
                        Paths.get("$home/play-1.$minor"),
                        Paths.get("$home/.play"),
                        Paths.get("$home/tools/play-1.$minor"),
                    )
                }
            )
            // Windows
            add(Paths.get("C:/play-1.0"))
            add(Paths.get("C:/play"))
        }

        return candidates.firstOrNull { Play1HomeValidator.isValidPlayHome(it) }
    }

    private fun fromPlayCommand(): Path? {
        return try {
            val process = ProcessBuilder("which", "play")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readLine()?.trim() ?: return null
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }

            resolveCommandOutput(output)
        } catch (_: Exception) {
            null
        }
    }

    private fun fromUserShell(): Path? {
        val shells = listOfNotNull(
            System.getenv("SHELL"),
            "/bin/zsh",
            "/bin/bash",
        ).distinct()

        return shells.firstNotNullOfOrNull { shell ->
            val shellPath = Paths.get(shell)
            if (!Files.isRegularFile(shellPath)) return@firstNotNullOfOrNull null
            runShellDetection(shellPath)
        }
    }

    private fun runShellDetection(shell: Path): Path? {
        return try {
            val command = "if [ -n \"\$PLAY_HOME\" ]; then printf '%s\\n' \"\$PLAY_HOME\"; elif command -v play >/dev/null 2>&1; then command -v play; fi"
            val process = ProcessBuilder(shell.toString(), "-ic", command)
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }

            process.inputStream.bufferedReader()
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .firstNotNullOfOrNull(::resolveCommandOutput)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCommandOutput(output: String): Path? {
        return try {
            val path = Paths.get(output).toRealPath()
            val candidate = if (path.fileName.toString() == "play" && Files.isRegularFile(path)) {
                path.parent
            } else {
                path
            }
            if (Play1HomeValidator.isValidPlayHome(candidate)) candidate else null
        } catch (_: Exception) {
            null
        }
    }
}
