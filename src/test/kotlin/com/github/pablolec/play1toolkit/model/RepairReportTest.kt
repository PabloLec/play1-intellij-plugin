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

    @Test
    fun `report with only skipped items is not an error`() {
        val report = RepairReport("testapp")
        report.skipped("Library attachment", "no module found")
        report.skipped("Source roots", "no module found")

        assertFalse("Skipped items must not trigger hasErrors", report.hasErrors)
        assertFalse(report.toText().contains("ERRORS FOUND"))
    }

    @Test
    fun `mixed ok and skipped does not show ERRORS FOUND`() {
        val report = RepairReport("testapp")
        report.ok("Play project", "detected")
        report.skipped("Library attachment", "no module found")
        report.ok("Run configuration", "created")

        assertFalse(report.hasErrors)
        assertTrue(report.toText().contains("Status: OK"))
    }

    @Test
    fun `single error among ok and skipped triggers ERRORS FOUND`() {
        val report = RepairReport("testapp")
        report.ok("Play project", "detected")
        report.skipped("Library attachment", "no module found")
        report.error("Play home", "invalid path")

        assertTrue(report.hasErrors)
        assertTrue(report.toText().contains("ERRORS FOUND"))
    }
}
