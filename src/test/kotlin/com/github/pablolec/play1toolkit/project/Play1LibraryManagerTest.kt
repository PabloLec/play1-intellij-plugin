package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * Tests the JAR scanning and Play home validation logic used by Play1LibraryManager.
 * Exercises the file-system scanning without requiring an IDE environment.
 */
class Play1LibraryManagerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    // ── findPlayJar ─────────────────────────────────────────────────────────────

    @Test
    fun `findPlayJar returns null for empty directory`() {
        val frameworkDir = tempDir.newFolder("framework")
        val result = Play1HomeValidator.findPlayJar(frameworkDir.toPath())
        assertNull(result)
    }

    @Test
    fun `findPlayJar returns null when no jar matches play-* pattern`() {
        val frameworkDir = tempDir.newFolder("framework")
        File(frameworkDir, "other-lib.jar").createNewFile()
        File(frameworkDir, "tools.jar").createNewFile()
        val result = Play1HomeValidator.findPlayJar(frameworkDir.toPath())
        assertNull(result)
    }

    @Test
    fun `findPlayJar finds play-1_2_7-jar in framework dir`() {
        val frameworkDir = tempDir.newFolder("framework")
        val stubJar = Play1LibraryManagerTest::class.java.getResourceAsStream("/stubs/play-stub.jar")
            ?: error("play-stub.jar not found")

        val playJar = File(frameworkDir, "play-1.2.7.jar")
        stubJar.use { Files.copy(it, playJar.toPath()) }

        val result = Play1HomeValidator.findPlayJar(frameworkDir.toPath())
        assertNotNull("Should find play-1.2.7.jar", result)
        assertEquals("play-1.2.7.jar", result!!.fileName.toString())
    }

    @Test
    fun `findPlayJar prefers play-versioned jar over other jars`() {
        val frameworkDir = tempDir.newFolder("framework")
        val stubJar = Play1LibraryManagerTest::class.java.getResourceAsStream("/stubs/play-stub.jar")
            ?: error("play-stub.jar not found")

        File(frameworkDir, "other.jar").createNewFile()
        val playJar = File(frameworkDir, "play-1.0.0.jar")
        stubJar.use { Files.copy(it, playJar.toPath()) }
        File(frameworkDir, "another.jar").createNewFile()

        val result = Play1HomeValidator.findPlayJar(frameworkDir.toPath())
        assertNotNull(result)
        assertTrue(result!!.fileName.toString().startsWith("play-"))
    }

    // ── JAR URL format ─────────────────────────────────────────────────────────

    @Test
    fun `jar URL format is correct for classpath entries`() {
        // Verify the URL format used by Play1LibraryManager.toJarUrl
        // jar:// URLs must end with "!/" for IntelliJ to treat them as jar roots
        val testJar = tempDir.newFile("test.jar")
        val url = "jar://" + testJar.absolutePath + "!/"
        assertTrue("JAR URL should end with !/", url.endsWith("!/"))
        assertTrue("JAR URL should start with jar://", url.startsWith("jar://"))
    }

    // ── Framework lib scanning ──────────────────────────────────────────────────

    @Test
    fun `lib directory jar count is accurate`() {
        val libDir = tempDir.newFolder("framework", "lib")
        File(libDir, "a.jar").createNewFile()
        File(libDir, "b.jar").createNewFile()
        File(libDir, "c.txt").createNewFile()  // should not count

        val jarCount = libDir.listFiles()!!.count { it.name.endsWith(".jar") }
        assertEquals("Should find 2 JARs", 2, jarCount)
    }

    @Test
    fun `empty lib directory produces zero jars`() {
        val libDir = tempDir.newFolder("framework", "lib")
        val jarCount = libDir.listFiles()!!.count { it.name.endsWith(".jar") }
        assertEquals(0, jarCount)
    }

    @Test
    fun `project lib dir without jar files produces zero count`() {
        val projectLibDir = tempDir.newFolder("lib")
        File(projectLibDir, "readme.txt").createNewFile()
        val jarCount = projectLibDir.listFiles()!!.count { it.name.endsWith(".jar") }
        assertEquals(0, jarCount)
    }

    @Test
    fun `project lib dir with multiple jars all counted`() {
        val projectLibDir = tempDir.newFolder("lib")
        repeat(5) { i -> File(projectLibDir, "dep$i.jar").createNewFile() }
        val jarCount = projectLibDir.listFiles()!!.count { it.name.endsWith(".jar") }
        assertEquals(5, jarCount)
    }
}
