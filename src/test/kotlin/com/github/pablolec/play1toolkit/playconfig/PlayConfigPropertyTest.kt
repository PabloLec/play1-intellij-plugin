package com.github.pablolec.play1toolkit.playconfig

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLexer
import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigTokenTypes
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for rawKey → profile + logicalKey decomposition logic.
 * Exercises the parsing rules used by PlayConfigProperty.
 */
class PlayConfigPropertyTest {

    private fun parseRawKey(rawKey: String): Pair<String?, String> {
        // Mirrors the logic in PlayConfigProperty
        val profile: String?
        val logicalKey: String
        if (rawKey.startsWith("%")) {
            val dotIdx = rawKey.indexOf('.')
            profile = if (dotIdx > 1) rawKey.substring(1, dotIdx) else null
            logicalKey = if (dotIdx >= 0 && dotIdx < rawKey.length - 1) rawKey.substring(dotIdx + 1) else rawKey
        } else {
            profile = null
            logicalKey = rawKey
        }
        return profile to logicalKey
    }

    @Test
    fun `simple key has no profile`() {
        val (profile, key) = parseRawKey("db.url")
        assertNull(profile)
        assertEquals("db.url", key)
    }

    @Test
    fun `profiled key extracts profile`() {
        val (profile, key) = parseRawKey("%docker.db.url")
        assertEquals("docker", profile)
        assertEquals("db.url", key)
    }

    @Test
    fun `linux profile key`() {
        val (profile, key) = parseRawKey("%linux.db.url")
        assertEquals("linux", profile)
        assertEquals("db.url", key)
    }

    @Test
    fun `dev profile key`() {
        val (profile, key) = parseRawKey("%dev.application.mode")
        assertEquals("dev", profile)
        assertEquals("application.mode", key)
    }

    @Test
    fun `test-linux profile key with hyphen`() {
        val (profile, key) = parseRawKey("%test-linux.db.url")
        assertEquals("test-linux", profile)
        assertEquals("db.url", key)
    }

    @Test
    fun `multi-segment logical key is preserved`() {
        val (profile, key) = parseRawKey("%prod.application.session.maxAge")
        assertEquals("prod", profile)
        assertEquals("application.session.maxAge", key)
    }

    @Test
    fun `simple multi-segment key`() {
        val (profile, key) = parseRawKey("application.session.maxAge")
        assertNull(profile)
        assertEquals("application.session.maxAge", key)
    }

    // --- Lexer integration: profile key is tokenized as one KEY token ---

    @Test
    fun `profiled key tokenized as single KEY token`() {
        val lexer = PlayConfigLexer()
        val input = "%docker.db.url=\${DATABASE_URL}\n"
        lexer.start(input, 0, input.length, PlayConfigLexer.STATE_LINE_START)
        val firstToken = lexer.tokenType
        assertEquals(PlayConfigTokenTypes.KEY, firstToken)
        val keyText = input.substring(lexer.tokenStart, lexer.tokenEnd)
        assertEquals("%docker.db.url", keyText)
    }
}
