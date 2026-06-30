package com.github.pablolec.play1toolkit.project

import com.intellij.openapi.progress.ProgressIndicator

data class DepsResult(
    val success: Boolean,
    val skipped: Boolean = false,
    val message: String = "",
    val reason: DepsResultReason = DepsResultReason.NONE,
    val requiredPythonMajor: Int? = null,
    val detail: String? = null,
)

enum class DepsResultReason {
    NONE,
    UNSUPPORTED_PLAY_VERSION,
    DEPENDENCIES_FILE_MISSING,
    LIB_ALREADY_POPULATED,
    PYTHON_INTERPRETER_MISSING,
    MANAGED_RUNTIME_UNAVAILABLE,
}

object Play1DepsRunner {

    fun run(
        projectPath: String,
        playHome: String,
        playVersion: String? = null,
        environmentOverrides: Map<String, String> = emptyMap(),
        indicator: ProgressIndicator? = null,
        onLine: (line: String, isError: Boolean) -> Unit = { _, _ -> },
    ): DepsResult {
        val result = Play1CliRunner.run(
            request = Play1CliRequest(Play1CliCommandId.DEPS),
            projectPath = projectPath,
            playHome = playHome,
            projectPlayVersion = playVersion,
            environmentOverrides = environmentOverrides,
            indicator = indicator,
            onLine = onLine,
        )

        if (!result.success && result.reason == Play1CliResultReason.UNSUPPORTED_PLAY_VERSION && result.effectivePlayHome != null) {
            onLine("⚠  Project Play ${playVersion ?: "unknown"} doesn't support 'play deps'.", true)
            onLine(
                "→  Using ${result.effectivePlayHome} (Play ${result.effectivePlayVersion ?: "unknown"}) for dependency resolution.",
                false
            )
        }

        return DepsResult(
            success = result.success,
            skipped = result.skipped,
            message = result.message,
            reason = when (result.reason) {
                Play1CliResultReason.UNSUPPORTED_PLAY_VERSION -> DepsResultReason.UNSUPPORTED_PLAY_VERSION
                Play1CliResultReason.DEPENDENCIES_FILE_MISSING -> DepsResultReason.DEPENDENCIES_FILE_MISSING
                Play1CliResultReason.LIB_ALREADY_POPULATED -> DepsResultReason.LIB_ALREADY_POPULATED
                Play1CliResultReason.PYTHON_INTERPRETER_MISSING -> DepsResultReason.PYTHON_INTERPRETER_MISSING
                Play1CliResultReason.MANAGED_RUNTIME_UNAVAILABLE -> DepsResultReason.MANAGED_RUNTIME_UNAVAILABLE
                else -> DepsResultReason.NONE
            },
            requiredPythonMajor = result.requiredPythonMajor,
            detail = result.detail,
        )
    }
}
