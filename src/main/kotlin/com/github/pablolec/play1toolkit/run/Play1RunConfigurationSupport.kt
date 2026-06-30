package com.github.pablolec.play1toolkit.run

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.execution.ParametersListUtil
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

internal object Play1RunConfigurationSupport {

    fun isDebugExecutor(environment: ExecutionEnvironment): Boolean =
        environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

    fun validatePorts(httpPort: Int, debugPort: Int) {
        if (httpPort !in 1..65535) {
            throw com.intellij.execution.configurations.RuntimeConfigurationError(
                "HTTP port must be between 1 and 65535."
            )
        }
        if (debugPort !in 1..65535) {
            throw com.intellij.execution.configurations.RuntimeConfigurationError(
                "Debug port must be between 1 and 65535."
            )
        }
        if (httpPort == debugPort) {
            throw com.intellij.execution.configurations.RuntimeConfigurationError(
                "HTTP port and debug port must be different."
            )
        }
    }

    fun parseJvmOptions(jvmOptions: String): List<String> = ParametersListUtil.parse(jvmOptions)

    fun removeDebugJvmOptions(jvmOptions: String): String {
        if (jvmOptions.isBlank()) {
            return ""
        }
        return parseJvmOptions(jvmOptions)
            .filterNot { option ->
                option == "-Xdebug" ||
                    option.startsWith("-Xrunjdwp:") ||
                    option.startsWith("-agentlib:jdwp") ||
                    option.startsWith("-Xjdwp:")
            }
            .joinToString(" ")
    }

    fun selectInitialProfile(
        configuredDefault: String,
        availableProfiles: Collection<String>,
        osName: String = System.getProperty("os.name"),
    ): String {
        val profiles = availableProfiles.mapTo(linkedSetOf()) { it.trim() }.filter { it.isNotBlank() }.toSet()
        val configured = configuredDefault.trim()
        if (configured.isNotBlank() && (profiles.isEmpty() || configured in profiles)) {
            return configured
        }

        currentOsProfileCandidates(osName).firstOrNull { it in profiles }?.let { return it }
        return configured
    }

    fun resolveModule(project: Project, applicationPath: String): Module? {
        val modules = ModuleManager.getInstance(project).modules
        if (modules.isEmpty()) {
            return null
        }

        val selectedRootPath = selectBestRootPath(
            applicationPath,
            modules.flatMap { module ->
                ModuleRootManager.getInstance(module).contentRoots.map { root -> root.path }
            }
        )

        return selectedRootPath?.let { rootPath ->
            modules.firstOrNull { module ->
                ModuleRootManager.getInstance(module).contentRoots.any { it.path == rootPath }
            }
        } ?: modules.firstOrNull()
    }

    fun resolveSearchScope(project: Project, applicationPath: String): GlobalSearchScope {
        val module = resolveModule(project, applicationPath)
        return if (module != null) {
            GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
        } else {
            GlobalSearchScope.projectScope(project)
        }
    }

    fun resolveSdk(project: Project, module: Module?): Sdk? {
        return module?.let { ModuleRootManager.getInstance(it).sdk }
            ?: ProjectRootManager.getInstance(project).projectSdk
    }

    fun buildJavaSdkEnvironment(
        sdkHomePath: String,
        configuredEnv: Map<String, String>,
        inheritedPath: String? = System.getenv("PATH"),
        pathSeparator: String = File.pathSeparator,
        inheritedEnvKeys: Set<String> = System.getenv().keys,
    ): Map<String, String> {
        val javaBinPath = Paths.get(sdkHomePath).resolve("bin").toString()
        val pathKey = configuredEnv.keys.firstOrNull { it.equals("PATH", ignoreCase = true) }
            ?: inheritedEnvKeys.firstOrNull { it.equals("PATH", ignoreCase = true) }
            ?: "PATH"
        val configuredPath = configuredEnv[pathKey]?.takeIf { it.isNotBlank() }
        val existingPath = configuredPath ?: inheritedPath.orEmpty()
        val pathEntries = existingPath
            .split(pathSeparator)
            .filter { it.isNotBlank() }

        val resolvedPath = if (javaBinPath in pathEntries) {
            pathEntries.joinToString(pathSeparator)
        } else {
            (listOf(javaBinPath) + pathEntries).joinToString(pathSeparator)
        }

        return mapOf(
            "JAVA_HOME" to sdkHomePath,
            pathKey to resolvedPath,
        )
    }

    fun selectBestRootPath(applicationPath: String, contentRoots: Collection<String>): String? {
        val normalizedApplicationPath = normalizePathOrNull(applicationPath) ?: return null
        return contentRoots
            .mapNotNull { rootPath ->
                val normalizedRoot = normalizePathOrNull(rootPath) ?: return@mapNotNull null
                if (normalizedApplicationPath == normalizedRoot || normalizedApplicationPath.startsWith(normalizedRoot)) {
                    normalizedRoot.toString()
                } else {
                    null
                }
            }
            .maxByOrNull { rootPath -> rootPath.length }
    }

    private fun normalizePathOrNull(path: String): Path? {
        if (path.isBlank()) {
            return null
        }
        return try {
            Paths.get(FileUtil.toSystemIndependentName(path)).normalize()
        } catch (_: Exception) {
            null
        }
    }

    private fun currentOsProfileCandidates(osName: String): List<String> {
        val normalized = osName.lowercase()
        return when {
            "linux" in normalized -> listOf("linux")
            "mac" in normalized || "darwin" in normalized -> listOf("macos", "mac", "osx", "darwin")
            "windows" in normalized -> listOf("windows", "win")
            else -> emptyList()
        }
    }
}
