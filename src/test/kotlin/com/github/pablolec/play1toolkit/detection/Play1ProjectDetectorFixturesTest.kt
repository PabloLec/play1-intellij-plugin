package com.github.pablolec.play1toolkit.detection

import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import java.nio.file.Paths

/**
 * Tests Play1ProjectDetector against the static fixture directories in test/resources.
 * These tests validate that the fixtures themselves represent the expected project types.
 */
class Play1ProjectDetectorFixturesTest {

    private val detector = Play1ProjectDetector()

    private fun fixtureRoot(name: String): java.nio.file.Path? {
        val resource: URL = javaClass.classLoader.getResource("fixtures/$name") ?: return null
        return Paths.get(resource.toURI())
    }

    @Test
    fun `play1-standard fixture is detected as Play 1 project`() {
        val root = fixtureRoot("play1-standard") ?: return // skip if fixture not found
        val result = detector.detect(root)
        assertTrue(
            "play1-standard fixture should be detected as Play 1 (matched: ${result.matchedCriteria})",
            result.isPlay1
        )
    }

    @Test
    fun `play1-minimal fixture is detected as Play 1 project`() {
        val root = fixtureRoot("play1-minimal") ?: return
        val result = detector.detect(root)
        assertTrue(
            "play1-minimal fixture should be detected as Play 1 (matched: ${result.matchedCriteria})",
            result.isPlay1
        )
    }

    @Test
    fun `not-play1 fixture is NOT detected as Play 1 project`() {
        val root = fixtureRoot("not-play1") ?: return
        val result = detector.detect(root)
        assertFalse(
            "not-play1 fixture should not be detected as Play 1",
            result.isPlay1
        )
    }

    @Test
    fun `play1-standard fixture has all 3 strong criteria`() {
        val root = fixtureRoot("play1-standard") ?: return
        val result = detector.detect(root)
        assertTrue(
            "play1-standard should match all 3 strong criteria",
            result.matchedCriteria.containsAll(listOf("conf/application.conf", "conf/routes", "app/controllers/"))
        )
    }
}
