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
            applicationPath = "/workspace/root/apps/gmvet-light",
            contentRoots = listOf(
                "/workspace",
                "/workspace/root",
                "/workspace/root/apps/gmvet-light"
            )
        )

        assertEquals("/workspace/root/apps/gmvet-light", selectedRoot)
    }
}
