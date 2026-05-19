package com.github.pablolec.play1toolkit.routes

import org.junit.Assert.*
import org.junit.Test

class RoutesLexerTest {

    private fun tokenize(input: String): List<Pair<String, String>> {
        val lexer = RoutesLexer()
        lexer.start(input, 0, input.length, RoutesLexer.STATE_LINE_START)
        val tokens = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!.toString()
            val text = input.substring(lexer.tokenStart, lexer.tokenEnd)
            tokens.add(type to text)
            lexer.advance()
        }
        return tokens
    }

    private fun tokenTypes(input: String) = tokenize(input).map { it.first }

    // ── Comment ────────────────────────────────────────────────────────────────

    @Test
    fun `comment line produces COMMENT token`() {
        val tokens = tokenize("# This is a comment")
        assertEquals(1, tokens.size)
        assertEquals("RoutesTokenType.COMMENT", tokens[0].first)
        assertEquals("# This is a comment", tokens[0].second)
    }

    @Test
    fun `blank line produces NEWLINE token`() {
        val tokens = tokenize("\n")
        assertEquals(1, tokens.size)
        assertEquals("RoutesTokenType.NEWLINE", tokens[0].first)
    }

    // ── Simple GET route ───────────────────────────────────────────────────────

    @Test
    fun `GET route produces correct token sequence`() {
        val types = tokenTypes("GET     /login              Security.authenticate\n")
        // HTTP_METHOD WS PATH WS CONTROLLER_NAME DOT ACTION_NAME NEWLINE
        assertTrue(types.contains("RoutesTokenType.HTTP_METHOD"))
        assertTrue(types.contains("RoutesTokenType.PATH"))
        assertTrue(types.contains("RoutesTokenType.CONTROLLER_NAME"))
        assertTrue(types.contains("RoutesTokenType.DOT"))
        assertTrue(types.contains("RoutesTokenType.ACTION_NAME"))
    }

    @Test
    fun `GET route HTTP_METHOD text is GET`() {
        val tokens = tokenize("GET /login Application.index\n")
        val method = tokens.first { it.first == "RoutesTokenType.HTTP_METHOD" }
        assertEquals("GET", method.second)
    }

    @Test
    fun `GET route PATH text is correct`() {
        val tokens = tokenize("GET /login Application.index\n")
        val path = tokens.first { it.first == "RoutesTokenType.PATH" }
        assertEquals("/login", path.second)
    }

    @Test
    fun `GET route CONTROLLER_NAME is correct`() {
        val tokens = tokenize("GET /login Application.index\n")
        val ctrl = tokens.first { it.first == "RoutesTokenType.CONTROLLER_NAME" }
        assertEquals("Application", ctrl.second)
    }

    @Test
    fun `GET route ACTION_NAME is correct`() {
        val tokens = tokenize("GET /login Application.index\n")
        val action = tokens.first { it.first == "RoutesTokenType.ACTION_NAME" }
        assertEquals("index", action.second)
    }

    // ── Wildcard and POST ──────────────────────────────────────────────────────

    @Test
    fun `wildcard method is tokenized as HTTP_METHOD`() {
        val tokens = tokenize("*   /admin  Admin.panel\n")
        assertEquals("RoutesTokenType.HTTP_METHOD", tokens[0].first)
        assertEquals("*", tokens[0].second)
    }

    @Test
    fun `POST route is correctly tokenized`() {
        val types = tokenTypes("POST /login Security.authenticate\n")
        assertTrue(types.contains("RoutesTokenType.HTTP_METHOD"))
        assertTrue(types.contains("RoutesTokenType.CONTROLLER_NAME"))
        assertTrue(types.contains("RoutesTokenType.ACTION_NAME"))
    }

    // ── Path parameters ────────────────────────────────────────────────────────

    @Test
    fun `path with param produces PATH_PARAM token`() {
        val tokens = tokenize("GET /patients/{id} Patients.show\n")
        assertTrue(tokens.any { it.first == "RoutesTokenType.PATH_PARAM" && it.second == "{id}" })
    }

    @Test
    fun `path with regex param produces PATH_PARAM token`() {
        val tokens = tokenize("GET /posts/{<[0-9]+>id} Posts.show\n")
        assertTrue(tokens.any { it.first == "RoutesTokenType.PATH_PARAM" })
        val param = tokens.first { it.first == "RoutesTokenType.PATH_PARAM" }
        assertTrue(param.second.startsWith("{"))
        assertTrue(param.second.endsWith("}"))
    }

    @Test
    fun `path parts around param are separate PATH tokens`() {
        val types = tokenTypes("GET /patients/{id}/info Patients.details\n")
        val paths = types.filter { it == "RoutesTokenType.PATH" }
        assertEquals(2, paths.size)
    }

    // ── staticDir and module ───────────────────────────────────────────────────

    @Test
    fun `staticDir route produces STATIC_REF token`() {
        val tokens = tokenize("GET /public/ staticDir:public\n")
        assertTrue(tokens.any { it.first == "RoutesTokenType.STATIC_REF" })
        val ref = tokens.first { it.first == "RoutesTokenType.STATIC_REF" }
        assertTrue(ref.second.startsWith("staticDir:"))
    }

    @Test
    fun `module route produces MODULE_REF token`() {
        val tokens = tokenize("*   /admin  module:crud\n")
        assertTrue(tokens.any { it.first == "RoutesTokenType.MODULE_REF" })
        val ref = tokens.first { it.first == "RoutesTokenType.MODULE_REF" }
        assertTrue(ref.second.startsWith("module:"))
    }

    // ── Multi-line tokenization ────────────────────────────────────────────────

    @Test
    fun `multiple routes are each tokenized correctly`() {
        val input = "GET /a Application.a\nPOST /b Application.b\n"
        val tokens = tokenize(input)
        val methods = tokens.filter { it.first == "RoutesTokenType.HTTP_METHOD" }
        assertEquals(2, methods.size)
        assertEquals("GET", methods[0].second)
        assertEquals("POST", methods[1].second)
    }

    @Test
    fun `routes file with comments and blank lines tokenized correctly`() {
        val input = """
            # Routes file
            GET     /       Application.index

            POST    /login  Security.authenticate
        """.trimIndent() + "\n"
        val types = tokenTypes(input)
        assertTrue(types.contains("RoutesTokenType.COMMENT"))
        val methods = tokenize(input).filter { it.first == "RoutesTokenType.HTTP_METHOD" }
        assertEquals(2, methods.size)
    }

    // ── Realistic sample ───────────────────────────────────────────────────────

    @Test
    fun `realistic routes file tokenizes without errors`() {
        val input = """
            # Application routes
            GET     /                       Application.index
            GET     /about                  Application.about
            POST    /login                  Security.authenticate
            GET     /patients/{id}          Patients.show
            *       /public/                staticDir:public
            *       /admin                  module:crud
        """.trimIndent() + "\n"
        val tokens = tokenize(input)
        assertTrue(tokens.none { it.first == "RoutesTokenType.BAD_CHARACTER" })
        val methods = tokens.filter { it.first == "RoutesTokenType.HTTP_METHOD" }
        assertEquals(6, methods.size)
    }
}
