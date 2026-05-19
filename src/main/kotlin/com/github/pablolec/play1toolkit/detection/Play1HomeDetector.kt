package com.github.pablolec.play1toolkit.detection

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Auto-detects a Play Framework 1.x installation directory.
 * Checks environment variables, common installation paths, and PATH.
 */
object Play1HomeDetector {

    fun detect(): Path? {
        return fromEnvironment()
            ?: fromCommonPaths()
            ?: fromPlayCommand()
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
            process.waitFor()

            val playScript = Paths.get(output).toRealPath()
            // play script is at $PLAY_HOME/play, so parent is $PLAY_HOME
            val candidate = playScript.parent
            if (Play1HomeValidator.isValidPlayHome(candidate)) candidate else null
        } catch (_: Exception) {
            null
        }
    }
}
