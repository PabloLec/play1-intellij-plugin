package com.github.pablolec.play1toolkit.playmessages

import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesLexer
import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesTokenTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayMessagesLexerTest {

    private fun lex(input: String): List<Pair<String, String>> {
        val lexer = PlayMessagesLexer()
        lexer.start(input, 0, input.length, PlayMessagesLexer.STATE_LINE_START)
        val result = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            result.add(lexer.tokenType!!.toString() to input.substring(lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return result
    }

    @Test
    fun `simple key-value`() {
        val tokens = lex("hello=Hello World\n")
        val types = tokens.map { it.first }
        assert(types.contains(PlayMessagesTokenTypes.KEY.toString()))
        assert(types.contains(PlayMessagesTokenTypes.SEPARATOR.toString()))
        assert(types.contains(PlayMessagesTokenTypes.VALUE.toString()))
        assert(types.contains(PlayMessagesTokenTypes.NEWLINE.toString()))
    }

    @Test
    fun `key-value with spaces around equals`() {
        val tokens = lex("hello = World\n")
        val types = tokens.map { it.first }
        assert(types.contains(PlayMessagesTokenTypes.KEY.toString()))
        assert(types.contains(PlayMessagesTokenTypes.SEPARATOR.toString()))
        assert(types.contains(PlayMessagesTokenTypes.VALUE.toString()))
    }

    @Test
    fun `comment with hash`() {
        val tokens = lex("# This is a comment\n")
        assertEquals(PlayMessagesTokenTypes.COMMENT.toString(), tokens[0].first)
    }

    @Test
    fun `comment with semicolon`() {
        val tokens = lex("; Also a comment\n")
        assertEquals(PlayMessagesTokenTypes.COMMENT.toString(), tokens[0].first)
    }

    @Test
    fun `empty value`() {
        val tokens = lex("key=\n")
        val types = tokens.map { it.first }
        assert(types.contains(PlayMessagesTokenTypes.KEY.toString()))
        assert(types.contains(PlayMessagesTokenTypes.SEPARATOR.toString()))
        assert(!types.contains(PlayMessagesTokenTypes.VALUE.toString()))
        assert(types.contains(PlayMessagesTokenTypes.NEWLINE.toString()))
    }

    @Test
    fun `blank line`() {
        val tokens = lex("\n")
        assertEquals(1, tokens.size)
        assertEquals(PlayMessagesTokenTypes.NEWLINE.toString(), tokens[0].first)
    }

    @Test
    fun `single placeholder string specifier`() {
        val tokens = lex("greeting=Hello %s!\n")
        val types = tokens.map { it.first }
        assert(types.contains(PlayMessagesTokenTypes.PLACEHOLDER.toString()))
        val placeholderToken = tokens.first { it.first == PlayMessagesTokenTypes.PLACEHOLDER.toString() }
        assertEquals("%s", placeholderToken.second)
    }

    @Test
    fun `multiple format specifiers`() {
        val tokens = lex("msg=Dear %s, you have %d items\n")
        val placeholders = tokens.filter { it.first == PlayMessagesTokenTypes.PLACEHOLDER.toString() }
        assertEquals(2, placeholders.size)
        assertEquals("%s", placeholders[0].second)
        assertEquals("%d", placeholders[1].second)
    }

    @Test
    fun `escaped percent is not a placeholder`() {
        val tokens = lex("pct=100%%\n")
        val types = tokens.map { it.first }
        assert(!types.contains(PlayMessagesTokenTypes.PLACEHOLDER.toString()))
        // %% should be emitted as VALUE
        val valueTokens = tokens.filter { it.first == PlayMessagesTokenTypes.VALUE.toString() }
        assert(valueTokens.any { it.second.contains("%%") || it.second == "100" })
    }

    @Test
    fun `key with dots`() {
        val tokens = lex("app.error.notFound=Not found\n")
        val keyToken = tokens.first { it.first == PlayMessagesTokenTypes.KEY.toString() }
        assertEquals("app.error.notFound", keyToken.second)
    }

    @Test
    fun `multi-line file without errors`() {
        val input = "# comment\nhello=Hello\nworld=World\n"
        val tokens = lex(input)
        val badChars = tokens.filter { it.first == PlayMessagesTokenTypes.BAD_CHARACTER.toString() }
        assertEquals(0, badChars.size)
    }
}
