package com.github.pablolec.play1toolkit.detection

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class Play1HomeValidatorTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `rejects non-existent path`() {
        val result = Play1HomeValidator.validate(tempDir.root.toPath().resolve("does-not-exist"))
        assertFalse(result.valid)
        assertNotNull(result.error)
    }

    @Test
    fun `rejects path without framework dir`() {
        val result = Play1HomeValidator.validate(tempDir.root.toPath())
        assertFalse(result.valid)
        assertTrue(result.error?.contains("framework") == true)
    }

    @Test
    fun `rejects path with framework dir but no play jar`() {
        tempDir.newFolder("framework")
        val result = Play1HomeValidator.validate(tempDir.root.toPath())
        assertFalse(result.valid)
        assertTrue(result.error?.contains("play-*.jar") == true || result.error?.contains("Not found") == true)
    }

    @Test
    fun `validates play home with stub jar containing Controller class`() {
        val frameworkDir = tempDir.newFolder("framework")
        val stubJarSrc = Play1HomeValidatorTest::class.java
            .getResourceAsStream("/stubs/play-stub.jar")
            ?: error("play-stub.jar not found in test resources")

        val playJar = File(frameworkDir, "play-1.0.0.jar")
        stubJarSrc.use { Files.copy(it, playJar.toPath()) }

        val result = Play1HomeValidator.validate(tempDir.root.toPath())
        assertTrue("Should be valid with stub jar: ${result.error}", result.valid)
        assertEquals("1.0.0", result.playVersion)
    }

    @Test
    fun `extracts version from jar name`() {
        val frameworkDir = tempDir.newFolder("framework")
        val stubJarSrc = Play1HomeValidatorTest::class.java
            .getResourceAsStream("/stubs/play-stub.jar")
            ?: error("play-stub.jar not found in test resources")

        val playJar = File(frameworkDir, "play-1.2.7.jar")
        stubJarSrc.use { Files.copy(it, playJar.toPath()) }

        val result = Play1HomeValidator.validate(tempDir.root.toPath())
        assertEquals("1.2.7", result.playVersion)
    }
}
