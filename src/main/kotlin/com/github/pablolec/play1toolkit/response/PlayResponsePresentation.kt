package com.github.pablolec.play1toolkit.response

object PlayResponsePresentation {

    fun shortLabel(kind: PlayResponseKind): String = when (kind) {
        PlayResponseKind.HTML -> "HTML"
        PlayResponseKind.JSON -> "JSON"
        PlayResponseKind.XML -> "XML"
        PlayResponseKind.TEXT -> "TXT"
        PlayResponseKind.BINARY -> "BIN"
        PlayResponseKind.REDIRECT -> "302"
        PlayResponseKind.STATUS -> "HTTP"
        PlayResponseKind.ERROR -> "ERR"
        PlayResponseKind.MIXED -> "MIX"
        PlayResponseKind.UNKNOWN -> "?"
    }

    fun tooltip(info: PlayEndpointResponseInfo): String {
        if (info.kind == PlayResponseKind.UNKNOWN || info.outcomes.isEmpty()) {
            return "Response: Unknown"
        }

        val primary = when (info.kind) {
            PlayResponseKind.MIXED -> {
                val labels = info.primaryOutcomes
                    .map { it.kind }
                    .distinct()
                    .joinToString(" | ") { shortLabel(it) }
                "Response: Mixed: $labels"
            }

            else -> {
                val first = info.primaryOutcomes.firstOrNull { it.kind == info.kind }
                    ?: info.statusOutcomes.firstOrNull()
                    ?: info.outcomes.first()
                val detail = first.details?.let { ": $it" } ?: ""
                val via = first.callText?.let { " via $it" } ?: ""
                "Response: ${shortLabel(info.kind)}$detail$via"
            }
        }

        val nominalStatus = if (info.kind == PlayResponseKind.MIXED || info.primaryOutcomes.isEmpty()) {
            null
        } else {
            info.statusOutcomes
                .mapNotNull { it.statusCode }
                .firstOrNull { it in 200..399 }
                ?.let { "Nominal HTTP status: $it" }
        }

        val mixedPrimaryDetails = if (info.kind != PlayResponseKind.MIXED) {
            emptyList()
        } else {
            info.primaryOutcomes
                .map { outcome ->
                    val via = outcome.callText?.let { " via $it" } ?: ""
                    "${shortLabel(outcome.kind)}${outcome.details?.let { ": $it" } ?: ""}$via"
                }
                .distinct()
        }

        val secondaryStatuses = info.statusOutcomes
            .mapNotNull { outcome ->
                val status = outcome.statusCode ?: return@mapNotNull null
                if (info.primaryOutcomes.isNotEmpty() && status in 200..399) null else "May also return HTTP $status"
            }
            .distinct()

        return (listOfNotNull(primary, nominalStatus) + mixedPrimaryDetails + secondaryStatuses).joinToString("\n")
    }
}
