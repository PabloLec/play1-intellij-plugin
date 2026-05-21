package com.github.pablolec.play1toolkit.templates.inspection

import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayTemplateTagBalanceInspectionTest {

    private data class TagError(val offset: Int, val message: String)

    private fun findBalanceErrors(text: String): List<TagError> {
        val matches = mutableListOf<Pair<String, MatchResult>>()
        PlayTemplatePatterns.TAG_SELF_CLOSE.findAll(text).forEach { matches += "self" to it }
        PlayTemplatePatterns.TAG_OPEN.findAll(text).forEach { matches += "open" to it }
        PlayTemplatePatterns.TAG_CLOSE.findAll(text).forEach { matches += "close" to it }

        val stack = mutableListOf<Pair<String, Int>>()
        val errors = mutableListOf<TagError>()

        matches.sortedBy { it.second.range.first }.forEach { (kind, match) ->
            when (kind) {
                "self" -> Unit
                "open" -> {
                    val tagName = match.groupValues[1]
                    if (match.value.trimEnd().endsWith("/}")) return@forEach
                    stack += tagName to match.range.first
                }
                "close" -> {
                    val tagName = match.groupValues[1]
                    if (tagName == "if") {
                        while (stack.lastOrNull()?.first in setOf("else", "elseif")) {
                            stack.removeLast()
                        }
                    }
                    val last = stack.lastOrNull()
                    if (last == null || last.first != tagName) {
                        errors += TagError(
                            match.range.first + 3,
                            "Unexpected closing Play tag: #{$tagName}"
                        )
                    } else {
                        stack.removeLast()
                    }
                }
            }
        }

        stack.forEach { (tagName, absOffset) ->
            errors += TagError(absOffset + 2, "Unclosed Play tag: #{$tagName}")
        }

        return errors
    }

    @Test
    fun `matching open and close tags are balanced`() {
        val text = """
            #{if condition}
                content
            #{/if}
        """.trimIndent()
        assertEquals(emptyList<TagError>(), findBalanceErrors(text))
    }

    @Test
    fun `unmatched close tag is reported`() {
        val text = """
            #{if condition}
                content
            #{/if}
            #{/nonexistent}
        """.trimIndent()
        val errors = findBalanceErrors(text)
        assertEquals(1, errors.size)
        assertEquals("Unexpected closing Play tag: #{nonexistent}", errors[0].message)
    }

    @Test
    fun `unclosed open tag is reported`() {
        val text = """
            #{list items:users, as:'user'}
                ${'$'}{user}
        """.trimIndent()
        val errors = findBalanceErrors(text)
        assertEquals(1, errors.size)
        assertEquals("Unclosed Play tag: #{list}", errors[0].message)
    }

    @Test
    fun `else and elseif inside if block are ignored as open tags`() {
        val text = """
            #{if condition}
                a
            #{elseif other}
                b
            #{else}
                c
            #{/if}
        """.trimIndent()
        assertEquals(emptyList<TagError>(), findBalanceErrors(text))
    }

    @Test
    fun `closing else inside if block is silently accepted`() {
        val text = """
            #{if isCached == true}
                #{cache "key", for:"10mn"}
                    #{depenses.depensesScreen /}
                #{/cache}
            #{/if}
            #{else}
                #{depenses.depensesScreen /}
            #{/else}
        """.trimIndent()
        assertEquals(emptyList<TagError>(), findBalanceErrors(text))
    }

    @Test
    fun `closing elseif inside if block is silently accepted`() {
        val text = """
            #{if a}
                x
            #{elseif b}
                y
            #{/elseif}
            #{else}
                z
            #{/if}
        """.trimIndent()
        assertEquals(emptyList<TagError>(), findBalanceErrors(text))
    }

    @Test
    fun `closing else is rejected when not inside if`() {
        val text = """
            #{/else}
        """.trimIndent()
        val errors = findBalanceErrors(text)
        assertEquals(1, errors.size)
        assertEquals("Unexpected closing Play tag: #{else}", errors[0].message)
    }

    @Test
    fun `closing else is rejected when inside list`() {
        val text = """
            #{list items:users, as:'user'}
            #{/else}
            #{/list}
        """.trimIndent()
        val errors = findBalanceErrors(text)
        assertEquals(1, errors.size)
        assertEquals("Unexpected closing Play tag: #{else}", errors[0].message)
    }

    @Test
    fun `nested if blocks with else work correctly`() {
        val text = """
            #{if a}
                #{if b}
                    inner
                #{/if}
            #{else}
                outer else
            #{/if}
        """.trimIndent()
        assertEquals(emptyList<TagError>(), findBalanceErrors(text))
    }

    @Test
    fun `self-closing tags are ignored`() {
        val text = """
            #{extends 'main.html' /}
            #{include 'header.html' /}
            #{userCard user:user /}
        """.trimIndent()
        assertEquals(emptyList<TagError>(), findBalanceErrors(text))
    }
}
