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
    fun `removeDebugJvmOptions removes JDWP options and keeps regular JVM options`() {
        val sanitized = Play1RunConfigurationSupport.removeDebugJvmOptions(
            "-Xmx2g -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n " +
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Dfoo=bar"
        )

        assertEquals("-Xmx2g -Dfoo=bar", sanitized)
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

    @Test
    fun `buildJavaSdkEnvironment uses sdk as java home and prepends java bin to configured path`() {
        val env = Play1RunConfigurationSupport.buildJavaSdkEnvironment(
            sdkHomePath = "/jdks/current",
            configuredEnv = mapOf("PATH" to "/custom/bin:/usr/bin"),
            inheritedPath = "/ignored/bin",
            pathSeparator = ":",
        )

        assertEquals("/jdks/current", env["JAVA_HOME"])
        assertEquals("/jdks/current/bin:/custom/bin:/usr/bin", env["PATH"])
    }

    @Test
    fun `buildJavaSdkEnvironment falls back to inherited path`() {
        val env = Play1RunConfigurationSupport.buildJavaSdkEnvironment(
            sdkHomePath = "/jdks/current",
            configuredEnv = emptyMap(),
            inheritedPath = "/usr/local/bin:/usr/bin",
            pathSeparator = ":",
        )

        assertEquals("/jdks/current/bin:/usr/local/bin:/usr/bin", env["PATH"])
    }

    @Test
    fun `buildJavaSdkEnvironment does not duplicate sdk bin path`() {
        val env = Play1RunConfigurationSupport.buildJavaSdkEnvironment(
            sdkHomePath = "/jdks/current",
            configuredEnv = mapOf("PATH" to "/jdks/current/bin:/usr/bin"),
            inheritedPath = "/ignored/bin",
            pathSeparator = ":",
        )

        assertEquals("/jdks/current/bin:/usr/bin", env["PATH"])
    }
}
