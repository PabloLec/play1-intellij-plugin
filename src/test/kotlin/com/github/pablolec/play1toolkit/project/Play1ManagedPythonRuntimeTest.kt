package com.github.pablolec.play1toolkit.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Play1ManagedPythonRuntimeTest {

    @Test
    fun `detect artifact for linux x64`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Linux", "x86_64")
        assertEquals("pypy2.7-v7.3.19-linux64.tar.bz2", artifact?.fileName)
    }

    @Test
    fun `detect python 3 artifact for linux x64`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Linux", "x86_64", pythonMajor = 3)
        assertEquals("pypy3.11-v7.3.19-linux64.tar.bz2", artifact?.fileName)
    }

    @Test
    fun `detect artifact for linux arm64`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Linux", "aarch64")
        assertEquals("pypy2.7-v7.3.19-aarch64.tar.bz2", artifact?.fileName)
    }

    @Test
    fun `detect python 3 artifact for linux arm64`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Linux", "aarch64", pythonMajor = 3)
        assertEquals("pypy3.11-v7.3.19-aarch64.tar.bz2", artifact?.fileName)
    }

    @Test
    fun `detect artifact for mac arm64`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Mac OS X", "arm64")
        assertEquals("pypy2.7-v7.3.19-macos_arm64.tar.bz2", artifact?.fileName)
    }

    @Test
    fun `detect python 3 artifact for mac arm64`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Mac OS X", "arm64", pythonMajor = 3)
        assertEquals("pypy3.11-v7.3.19-macos_arm64.tar.bz2", artifact?.fileName)
    }

    @Test
    fun `detect artifact for windows x64`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Windows 11", "amd64")
        assertEquals("pypy2.7-v7.3.19-win64.zip", artifact?.fileName)
    }

    @Test
    fun `detect python 3 artifact for windows x64`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Windows 11", "amd64", pythonMajor = 3)
        assertEquals("pypy3.11-v7.3.19-win64.zip", artifact?.fileName)
    }

    @Test
    fun `unsupported platform returns null`() {
        val artifact = Play1ManagedPythonRuntime.detectArtifact("Linux", "ppc64")
        assertNull(artifact)
    }
}
