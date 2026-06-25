package com.github.pablolec.play1toolkit.run

import org.jdom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Play1ApplicationRunConfigurationTest {

    @Test
    fun `stripMakeBeforeRunTask removes IntelliJ make step`() {
        val configuration = Element("configuration")
        val method = Element("method").setAttribute("v", "2")
        method.addContent(Element("option").setAttribute("name", "Make").setAttribute("enabled", "true"))
        method.addContent(Element("option").setAttribute("name", "Custom").setAttribute("enabled", "true"))
        configuration.addContent(method)

        Play1ApplicationRunConfiguration.stripMakeBeforeRunTask(configuration)

        assertNull(method.children.filterIsInstance<Element>().firstOrNull { it.getAttributeValue("name") == "Make" })
        assertEquals("Custom", method.children.filterIsInstance<Element>().single().getAttributeValue("name"))
    }
}
