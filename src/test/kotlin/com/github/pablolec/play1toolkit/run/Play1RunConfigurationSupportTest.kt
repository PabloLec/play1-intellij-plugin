package com.github.pablolec.play1toolkit.run

import com.intellij.execution.configurations.RuntimeConfigurationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Play1RunConfigurationSupportTest {

    @Test
    fun `parseJvmOptions keeps quoted arguments intact`() {
        val parsed = Play1RunConfigurationSupport.parseJvmOptions(
            """-Xmx2g -Dfoo="hello world" --add-opens java.base/java.lang=ALL-UNNAMED"""
        )

        assertEquals(
            listOf(
                "-Xmx2g",
                "-Dfoo=hello world",
                "--add-opens",
                "java.base/java.lang=ALL-UNNAMED"
            ),
            parsed
        )
    }

    @Test
    fun `validatePorts rejects identical http and debug ports`() {
        val error = assertFailsWith<RuntimeConfigurationError> {
            Play1RunConfigurationSupport.validatePorts(httpPort = 9000, debugPort = 9000)
        }

        assertEquals("HTTP port and debug port must be different.", error.localizedMessage)
    }

    @Test
    fun `selectBestRootPath prefers the deepest matching content root`() {
        val selectedRoot = Play1RunConfigurationSupport.selectBestRootPath(
            applicationPath = "/workspace/root/apps/legacy-app",
            contentRoots = listOf(
                "/workspace",
                "/workspace/root",
                "/workspace/root/apps/legacy-app"
            )
        )

        assertEquals("/workspace/root/apps/legacy-app", selectedRoot)
    }

    @Test
    fun `selectInitialProfile prefers configured profile when it exists`() {
        val selected = Play1RunConfigurationSupport.selectInitialProfile(
            configuredDefault = "dev",
            availableProfiles = listOf("dev", "linux"),
            osName = "Linux",
        )

        assertEquals("dev", selected)
    }

    @Test
    fun `selectInitialProfile falls back to current operating system profile`() {
        val selected = Play1RunConfigurationSupport.selectInitialProfile(
            configuredDefault = "dev",
            availableProfiles = listOf("linux", "prod"),
            osName = "Linux",
        )

        assertEquals("linux", selected)
    }

    @Test
    fun `selectInitialProfile keeps configured default when profiles are unavailable`() {
        val selected = Play1RunConfigurationSupport.selectInitialProfile(
            configuredDefault = "dev",
            availableProfiles = emptyList(),
            osName = "Linux",
        )

        assertEquals("dev", selected)
    }
}
