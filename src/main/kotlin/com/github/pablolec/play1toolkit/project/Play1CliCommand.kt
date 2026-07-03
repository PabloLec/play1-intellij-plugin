package com.github.pablolec.play1toolkit.project

enum class Play1CliCommandGroup(val title: String) {
    BUILD_TEST("Build & Test"),
    PACKAGE("Package"),
    DEPENDENCIES("Dependencies"),
}

enum class Play1CliCommandId(
    val displayName: String,
    val group: Play1CliCommandGroup,
    val description: String,
) {
    CLEAN(
        displayName = "Clean",
        group = Play1CliCommandGroup.BUILD_TEST,
        description = "Delete temporary files and bytecode cache",
    ),
    TEST(
        displayName = "Test",
        group = Play1CliCommandGroup.BUILD_TEST,
        description = "Run the application in test mode",
    ),
    AUTOTEST(
        displayName = "Auto Test",
        group = Play1CliCommandGroup.BUILD_TEST,
        description = "Automatically rerun tests on changes",
    ),
    PRECOMPILE(
        displayName = "Precompile",
        group = Play1CliCommandGroup.BUILD_TEST,
        description = "Precompile Java sources and templates",
    ),
    WAR(
        displayName = "Build WAR",
        group = Play1CliCommandGroup.PACKAGE,
        description = "Export the application as a WAR archive",
    ),
    DEPS(
        displayName = "Sync Dependencies",
        group = Play1CliCommandGroup.DEPENDENCIES,
        description = "Resolve dependencies from conf/dependencies.yml",
    ),
}

data class Play1CliRequest(
    val commandId: Play1CliCommandId,
    val profile: String? = null,
    val warOutputPath: String? = null,
    val warZip: Boolean = false,
)

enum class Play1CliResultReason {
    NONE,
    PLAY_HOME_INVALID,
    COMMAND_UNSUPPORTED,
    UNSUPPORTED_PLAY_VERSION,
    MANAGED_PLAY_HOME_UNAVAILABLE,
    DEPENDENCIES_FILE_MISSING,
    LIB_ALREADY_POPULATED,
    PYTHON_INTERPRETER_MISSING,
    MANAGED_RUNTIME_UNAVAILABLE,
    INVALID_COMMAND_OPTIONS,
    START_FAILURE,
    EXECUTION_CANCELLED,
}

data class Play1CliCommandPlan(
    val request: Play1CliRequest,
    val available: Boolean,
    val message: String,
    val reason: Play1CliResultReason = Play1CliResultReason.NONE,
    val effectivePlayHome: String? = null,
    val effectivePlayVersion: String? = null,
    val commandName: String? = null,
    val args: List<String> = emptyList(),
    val runtimeDescription: String? = null,
    val requiredPythonMajor: Int? = null,
    val detail: String? = null,
)

data class Play1CliResult(
    val request: Play1CliRequest,
    val success: Boolean,
    val skipped: Boolean = false,
    val message: String = "",
    val reason: Play1CliResultReason = Play1CliResultReason.NONE,
    val effectivePlayHome: String? = null,
    val effectivePlayVersion: String? = null,
    val runtimeDescription: String? = null,
    val requiredPythonMajor: Int? = null,
    val detail: String? = null,
)
