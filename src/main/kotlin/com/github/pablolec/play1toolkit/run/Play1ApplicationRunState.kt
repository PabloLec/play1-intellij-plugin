package com.github.pablolec.play1toolkit.run

import com.github.pablolec.play1toolkit.config.Play1Settings
import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.runners.ExecutionEnvironment
import java.nio.file.Files
import java.nio.file.Paths

class Play1ApplicationRunState(
    environment: ExecutionEnvironment,
    private val config: Play1ApplicationRunConfiguration
) : JavaCommandLineState(environment) {

    override fun createJavaParameters(): JavaParameters {
        val settings = Play1Settings.getInstance()
        val playHome = settings.playHome.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("Play Home is not configured. Go to Settings > Tools > Play 1 Toolkit.")

        val playHomePath = Paths.get(playHome)
        val frameworkDir = playHomePath.resolve("framework")
        val playJar = Play1HomeValidator.findPlayJar(frameworkDir)
            ?: throw ExecutionException("play-*.jar not found in $frameworkDir")

        val params = JavaParameters()
        params.mainClass = "play.server.Server"
        params.workingDirectory = config.applicationPath

        // Add play jar and framework libs to classpath
        params.classPath.add(playJar.toAbsolutePath().toString())

        val libDir = frameworkDir.resolve("lib")
        if (Files.isDirectory(libDir)) {
            Files.list(libDir).use { stream ->
                stream.filter { it.toString().endsWith(".jar") }.forEach { jar ->
                    params.classPath.add(jar.toAbsolutePath().toString())
                }
            }
        }

        // Add project lib/*.jar
        val projectLibDir = Paths.get(config.applicationPath, "lib")
        if (Files.isDirectory(projectLibDir)) {
            Files.list(projectLibDir).use { stream ->
                stream.filter { it.toString().endsWith(".jar") }.forEach { jar ->
                    params.classPath.add(jar.toAbsolutePath().toString())
                }
            }
        }

        // VM options
        params.vmParametersList.add("-Dapplication.path=${config.applicationPath}")
        params.vmParametersList.add("-Dplay.id=${config.playId}")
        params.vmParametersList.add("-Dhttp.port=${config.httpPort}")

        if (config.jvmOptions.isNotBlank()) {
            config.jvmOptions.split(" ").filter { it.isNotBlank() }.forEach {
                params.vmParametersList.add(it)
            }
        }

        // Debug mode
        val executor = environment.executor
        if (executor.id == "Debug") {
            params.vmParametersList.add(
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${config.debugPort}"
            )
        }

        return params
    }
}
