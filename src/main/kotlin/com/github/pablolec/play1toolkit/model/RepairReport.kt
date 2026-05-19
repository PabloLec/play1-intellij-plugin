package com.github.pablolec.play1toolkit.model

data class RepairReport(
    val projectName: String,
    val items: MutableList<ReportItem> = mutableListOf()
) {
    val hasErrors: Boolean get() = items.any { it.status == ReportStatus.ERROR }

    fun add(label: String, status: ReportStatus, detail: String = "") {
        items.add(ReportItem(label, status, detail))
    }

    fun ok(label: String, detail: String = "") = add(label, ReportStatus.OK, detail)
    fun error(label: String, detail: String = "") = add(label, ReportStatus.ERROR, detail)
    fun skipped(label: String, detail: String = "") = add(label, ReportStatus.SKIPPED, detail)

    fun toText(): String = buildString {
        appendLine("Play v1 Toolkit — Repair Report")
        appendLine()
        appendLine("Project: $projectName")
        appendLine()
        for (item in items) {
            val icon = when (item.status) {
                ReportStatus.OK -> "✓"
                ReportStatus.ERROR -> "✗"
                ReportStatus.SKIPPED -> "–"
            }
            append("$icon  ${item.label}")
            if (item.detail.isNotBlank()) append(": ${item.detail}")
            appendLine()
        }
        appendLine()
        appendLine(if (hasErrors) "Status: ERRORS FOUND" else "Status: OK")
    }
}

data class ReportItem(
    val label: String,
    val status: ReportStatus,
    val detail: String
)

enum class ReportStatus { OK, ERROR, SKIPPED }
