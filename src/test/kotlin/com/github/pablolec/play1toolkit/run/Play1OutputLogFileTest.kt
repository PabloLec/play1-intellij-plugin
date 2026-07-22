package com.github.pablolec.play1toolkit.run

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class Play1OutputLogFileTest {

    @Test
    fun `blank path resolves to a stable temporary log file`() {
        val path = Play1OutputLogFile.resolve("", "Sample App")

        assertEquals(
            Paths.get(System.getProperty("java.io.tmpdir")).resolve("play-v1-sample-app.log").toAbsolutePath().normalize(),
            path,
        )
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

    @Test
    fun `creating listener truncates an existing log file`() {
        val directory = Files.createTempDirectory("play-v1-output-log-test")
        val file = directory.resolve("custom.log")
        file.writeText("previous output")

        val listener = Play1OutputLogFileListener.create(file)
        listener.processTerminated(
            com.intellij.execution.process.ProcessEvent(
                object : com.intellij.execution.process.ProcessHandler() {
                    override fun destroyProcessImpl() = Unit
                    override fun detachProcessImpl() = Unit
                    override fun detachIsDefault(): Boolean = false
                    override fun getProcessInput() = null
                },
                0,
            )
        )

        assertEquals("", file.readText())
        Files.deleteIfExists(file)
        Files.deleteIfExists(directory)
    }
}
