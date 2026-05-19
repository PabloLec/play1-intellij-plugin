package com.github.pablolec.play1toolkit.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Play1CliCapabilitiesDetectorTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `detect extracts declared command names from command modules`() {
        val commandsDir = tempDir.newFolder("play-home", "framework", "pym", "play", "commands")
        File(commandsDir, "base.py").writeText(
            """
            COMMANDS = ['run', 'clean', 'autotest', 'auto-test']
            """.trimIndent()
        )
        File(commandsDir, "deps.py").writeText(
            """
            COMMANDS = ['dependencies', 'deps']
            """.trimIndent()
        )

        val detected = Play1CliCapabilitiesDetector.detect(tempDir.root.toPath().resolve("play-home"))

        assertEquals(setOf("run", "clean", "autotest", "auto-test", "dependencies", "deps"), detected.commands)
    }

    @Test
    fun `resolve command name respects aliases`() {
        val commands = setOf("dependencies", "auto-test", "clean")

        assertEquals(
            "dependencies",
            Play1CliCapabilitiesDetector.resolveCommandName(Play1CliCommandId.DEPS, commands, "1.5.3")
        )
        assertEquals(
            "auto-test",
            Play1CliCapabilitiesDetector.resolveCommandName(Play1CliCommandId.AUTOTEST, commands, "1.5.3")
        )
        assertNull(Play1CliCapabilitiesDetector.resolveCommandName(Play1CliCommandId.WAR, commands, "1.5.3"))
    }
}
