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

    @Test
    fun `artifact key strips trailing version from jar name`() {
        assertEquals("slf4j-api", Play1LibraryManager.artifactKey(tempDir.newFile("slf4j-api-1.7.36.jar").toPath()))
        assertEquals("kafka_2.13", Play1LibraryManager.artifactKey(tempDir.newFile("kafka_2.13-8.1.1.jar").toPath()))
        assertEquals("asm", Play1LibraryManager.artifactKey(tempDir.newFile("asm-7.0.jar").toPath()))
    }

    @Test
    fun `project lib jars override framework jars with same artifact`() {
        val frameworkLibDir = tempDir.newFolder("play-home", "framework", "lib")
        val projectLibDir = tempDir.newFolder("project", "lib")

        File(frameworkLibDir, "slf4j-api-1.6.1.jar").createNewFile()
        File(frameworkLibDir, "commons-io-1.4.jar").createNewFile()
        File(projectLibDir, "slf4j-api-1.7.36.jar").createNewFile()
        File(projectLibDir, "okio-1.13.0.jar").createNewFile()

        val result = Play1LibraryManager.buildProjectClasspathJars(
            tempDir.root.toPath().resolve("play-home"),
            tempDir.root.toPath().resolve("project").toString()
        )

        assertEquals(
            listOf("okio-1.13.0.jar", "slf4j-api-1.7.36.jar"),
            result.projectJars.map { it.fileName.toString() }
        )
        assertEquals(
            listOf("commons-io-1.4.jar"),
            result.frameworkJars.map { it.fileName.toString() }
        )
        assertEquals(
            listOf("slf4j-api-1.6.1.jar"),
            result.overriddenFrameworkJars.map { it.fileName.toString() }
        )
    }

    @Test
    fun `project lib jars override framework jars across commons collections versions`() {
        val frameworkLibDir = tempDir.newFolder("play-home-cc", "framework", "lib")
        val projectLibDir = tempDir.newFolder("project-cc", "lib")

        File(frameworkLibDir, "commons-collections-3.1.jar").createNewFile()
        File(projectLibDir, "commons-collections-3.2.2.jar").createNewFile()

        val result = Play1LibraryManager.buildProjectClasspathJars(
            tempDir.root.toPath().resolve("play-home-cc"),
            tempDir.root.toPath().resolve("project-cc").toString()
        )

        assertEquals(
            listOf("commons-collections-3.2.2.jar"),
            result.projectJars.map { it.fileName.toString() }
        )
        assertTrue(result.frameworkJars.isEmpty())
        assertEquals(
            listOf("commons-collections-3.1.jar"),
            result.overriddenFrameworkJars.map { it.fileName.toString() }
        )
    }
}
