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
    fun `deps stay available for play 1 1 via managed play download`() {
        val playHome = createPlayHome("project-play", "play.jar", "1.1.1", listOf("clean", "test"))
        val projectDir = createProjectDir(withDependenciesFile = true)

        val plan = Play1CliRunner.plan(
            request = Play1CliRequest(Play1CliCommandId.DEPS),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.1.1",
            depsPlayHome = "",
        )

        assertTrue(plan.available)
        assertEquals("deps", plan.commandName)
        assertEquals("1.5.3", plan.effectivePlayVersion)
        assertTrue(plan.runtimeDescription?.contains("Managed Play 1.5.3") == true)
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

    @Test
    fun `python 3 play launcher is detected from shebang`() {
        val playHome = createPlayHome(
            name = "python3-play-home",
            jarName = "play-1.5.3.jar",
            version = "1.5.3",
            commands = listOf("clean"),
            launcher = """
                #!/usr/bin/env python3
                from __future__ import print_function
                print("~")
            """.trimIndent(),
        )
        val projectDir = createProjectDir(withDependenciesFile = false)

        val plan = Play1CliRunner.plan(
            request = Play1CliRequest(Play1CliCommandId.CLEAN),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.5.3",
            depsPlayHome = "",
        )

        assertTrue(plan.available)
        assertEquals(3, plan.requiredPythonMajor)
        assertFalse(plan.runtimeDescription.orEmpty().contains("Managed PyPy 2.7"))
    }

    @Test
    fun `python 2 play launcher is detected from legacy print syntax`() {
        val playHome = createPlayHome(
            name = "python2-play-home",
            jarName = "play-1.5.3.jar",
            version = "1.5.3",
            commands = listOf("clean"),
            launcher = """
                #!/usr/bin/env python
                print r"~"
            """.trimIndent(),
        )
        val projectDir = createProjectDir(withDependenciesFile = false)

        val plan = Play1CliRunner.plan(
            request = Play1CliRequest(Play1CliCommandId.CLEAN),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.5.3",
            depsPlayHome = "",
        )

        assertTrue(plan.available)
        assertEquals(2, plan.requiredPythonMajor)
    }

    @Test
    fun `windows play bat launcher is accepted when python script is missing`() {
        val playHome = createPlayHome(
            name = "windows-play-home",
            jarName = "play-1.5.3.jar",
            version = "1.5.3",
            commands = listOf("clean"),
            launcher = null,
        )
        File(playHome, "play.bat").writeText("@echo off\r\n")
        val projectDir = createProjectDir(withDependenciesFile = false)

        val plan = Play1CliRunner.plan(
            request = Play1CliRequest(Play1CliCommandId.CLEAN),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.5.3",
            depsPlayHome = "",
        )

        assertTrue(plan.available)
        assertEquals("Native launcher", plan.runtimeDescription)
    }

    @Test
    fun `run applies java environment overrides to play command process`() {
        val playHome = createPlayHome(
            name = "env-play-home",
            jarName = "play-1.5.3.jar",
            version = "1.5.3",
            commands = listOf("clean"),
            launcher = """
                #!/bin/sh
                echo "JAVA_HOME=${'$'}JAVA_HOME"
                echo "PATH=${'$'}PATH"
            """.trimIndent(),
        )
        val projectDir = createProjectDir(withDependenciesFile = false)
        val lines = mutableListOf<String>()

        val result = Play1CliRunner.run(
            request = Play1CliRequest(Play1CliCommandId.CLEAN),
            projectPath = projectDir.absolutePath,
            playHome = playHome.absolutePath,
            projectPlayVersion = "1.5.3",
            environmentOverrides = mapOf(
                "JAVA_HOME" to "/jdks/current",
                "PATH" to "/jdks/current/bin:/usr/bin",
            ),
            onLine = { line, _ -> lines.add(line) },
        )

        assertTrue(result.success)
        assertTrue(lines.any { it == "JAVA_HOME=/jdks/current" })
        assertTrue(lines.any { it == "PATH=/jdks/current/bin:/usr/bin" })
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

    private fun createPlayHome(
        name: String,
        jarName: String,
        version: String,
        commands: List<String>,
        launcher: String? = """
            #!/usr/bin/env python
            print r"~"
        """.trimIndent(),
    ): File {
        val playHome = tempDir.newFolder(name)
        val frameworkDir = File(playHome, "framework").apply { mkdirs() }
        val playJar = File(frameworkDir, jarName)
        val stubJar = Play1CliRunnerTest::class.java.getResourceAsStream("/stubs/play-stub.jar")
            ?: error("play-stub.jar not found")
        stubJar.use { Files.copy(it, playJar.toPath()) }

        if (jarName == "play.jar") {
            // Version hint is passed separately for Play 1.1-style homes in tests.
        }

        if (launcher != null) {
            File(playHome, "play").writeText(launcher)
            File(playHome, "play").setExecutable(true)
        }

        val commandsDir = File(playHome, "framework/pym/play/commands").apply { mkdirs() }
        File(commandsDir, "commands.py").writeText(
            """
            COMMANDS = [${commands.joinToString(", ") { "'$it'" }}]
            """.trimIndent()
        )
        return playHome
    }
}
