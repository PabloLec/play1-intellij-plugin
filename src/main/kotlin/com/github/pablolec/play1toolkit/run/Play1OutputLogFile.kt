package com.github.pablolec.play1toolkit.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal object Play1OutputLogFile {
    fun resolve(configuredPath: String, configurationName: String): Path {
        val trimmed = configuredPath.trim()
        if (trimmed.isBlank()) {
            return Paths.get(System.getProperty("java.io.tmpdir"))
                .resolve("play-v1-${configurationName.safeFileStem()}.log")
                .toAbsolutePath()
                .normalize()
        }

        val configured = Paths.get(trimmed).toAbsolutePath().normalize()
        return if (configured.exists() && configured.isDirectory()) {
            configured.resolve("play-v1-${configurationName.safeFileStem()}.log")
        } else {
            configured
        }
    }

    private fun String.safeFileStem(): String =
        trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "app" }
}

internal class Play1OutputLogFileListener private constructor(
    private val writer: BufferedWriter,
) : ProcessListener {

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (outputType != ProcessOutputTypes.STDOUT && outputType != ProcessOutputTypes.STDERR) {
            return
        }

        synchronized(writer) {
            runCatching {
                writer.write(event.text)
                writer.flush()
            }
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        close()
    }

    private fun close() {
        synchronized(writer) {
            runCatching { writer.close() }
        }
    }

    companion object {
        fun create(path: Path): Play1OutputLogFileListener {
            path.parent?.createDirectories()
            val writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE,
            )
            return Play1OutputLogFileListener(writer)
        }
    }
}
