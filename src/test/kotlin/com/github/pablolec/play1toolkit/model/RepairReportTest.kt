package com.github.pablolec.play1toolkit.model

import org.junit.Assert.*
import org.junit.Test

class RepairReportTest {

    @Test
    fun `report with all OK has no errors`() {
        val report = RepairReport("myapp")
        report.ok("Play project", "detected")
        report.ok("Play home", "/opt/play-1.2.7")
        report.ok("Library", "attached")

        assertFalse(report.hasErrors)
    }

    @Test
    fun `report with error has errors`() {
        val report = RepairReport("myapp")
        report.ok("Play project", "detected")
        report.error("Play home", "not configured")

        assertTrue(report.hasErrors)
    }

    @Test
    fun `toText contains project name and status OK`() {
        val report = RepairReport("testapp")
        report.ok("Play project", "detected")
        report.ok("Library", "attached")

        val text = report.toText()

        assertTrue(text.contains("testapp"))
        assertTrue(text.contains("Status: OK"))
        assertFalse(text.contains("ERRORS"))
    }

    @Test
    fun `toText shows ERRORS when report has error`() {
        val report = RepairReport("testapp")
        report.error("Play home", "missing")

        val text = report.toText()

        assertTrue(text.contains("ERRORS FOUND"))
    }

    @Test
    fun `skipped items appear with dash`() {
        val report = RepairReport("testapp")
        report.skipped("Framework sources", "not found")

        val text = report.toText()
        assertTrue(text.contains("–"))
        assertTrue(text.contains("Framework sources"))
    }
}
