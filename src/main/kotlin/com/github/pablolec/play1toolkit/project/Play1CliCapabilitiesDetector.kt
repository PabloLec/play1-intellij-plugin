package com.github.pablolec.play1toolkit.project

import java.nio.file.Files
import java.nio.file.Path

object Play1CliCapabilitiesDetector {

    data class DetectedCapabilities(
        val commands: Set<String>,
    )

    fun detect(playHome: Path): DetectedCapabilities {
        val commandsDir = playHome.resolve("framework").resolve("pym").resolve("play").resolve("commands")
        if (!Files.isDirectory(commandsDir)) {
            return DetectedCapabilities(commands = emptySet())
        }

        val commands = linkedSetOf<String>()
        Files.list(commandsDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".py") }
                .forEach { commands += extractCommands(it) }
        }
        return DetectedCapabilities(commands = commands)
    }

    fun resolveCommandName(commandId: Play1CliCommandId, commands: Set<String>, playVersion: String?): String? {
        val normalized = commands.mapTo(linkedSetOf()) { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return when (commandId) {
            Play1CliCommandId.CLEAN -> resolveAlias(normalized, "clean")
            Play1CliCommandId.TEST -> resolveAlias(normalized, "test")
            Play1CliCommandId.AUTOTEST -> resolveAlias(normalized, "autotest", "auto-test")
            Play1CliCommandId.PRECOMPILE -> resolveAlias(normalized, "precompile")
            Play1CliCommandId.WAR -> resolveAlias(normalized, "war")
            Play1CliCommandId.DEPS -> resolveAlias(normalized, "deps", "dependencies")
                ?: if (supportsDepCommand(playVersion)) "deps" else null
        } ?: fallbackCommandName(commandId, playVersion, normalized.isEmpty())
    }

    fun supportsDepCommand(version: String?): Boolean {
        if (version == null) return true
        val parts = version.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return true
        val major = parts[0]
        val minor = parts[1]
        return major > 1 || (major == 1 && minor >= 2)
    }

    private fun fallbackCommandName(
        commandId: Play1CliCommandId,
        playVersion: String?,
        noCommandScan: Boolean,
    ): String? {
        if (!noCommandScan) return null
        return when (commandId) {
            Play1CliCommandId.CLEAN -> "clean"
            Play1CliCommandId.TEST -> "test"
            Play1CliCommandId.AUTOTEST -> "autotest"
            Play1CliCommandId.PRECOMPILE -> "precompile"
            Play1CliCommandId.WAR -> "war"
            Play1CliCommandId.DEPS -> if (supportsDepCommand(playVersion)) "deps" else null
        }
    }

    private fun resolveAlias(commands: Set<String>, vararg aliases: String): String? =
        aliases.firstOrNull { it in commands }

    private fun extractCommands(file: Path): Set<String> {
        val content = runCatching { Files.readString(file) }.getOrDefault("")
        val commandDeclaration = Regex("""COMMANDS\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptySet()
        return Regex("""['"]([^'"]+)['"]""")
            .findAll(commandDeclaration)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
