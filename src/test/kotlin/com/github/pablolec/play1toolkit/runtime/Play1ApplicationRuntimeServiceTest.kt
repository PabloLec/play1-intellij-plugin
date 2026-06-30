package com.github.pablolec.play1toolkit.runtime

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class Play1ApplicationRuntimeServiceTest : BasePlatformTestCase() {

    fun `test current process termination clears startup wait state`() {
        val service = Play1ApplicationRuntimeService.getInstance(project)
        val sessionId = service.processStarted("Run App", 19001)

        service.processTerminated(sessionId, 1)

        assertEquals(Play1ApplicationRuntimeService.ServerStatus.FAILED, service.state.serverStatus)
        assertEquals(Play1ApplicationRuntimeService.ApplicationStatus.UNKNOWN, service.state.applicationStatus)
        assertEquals("Play application process exited with code 1.", service.state.message)
    }

    fun `test stale process termination does not overwrite relaunched session`() {
        val service = Play1ApplicationRuntimeService.getInstance(project)
        val staleSessionId = service.processStarted("Run App", 19002)
        service.processStarted("Run App", 19003)

        service.processTerminated(staleSessionId, 1)

        assertEquals(Play1ApplicationRuntimeService.ServerStatus.STARTING, service.state.serverStatus)
        assertEquals(Play1ApplicationRuntimeService.ApplicationStatus.WAITING_FOR_SERVER, service.state.applicationStatus)
        assertEquals("http://127.0.0.1:19003/", service.state.url)
    }

    fun `test probe stops waiting when monitored process is no longer running`() {
        val service = Play1ApplicationRuntimeService.getInstance(project)
        val sessionId = service.processStarted("Run App", 19004)
        service.monitorProcess(
            sessionId = sessionId,
            isRunning = { false },
            exitCode = { 7 },
        )

        service.probe(sessionId, 19004)

        assertEquals(Play1ApplicationRuntimeService.ServerStatus.FAILED, service.state.serverStatus)
        assertEquals(Play1ApplicationRuntimeService.ApplicationStatus.UNKNOWN, service.state.applicationStatus)
        assertEquals("Play application process exited with code 7.", service.state.message)
    }

    fun `test stale process monitor does not overwrite relaunched session`() {
        val service = Play1ApplicationRuntimeService.getInstance(project)
        val staleSessionId = service.processStarted("Run App", 19005)
        service.processStarted("Run App", 19006)
        service.monitorProcess(
            sessionId = staleSessionId,
            isRunning = { false },
            exitCode = { 7 },
        )

        service.probe(staleSessionId, 19005)

        assertEquals(Play1ApplicationRuntimeService.ServerStatus.STARTING, service.state.serverStatus)
        assertEquals(Play1ApplicationRuntimeService.ApplicationStatus.WAITING_FOR_SERVER, service.state.applicationStatus)
        assertEquals("http://127.0.0.1:19006/", service.state.url)
    }
}
