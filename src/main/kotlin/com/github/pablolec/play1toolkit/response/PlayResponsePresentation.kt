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
                val labels = info.outcomes
                    .map { it.kind }
                    .filter { it !in setOf(PlayResponseKind.STATUS, PlayResponseKind.ERROR, PlayResponseKind.UNKNOWN) }
                    .distinct()
                    .joinToString(" | ") { shortLabel(it) }
                "Response: Mixed: $labels"
            }

            else -> {
                val first = info.outcomes.firstOrNull { it.kind == info.kind } ?: info.outcomes.first()
                val detail = first.details?.let { ": $it" } ?: ""
                val via = first.callText?.let { " via $it" } ?: ""
                "Response: ${shortLabel(info.kind)}$detail$via"
            }
        }

        val secondary = info.outcomes
            .filter { it.kind != info.kind || info.kind == PlayResponseKind.MIXED }
            .mapNotNull { outcome ->
                when (outcome.kind) {
                    PlayResponseKind.STATUS -> outcome.statusCode?.let { "May also return HTTP $it" }
                    PlayResponseKind.ERROR -> "May also return HTTP 500"
                    PlayResponseKind.UNKNOWN -> null
                    PlayResponseKind.MIXED -> null
                    else -> if (info.kind == PlayResponseKind.MIXED) {
                        val via = outcome.callText?.let { " via $it" } ?: ""
                        "${shortLabel(outcome.kind)}${outcome.details?.let { ": $it" } ?: ""}$via"
                    } else {
                        null
                    }
                }
            }
            .distinct()

        return (listOf(primary) + secondary).joinToString("\n")
    }
}
