package com.github.pablolec.play1toolkit.detection

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Validates a Play Framework 1.x home directory.
 * Checks for the presence of play-*.jar and verifies it contains core Play classes.
 */
object Play1HomeValidator {

    data class ValidationResult(
        val valid: Boolean,
        val playVersion: String?,
        val playJar: Path?,
        val error: String?
    )

    fun validate(playHome: Path): ValidationResult {
        if (!Files.isDirectory(playHome)) {
            return ValidationResult(false, null, null, "Path does not exist or is not a directory")
        }

        val frameworkDir = playHome.resolve("framework")
        if (!Files.isDirectory(frameworkDir)) {
            return ValidationResult(false, null, null, "Missing framework/ directory in Play Home")
        }

        val playJar = findPlayJar(frameworkDir)
            ?: return ValidationResult(false, null, null, "No Play JAR found in framework/ (expected play.jar or play-X.Y.Z.jar)")

        if (!containsControllerClass(playJar)) {
            return ValidationResult(false, null, null, "play-*.jar does not contain play/mvc/Controller.class")
        }

        val version = extractVersion(playJar)
        return ValidationResult(true, version, playJar, null)
    }

    fun isValidPlayHome(path: Path): Boolean = validate(path).valid

    fun findPlayJar(frameworkDir: Path): Path? {
        if (!Files.isDirectory(frameworkDir)) return null
        return Files.list(frameworkDir).use { stream ->
            stream
                .filter { path ->
                    val name = path.fileName.toString()
                    // Matches "play-1.2.7.jar" (Play 1.2.x) and "play.jar" (Play 1.1.x and earlier)
                    name.matches(Regex("play-\\d+\\..*\\.jar")) || name == "play.jar"
                }
                .findFirst()
                .orElse(null)
        }
    }

    private fun containsControllerClass(jar: Path): Boolean {
        return try {
            JarFile(jar.toFile()).use { jf ->
                jf.getJarEntry("play/mvc/Controller.class") != null
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun extractVersion(jar: Path): String? {
        // Try filename first (e.g., play-1.2.7.jar)
        val filenameVersion = Regex("play-(\\d+\\.\\S+)\\.jar").find(jar.fileName.toString())?.groupValues?.get(1)
        if (filenameVersion != null) return filenameVersion

        // For play.jar (Play 1.1.x): read from play/version entry inside the JAR
        return try {
            JarFile(jar.toFile()).use { jf ->
                val versionEntry = jf.getJarEntry("play/version")
                if (versionEntry != null) {
                    jf.getInputStream(versionEntry).bufferedReader().readText().trim()
                } else {
                    jf.manifest?.mainAttributes?.getValue("Specification-Version")
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
