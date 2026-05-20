package com.github.pablolec.play1toolkit.services

import com.github.pablolec.play1toolkit.project.Play1CliCommandId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Play1CommandExecutionServiceTest {

    @Test
    fun `listener sees lifecycle transitions immediately`() {
        val service = Play1CommandExecutionService()
        val states = mutableListOf<Play1CommandExecutionService.State>()

        val detach = service.addListener { states += it }

        assertEquals(listOf(Play1CommandExecutionService.State(null, false)), states)

        assertTrue(service.start(Play1CliCommandId.TEST))
        assertEquals(Play1CommandExecutionService.State(Play1CliCommandId.TEST, false), states.last())

        service.requestStop()
        assertEquals(Play1CommandExecutionService.State(Play1CliCommandId.TEST, true), states.last())

        service.finish()
        assertEquals(Play1CommandExecutionService.State(null, false), states.last())

        detach.invoke()
        val countBefore = states.size
        assertTrue(service.start(Play1CliCommandId.CLEAN))
        assertEquals(countBefore, states.size)
    }

    @Test
    fun `start rejects concurrent command`() {
        val service = Play1CommandExecutionService()

        assertTrue(service.start(Play1CliCommandId.TEST))
        assertFalse(service.start(Play1CliCommandId.CLEAN))
        assertEquals(Play1CliCommandId.TEST, service.currentCommandId)
    }
}
