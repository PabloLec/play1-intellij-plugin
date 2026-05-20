package com.github.pablolec.play1toolkit.playconfig

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLexer
import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigTokenTypes
import org.junit.Assert.*
import org.junit.Test

class PlayConfigLexerTest {

    private fun tokenize(input: String): List<Pair<String, String>> {
        val lexer = PlayConfigLexer()
        lexer.start(input, 0, input.length, PlayConfigLexer.STATE_LINE_START)
        val tokens = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            tokens.add(lexer.tokenType!!.toString() to input.substring(lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return tokens
    }

    private fun tokenTypes(input: String) = tokenize(input).map { it.first }
    private fun tokenTexts(input: String) = tokenize(input).map { it.second }

    private val KEY = PlayConfigTokenTypes.KEY.toString()
    private val SEPARATOR = PlayConfigTokenTypes.SEPARATOR.toString()
    private val VALUE = PlayConfigTokenTypes.VALUE.toString()
    private val COMMENT = PlayConfigTokenTypes.COMMENT.toString()
    private val ENV_PLACEHOLDER = PlayConfigTokenTypes.ENV_PLACEHOLDER.toString()
    private val NEWLINE = PlayConfigTokenTypes.NEWLINE.toString()
    private val WHITESPACE = PlayConfigTokenTypes.WHITESPACE.toString()

    @Test
    fun `simple property tokenizes correctly`() {
        val types = tokenTypes("application.mode=dev\n")
        assertTrue(types.contains(KEY))
        assertTrue(types.contains(SEPARATOR))
        assertTrue(types.contains(VALUE))
    }

    @Test
    fun `simple property key text is correct`() {
        val tokens = tokenize("application.mode=dev\n")
        val key = tokens.first { it.first == KEY }
        assertEquals("application.mode", key.second)
    }

    @Test
    fun `simple property value text is correct`() {
        val tokens = tokenize("application.mode=dev\n")
        val value = tokens.first { it.first == VALUE }
        assertEquals("dev", value.second)
    }

    @Test
    fun `property with spaces around equals tokenizes correctly`() {
        val types = tokenTypes("hibernate.show_sql = false\n")
        assertTrue(types.contains(KEY))
        assertTrue(types.contains(SEPARATOR))
        assertTrue(types.contains(VALUE))
    }

    @Test
    fun `property with spaces - key text is correct`() {
        val tokens = tokenize("hibernate.show_sql = false\n")
        val key = tokens.first { it.first == KEY }
        assertEquals("hibernate.show_sql", key.second)
    }

    @Test
    fun `property with spaces - value text is correct`() {
        val tokens = tokenize("hibernate.show_sql = false\n")
        val value = tokens.first { it.first == VALUE }
        assertEquals("false", value.second)
    }

    @Test
    fun `hash comment produces COMMENT token`() {
        val tokens = tokenize("# This is a comment\n")
        assertEquals(2, tokens.filter { it.first == COMMENT || it.first == NEWLINE }.size)
        assertTrue(tokens.any { it.first == COMMENT && it.second == "# This is a comment" })
    }

    @Test
    fun `semicolon comment produces COMMENT token`() {
        val tokens = tokenize("; This is also a comment\n")
        assertTrue(tokens.any { it.first == COMMENT })
    }

    @Test
    fun `profiled property key text is correct`() {
        val tokens = tokenize("%docker.db.url=jdbc:mysql://localhost/db\n")
        val key = tokens.first { it.first == KEY }
        assertEquals("%docker.db.url", key.second)
    }

    @Test
    fun `profiled property value text is correct`() {
        val tokens = tokenize("%docker.db.url=jdbc:mysql://localhost/db\n")
        val value = tokens.first { it.first == VALUE }
        assertEquals("jdbc:mysql://localhost/db", value.second)
    }

    @Test
    fun `env placeholder produces ENV_PLACEHOLDER token`() {
        val tokens = tokenize("%docker.db.url=\${DATABASE_URL}\n")
        assertTrue(tokens.any { it.first == ENV_PLACEHOLDER && it.second == "\${DATABASE_URL}" })
    }

    @Test
    fun `env placeholder text includes braces`() {
        val tokens = tokenize("db.pass=\${DB_PASS}\n")
        val env = tokens.first { it.first == ENV_PLACEHOLDER }
        assertEquals("\${DB_PASS}", env.second)
    }

    @Test
    fun `value with env placeholder has both VALUE and ENV_PLACEHOLDER tokens`() {
        val tokens = tokenize("db.url=jdbc:\${DB_URL}/dbname\n")
        assertTrue(tokens.any { it.first == ENV_PLACEHOLDER })
        assertTrue(tokens.any { it.first == VALUE })
    }

    @Test
    fun `empty value produces no VALUE token`() {
        val tokens = tokenize("job.force.debug=\n")
        assertFalse(tokens.any { it.first == VALUE })
        assertTrue(tokens.any { it.first == KEY && it.second == "job.force.debug" })
    }

    @Test
    fun `invalid line does not break subsequent valid line`() {
        val input = "!!!invalid!!!\ndb.url=valid\n"
        val tokens = tokenize(input)
        assertTrue(tokens.any { it.first == KEY && it.second == "db.url" })
        assertTrue(tokens.any { it.first == VALUE && it.second == "valid" })
    }

    @Test
    fun `multiple properties tokenize without errors`() {
        val input = """
            application.mode=dev
            %prod.application.mode=prod
            db.url=jdbc:mysql://localhost/db
            application.secret=verysecret
        """.trimIndent() + "\n"
        val tokens = tokenize(input)
        val keys = tokens.filter { it.first == KEY }
        assertEquals(4, keys.size)
    }

    @Test
    fun `linux profile property tokenizes correctly`() {
        val tokens = tokenize("%linux.db.url=jdbc:mysql://localhost:3306/gmvet\n")
        val key = tokens.first { it.first == KEY }
        assertEquals("%linux.db.url", key.second)
    }

    @Test
    fun `value with special chars tokenizes without BAD_CHARACTER`() {
        val input = "db.url=jdbc:mysql://host:3306/db?useSSL=false&allowPublicKeyRetrieval=true\n"
        val tokens = tokenize(input)
        assertFalse(tokens.any { it.first == "PlayConfigTokenType.BAD_CHARACTER" })
    }

    @Test
    fun `blank line produces NEWLINE`() {
        val tokens = tokenize("\n")
        assertEquals(1, tokens.size)
        assertEquals(NEWLINE, tokens[0].first)
    }
}
