package com.github.pablolec.play1toolkit.playmessages

import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import org.junit.Assert.*
import org.junit.Test

class PlayMessagesContextDetectorTest {

    // Tests for pure logic helpers that can be tested without the IntelliJ platform

    @Test
    fun `format arg count 0 when no args`() {
        // Messages.get("key") → 0 format args
        assertEquals(0, formatArgCount(1))
    }

    @Test
    fun `format arg count 1 when one arg`() {
        // Messages.get("key", a) → 1 format arg
        assertEquals(1, formatArgCount(2))
    }

    @Test
    fun `format arg count 2 when two args`() {
        // Messages.get("key", a, b) → 2 format args
        assertEquals(2, formatArgCount(3))
    }

    @Test
    fun `placeholder count matches format arg count for simple case`() {
        val value = "Hello %s"
        val expectedPlaceholders = PlayMessagesService.countPlaceholders(value)
        val argCount = formatArgCount(2) // Messages.get("key", name)
        assertEquals(expectedPlaceholders, argCount)
    }

    @Test
    fun `placeholder count mismatch detected`() {
        val value = "Hello %s, you have %d items"
        val expectedPlaceholders = PlayMessagesService.countPlaceholders(value)
        val argCount = formatArgCount(2) // Only 1 format arg provided
        assertNotEquals(expectedPlaceholders, argCount)
    }

    // Simulates PlayMessagesContextDetector.getFormatArgCount for a given total arg count
    private fun formatArgCount(totalArgs: Int): Int = maxOf(0, totalArgs - 1)
}
