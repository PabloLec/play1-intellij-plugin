package com.github.pablolec.play1toolkit.detection

import java.nio.file.Files
import java.nio.file.Path

/**
 * Detects whether a directory looks like a Play Framework 1.x project.
 * This class has no IntelliJ dependencies and can be tested with plain JUnit.
 */
class Play1ProjectDetector {

    data class DetectionResult(
        val isPlay1: Boolean,
        val matchedCriteria: List<String>,
        val missingCriteria: List<String>
    )

    fun detect(projectRoot: Path): DetectionResult {
        val strongCriteria = listOf(
            Pair("conf/application.conf", projectRoot.resolve("conf/application.conf")),
            Pair("conf/routes", projectRoot.resolve("conf/routes")),
            Pair("app/controllers/", projectRoot.resolve("app/controllers"))
        )

        val matched = strongCriteria.filter { (_, path) ->
            Files.exists(path)
        }.map { it.first }

        val missing = strongCriteria.filter { (_, path) ->
            !Files.exists(path)
        }.map { it.first }

        return DetectionResult(
            isPlay1 = matched.size >= 2,
            matchedCriteria = matched,
            missingCriteria = missing
        )
    }

    companion object {
        fun isPlay1Project(projectRoot: Path): Boolean =
            Play1ProjectDetector().detect(projectRoot).isPlay1
    }
}
