package com.github.pablolec.play1toolkit.playconfig.model

import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty

data class PlayConfigKey(
    val rawKey: String,
    val logicalKey: String,
    val profile: String?,
    val value: String,
    val property: PlayConfigProperty,
    val lineNumber: Int
)

data class PlayConfigResolution(
    val logicalKey: String,
    val activeProfile: String?,
    val defaultValue: PlayConfigKey?,
    val profileValue: PlayConfigKey?,
    val effectiveValue: String?,
    val valueSource: PlayConfigValueSource,
    val unresolvedEnvironmentVariables: List<String>
)

enum class PlayConfigValueSource {
    PROFILE_SPECIFIC,
    DEFAULT,
    RUN_CONFIGURATION_ENVIRONMENT,
    SYSTEM_ENVIRONMENT,
    UNKNOWN
}

data class PlayConfigWrapperMethod(
    val fqClassName: String,
    val methodName: String,
    val keyArgIndex: Int
)
