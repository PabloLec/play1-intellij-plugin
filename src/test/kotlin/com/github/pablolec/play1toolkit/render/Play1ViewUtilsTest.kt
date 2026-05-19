package com.github.pablolec.play1toolkit.render

import org.junit.Assert.assertEquals
import org.junit.Test

class Play1ViewUtilsTest {

    @Test
    fun `implicitViewPath for simple controller and action`() {
        assertEquals("app/views/Application/index.html", Play1ViewUtils.implicitViewPath("Application", "index"))
    }

    @Test
    fun `implicitViewPath for different controller`() {
        assertEquals("app/views/Posts/show.html", Play1ViewUtils.implicitViewPath("Posts", "show"))
    }

    @Test
    fun `implicitViewPath for admin controller`() {
        assertEquals("app/views/Admin/dashboard.html", Play1ViewUtils.implicitViewPath("Admin", "dashboard"))
    }

    @Test
    fun `implicitViewPath for action with long name`() {
        assertEquals("app/views/Patients/listByTag.html", Play1ViewUtils.implicitViewPath("Patients", "listByTag"))
    }

    @Test
    fun `implicitViewPath fixture view exists for Application index`() {
        val resource = javaClass.classLoader.getResource("fixtures/play1-standard/app/views/Application/index.html")
        assertNotNull("index.html fixture should exist", resource)
    }

    @Test
    fun `implicitViewPath fixture view exists for Application show`() {
        val resource = javaClass.classLoader.getResource("fixtures/play1-standard/app/views/Application/show.html")
        assertNotNull("show.html fixture should exist", resource)
    }

    private fun assertNotNull(message: String, value: Any?) {
        if (value == null) throw AssertionError(message)
    }
}
