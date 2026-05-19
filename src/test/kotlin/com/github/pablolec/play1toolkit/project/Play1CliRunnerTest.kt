package com.github.pablolec.play1toolkit.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class Play1CliRunnerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `deps are unavailable for play 1 1 without alternate deps home`() {
        val playHome = createPlayHome("project-play", "play.jar", "1.1.1", listOf("clean", "test"))
        val projectDir = createProjectDir(withDependenciesFile = true)

        val plan = Play1CliRunner.plan(
            request = Play1CliRequest(Play1CliCommandId.DEPS),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.1.1",
            depsPlayHome = "",
        )

        assertFalse(plan.available)
        assertEquals(Play1CliResultReason.UNSUPPORTED_PLAY_VERSION, plan.reason)
    }

    @Test
    fun `deps use alternate play home when project version does not support them`() {
        val playHome = createPlayHome("project-play", "play.jar", "1.1.1", listOf("clean", "test"))
        val depsHome = createPlayHome("deps-play", "play-1.5.3.jar", "1.5.3", listOf("clean", "deps"))
        val projectDir = createProjectDir(withDependenciesFile = true)

        val plan = Play1CliRunner.plan(
            request = Play1CliRequest(Play1CliCommandId.DEPS),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.1.1",
            depsPlayHome = depsHome.absolutePath,
        )

        assertTrue(plan.available)
        assertEquals("deps", plan.commandName)
        assertEquals(depsHome.absolutePath, plan.effectivePlayHome)
        assertEquals("1.5.3", plan.effectivePlayVersion)
    }

    @Test
    fun `war output must be outside project directory`() {
        val playHome = createPlayHome("play-home", "play-1.5.3.jar", "1.5.3", listOf("war"))
        val projectDir = createProjectDir(withDependenciesFile = false)

        val plan = Play1CliRunner.plan(
            request = Play1CliRequest(
                Play1CliCommandId.WAR,
                warOutputPath = File(projectDir, "build/war").absolutePath,
            ),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.5.3",
            depsPlayHome = "",
        )

        assertFalse(plan.available)
        assertEquals(Play1CliResultReason.INVALID_COMMAND_OPTIONS, plan.reason)
    }

    @Test
    fun `clean command is available when declared by play home`() {
        val playHome = createPlayHome("play-home", "play-1.5.3.jar", "1.5.3", listOf("clean"))
        val projectDir = createProjectDir(withDependenciesFile = false)

        val plan = Play1CliRunner.plan(
            request = Play1CliRequest(Play1CliCommandId.CLEAN),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.5.3",
            depsPlayHome = "",
        )

        assertTrue(plan.available)
        assertEquals("clean", plan.commandName)
        assertTrue(plan.runtimeDescription?.contains("Managed PyPy 2.7") == true || plan.runtimeDescription?.contains("Python 2") == true)
    }

    private fun createProjectDir(withDependenciesFile: Boolean): File {
        val projectDir = tempDir.newFolder("project-${System.nanoTime()}")
        File(projectDir, "conf").mkdirs()
        File(projectDir, "lib").mkdirs()
        if (withDependenciesFile) {
            File(projectDir, "conf/dependencies.yml").writeText("require:\n  - play\n")
        }
        return projectDir
    }

    private fun createPlayHome(name: String, jarName: String, version: String, commands: List<String>): File {
        val playHome = tempDir.newFolder(name)
        val frameworkDir = File(playHome, "framework").apply { mkdirs() }
        val playJar = File(frameworkDir, jarName)
        val stubJar = Play1CliRunnerTest::class.java.getResourceAsStream("/stubs/play-stub.jar")
            ?: error("play-stub.jar not found")
        stubJar.use { Files.copy(it, playJar.toPath()) }

        if (jarName == "play.jar") {
            // Version hint is passed separately for Play 1.1-style homes in tests.
        }

        File(playHome, "play").writeText(
            """
            #!/usr/bin/env python
            print r"~"
            """.trimIndent()
        )
        File(playHome, "play").setExecutable(true)

        val commandsDir = File(playHome, "framework/pym/play/commands").apply { mkdirs() }
        File(commandsDir, "commands.py").writeText(
            """
            COMMANDS = [${commands.joinToString(", ") { "'$it'" }}]
            """.trimIndent()
        )
        return playHome
    }
}
