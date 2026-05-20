package com.github.pablolec.play1toolkit.templates

import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayTemplateSupportTest {

    @Test
    fun `normalizeTemplatePath strips play views prefixes`() {
        assertEquals("Users/show.html", PlayTemplateFileUtils.normalizeTemplatePath("/app/views/Users/show.html"))
        assertEquals("Users/show.html", PlayTemplateFileUtils.normalizeTemplatePath("app/views/Users/show.html"))
        assertEquals("Users/show.html", PlayTemplateFileUtils.normalizeTemplatePath("Users/show.html"))
    }

    @Test
    fun `template logical path conventions resolve controller and action`() {
        assertEquals("Users", PlayTemplateFileUtils.controllerNameFromLogicalPath("Users/show.html"))
        assertEquals("show", PlayTemplateFileUtils.actionNameFromLogicalPath("Users/show.html"))
        assertEquals("Details", PlayTemplateFileUtils.titleFromTemplateFileName("details.html"))
    }

    @Test
    fun `tag logical path is converted to qualified tag name`() {
        assertEquals("userCard", PlayTemplateFileUtils.tagQualifiedName("tags/userCard.html"))
        assertEquals("layout.menu", PlayTemplateFileUtils.tagQualifiedName("tags/layout/menu.html"))
    }

    @Test
    fun `play template regexes detect core constructs`() {
        val template = """
            #{extends 'main.html' /}
            #{include 'Users/_row.html' /}
            #{list items:users, as:'user'}
                ${'$'}{user.name}
            #{/list}
            #{userCard user:user /}
            @{Users.show(user.id)}
            @{'/public/stylesheets/main.css'}
        """.trimIndent()

        assertEquals("main.html", PlayTemplatePatterns.TAG_EXTENDS.find(template)!!.groupValues[1])
        assertEquals("Users/_row.html", PlayTemplatePatterns.TAG_INCLUDE.find(template)!!.groupValues[1])
        assertEquals("user", PlayTemplatePatterns.LIST_TAG_VAR.find(template)!!.groupValues[1])
        assertEquals("Users.show", PlayTemplatePatterns.REVERSE_ROUTE.find(template)!!.groupValues[1])
        assertEquals("/public/stylesheets/main.css", PlayTemplatePatterns.STATIC_ASSET.find(template)!!.groupValues[1])
        assertTrue(PlayTemplatePatterns.TAG_NAME_AT.findAll(template).any { it.groupValues[1] == "userCard" })
    }

    @Test
    fun `custom tag parameter regex detects underscored variables`() {
        val tagTemplate = """
            <div>${'$'}{_user}</div>
            #{if _editable}
                <span>${'$'}{_label}</span>
            #{/if}
        """.trimIndent()

        val parameters = PlayTemplatePatterns.TAG_PARAM.findAll(tagTemplate).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("user", "label"), parameters)
    }
}
