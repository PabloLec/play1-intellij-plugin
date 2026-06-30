package com.github.pablolec.play1toolkit.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Paths

internal data class Play1JavaEnvironment(
    val sdkHomePath: String,
    val env: Map<String, String>,
)

internal object Play1JavaEnvironmentResolver {

    fun resolve(project: Project, applicationPath: String): Play1JavaEnvironment? =
        ApplicationManager.getApplication().runReadAction<Play1JavaEnvironment?> {
            val module = Play1RunConfigurationSupport.resolveModule(project, applicationPath)
            val sdk = Play1RunConfigurationSupport.resolveSdk(project, module) ?: return@runReadAction null
            val sdkHomePath = sdk.homePath?.takeIf { it.isNotBlank() } ?: return@runReadAction null
            if (!Files.isDirectory(Paths.get(sdkHomePath))) return@runReadAction null
            Play1JavaEnvironment(
                sdkHomePath = sdkHomePath,
                env = Play1RunConfigurationSupport.buildJavaSdkEnvironment(sdkHomePath, emptyMap()),
            )
        }
}
