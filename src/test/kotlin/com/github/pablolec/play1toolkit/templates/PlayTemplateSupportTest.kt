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

    @Test
    fun `bare action ref detects Controller action inside tag arguments`() {
        val template = """
            var route1 = #{custom.jsReversibleAction @login.LoginCtl.getConnectionState()/}
            var route2 = #{custom.jsReversibleAction @organization.SiteCtl.getImage(':siteId', ':defaultImageType') /}
            var route3 = #{custom.jsReversibleAction @login.LoginCtl.administratorLoginQRCode(':qrcode', ':rememberAccount')/}
            var route4 = #{custom.jsReversibleAction @login.LoginCtl.userLoginQRCode( ':qrcode')/}
            var route5 = #{custom.jsReversibleAction @login.LoginCtl.getAllSitesByUserId() /}
        """.trimIndent()

        val matches = PlayTemplatePatterns.BARE_ACTION_REF.findAll(template).toList()
        assertEquals(5, matches.size)
        assertEquals("login.LoginCtl.getConnectionState", matches[0].groupValues[1])
        assertEquals("organization.SiteCtl.getImage", matches[1].groupValues[1])
        assertEquals("login.LoginCtl.administratorLoginQRCode", matches[2].groupValues[1])
        assertEquals("login.LoginCtl.userLoginQRCode", matches[3].groupValues[1])
        assertEquals("login.LoginCtl.getAllSitesByUserId", matches[4].groupValues[1])
    }

    @Test
    fun `bare action ref does not match standard reverse routes`() {
        val template = "@{Users.show(user.id)}"
        val matches = PlayTemplatePatterns.BARE_ACTION_REF.findAll(template).toList()
        assertEquals(0, matches.size)
    }

    @Test
    fun `bare action ref does not match absolute reverse routes`() {
        val template = "@@{Users.show(user.id)}"
        val matches = PlayTemplatePatterns.BARE_ACTION_REF.findAll(template).toList()
        assertEquals(0, matches.size)
    }

    @Test
    fun `bare action ref matches tag argument with spaces`() {
        val template = "#{myTag @AdminCtl.index() /}"
        val match = PlayTemplatePatterns.BARE_ACTION_REF.find(template)
        assertEquals("AdminCtl.index", match!!.groupValues[1])
    }

    @Test
    fun `bare action ref captures arguments text`() {
        val template = "#{tag @login.LoginCtl.action(':param1', ':param2') /}"
        val match = PlayTemplatePatterns.BARE_ACTION_REF.find(template)
        assertEquals("login.LoginCtl.action", match!!.groupValues[1])
        assertEquals("':param1', ':param2'", match.groupValues[2])
    }

    @Test
    fun `tag name at detects namespaced custom tags`() {
        val template = "#{custom.jsReversibleAction @login.LoginCtl.getConnectionState()/}"
        val tagMatches = PlayTemplatePatterns.TAG_NAME_AT.findAll(template).toList()
        assertEquals(1, tagMatches.size)
        assertEquals("custom.jsReversibleAction", tagMatches[0].groupValues[1])
    }

    @Test
    fun `both REVERSE_ROUTE and BARE_ACTION_REF coexist without false matches`() {
        val template = """
            #{extends 'main.html' /}
            @{Users.show(user.id)}
            #{custom.jsReversibleAction @login.LoginCtl.getConnectionState()/}
            @{'/public/stylesheets/main.css'}
        """.trimIndent()

        val reverseRoutes = PlayTemplatePatterns.REVERSE_ROUTE.findAll(template).toList()
        assertEquals("Users.show", reverseRoutes[0].groupValues[1])
        assertEquals(1, reverseRoutes.count { it.groupValues[1].contains("Users.show") })

        val bareActionRefs = PlayTemplatePatterns.BARE_ACTION_REF.findAll(template).toList()
        assertEquals(1, bareActionRefs.size)
        assertEquals("login.LoginCtl.getConnectionState", bareActionRefs[0].groupValues[1])
    }
}
