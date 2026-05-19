package com.github.pablolec.play1toolkit.run

import com.intellij.debugger.impl.GenericDebuggerRunnerSettings
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RunnerSettings
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
import java.nio.file.Path
import java.nio.file.Paths

internal object Play1RunConfigurationSupport {

    private const val SOCKET_TRANSPORT = 0
    private const val LOCALHOST = "127.0.0.1"

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

    fun applyJvmOptions(params: JavaParameters, jvmOptions: String) {
        parseJvmOptions(jvmOptions).forEach(params.vmParametersList::add)
    }

    fun configureDebugRunnerSettings(runnerSettings: RunnerSettings?, debugPort: Int) {
        val settings = runnerSettings as? GenericDebuggerRunnerSettings
            ?: throw ExecutionException(
                "Cannot configure Play 1 debug session: missing IntelliJ debugger runner settings."
            )

        settings.setLocal(true)
        settings.setTransport(DebuggerSettings.getInstance().transport)
        settings.setDebugPort(debugPort.toString())
    }

    fun createDebugRemoteConnection(debugPort: Int): RemoteConnection {
        val transport = DebuggerSettings.getInstance().transport
        return RemoteConnection(
            transport == SOCKET_TRANSPORT,
            LOCALHOST,
            debugPort.toString(),
            true
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
}
