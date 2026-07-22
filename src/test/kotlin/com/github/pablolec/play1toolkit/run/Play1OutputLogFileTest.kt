package com.github.pablolec.play1toolkit.run

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Play1OutputLogFileTest {

    @Test
    fun `blank path creates a temporary log file`() {
        val path = Play1OutputLogFile.resolve("", "Sample App")

        assertTrue(path.exists())
        assertTrue(path.name.startsWith("play-v1-sample-app-"))
        assertTrue(path.name.endsWith(".log"))
        Files.deleteIfExists(path)
    }

    @Test
    fun `directory path creates a named log file inside directory`() {
        val directory = Files.createTempDirectory("play-v1-output-log-test")

        val path = Play1OutputLogFile.resolve(directory.toString(), "Sample App")

        assertEquals(directory.resolve("play-v1-sample-app.log"), path)
        Files.deleteIfExists(directory)
    }

    @Test
    fun `file path is used as is`() {
        val directory = Files.createTempDirectory("play-v1-output-log-test")
        val file = directory.resolve("custom.log")

        val path = Play1OutputLogFile.resolve(file.toString(), "Sample App")

        assertEquals(file, path)
        Files.deleteIfExists(directory)
    }
}
