package com.github.pablolec.play1toolkit.detection

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Play1ProjectDetectorTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val detector = Play1ProjectDetector()

    @Test
    fun `detects standard Play 1 project with all criteria`() {
        val root = tempDir.root.toPath()
        createFile("conf/application.conf")
        createFile("conf/routes")
        createDir("app/controllers")

        val result = detector.detect(root)

        assertTrue("Should detect as Play 1", result.isPlay1)
        assertEquals(3, result.matchedCriteria.size)
        assertTrue(result.missingCriteria.isEmpty())
    }

    @Test
    fun `detects Play 1 project with only 2 strong criteria`() {
        val root = tempDir.root.toPath()
        createFile("conf/application.conf")
        createFile("conf/routes")
        // no app/controllers

        val result = detector.detect(root)

        assertTrue("2 criteria should be enough", result.isPlay1)
        assertEquals(2, result.matchedCriteria.size)
        assertEquals(1, result.missingCriteria.size)
    }

    @Test
    fun `does not detect project with only 1 criterion`() {
        val root = tempDir.root.toPath()
        createFile("conf/application.conf")

        val result = detector.detect(root)

        assertFalse("1 criterion should not be enough", result.isPlay1)
    }

    @Test
    fun `does not detect empty directory as Play 1`() {
        val root = tempDir.root.toPath()

        val result = detector.detect(root)

        assertFalse(result.isPlay1)
        assertEquals(3, result.missingCriteria.size)
    }

    @Test
    fun `does not detect Spring Boot project as Play 1`() {
        val root = tempDir.root.toPath()
        createFile("pom.xml")
        createFile("src/main/resources/application.properties")
        createDir("src/main/java/com/example")

        val result = detector.detect(root)

        assertFalse(result.isPlay1)
    }

    @Test
    fun `companion object isPlay1Project works`() {
        val root = tempDir.root.toPath()
        createFile("conf/application.conf")
        createFile("conf/routes")
        createDir("app/controllers")

        assertTrue(Play1ProjectDetector.isPlay1Project(root))
    }

    private fun createFile(relativePath: String) {
        val file = File(tempDir.root, relativePath)
        file.parentFile.mkdirs()
        file.createNewFile()
    }

    private fun createDir(relativePath: String) {
        File(tempDir.root, relativePath).mkdirs()
    }
}
