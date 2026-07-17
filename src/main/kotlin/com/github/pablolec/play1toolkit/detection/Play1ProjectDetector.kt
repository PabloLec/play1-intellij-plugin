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
        val projectRoot: Path?,
        val matchedCriteria: List<String>,
        val missingCriteria: List<String>,
        val candidates: List<Candidate> = emptyList()
    )

    data class Candidate(
        val root: Path,
        val score: Int,
        val matchedCriteria: List<String>,
        val missingCriteria: List<String>,
    )

    fun detect(projectRoot: Path): DetectionResult {
        val candidates = detectCandidates(projectRoot)
        return candidates.firstOrNull()?.let { best ->
            DetectionResult(
                isPlay1 = best.score >= REQUIRED_SCORE,
                projectRoot = best.root.takeIf { best.score >= REQUIRED_SCORE },
                matchedCriteria = best.matchedCriteria,
                missingCriteria = best.missingCriteria,
                candidates = candidates
            )
        } ?: DetectionResult(
            isPlay1 = false,
            projectRoot = null,
            matchedCriteria = emptyList(),
            missingCriteria = STRONG_CRITERIA.map { it.label },
            candidates = emptyList()
        )
    }

    fun detectAt(projectRoot: Path): DetectionResult {
        val candidate = scoreCandidate(projectRoot)
        return DetectionResult(
            isPlay1 = candidate.score >= REQUIRED_SCORE,
            projectRoot = projectRoot.takeIf { candidate.score >= REQUIRED_SCORE },
            matchedCriteria = candidate.matchedCriteria,
            missingCriteria = candidate.missingCriteria,
            candidates = listOf(candidate)
        )
    }

    private fun detectCandidates(projectRoot: Path): List<Candidate> {
        if (!Files.isDirectory(projectRoot)) return emptyList()
        val roots = collectCandidateRoots(projectRoot)
        return roots
            .map(::scoreCandidate)
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { projectRoot.relativize(it.root).nameCount })
    }

    private fun collectCandidateRoots(root: Path): List<Path> {
        val roots = mutableListOf<Path>()

        fun visit(dir: Path, depth: Int) {
            roots.add(dir)
            if (depth >= MAX_SEARCH_DEPTH) return
            Files.list(dir).use { stream ->
                stream
                    .filter { Files.isDirectory(it) }
                    .filter { it.fileName.toString() !in IGNORED_DIRECTORIES }
                    .forEach { visit(it, depth + 1) }
            }
        }

        visit(root, 0)
        return roots
    }

    private fun scoreCandidate(root: Path): Candidate {
        val strongCriteria = STRONG_CRITERIA.map { criterion ->
            criterion.label to root.resolve(criterion.path)
        }

        val matchedStrong = strongCriteria.filter { (_, path) ->
            Files.exists(path)
        }.map { it.first }

        val missingStrong = strongCriteria.filter { (_, path) ->
            !Files.exists(path)
        }.map { it.first }

        val matchedWeak = WEAK_CRITERIA.filter { (_, path) -> Files.exists(root.resolve(path)) }
            .map { it.first }

        return Candidate(
            root = root,
            score = matchedStrong.size * STRONG_WEIGHT + matchedWeak.size,
            matchedCriteria = matchedStrong + matchedWeak,
            missingCriteria = missingStrong
        )
    }

    companion object {
        private const val STRONG_WEIGHT = 3
        private const val REQUIRED_SCORE = 6
        private const val MAX_SEARCH_DEPTH = 3

        private data class Criterion(val label: String, val path: String)

        private val STRONG_CRITERIA = listOf(
            Criterion("conf/application.conf", "conf/application.conf"),
            Criterion("conf/routes", "conf/routes"),
            Criterion("app/controllers/", "app/controllers"),
        )

        private val WEAK_CRITERIA = listOf(
            "app/views/" to "app/views",
            "app/models/" to "app/models",
            "app/jobs/" to "app/jobs",
            "conf/dependencies.yml" to "conf/dependencies.yml",
            "lib/" to "lib",
        )

        private val IGNORED_DIRECTORIES = setOf(
            ".git", ".idea", ".gradle", "build", "out", "target", "node_modules"
        )

        fun isPlay1Project(projectRoot: Path): Boolean =
            Play1ProjectDetector().detect(projectRoot).isPlay1
    }
}
