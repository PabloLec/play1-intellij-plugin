package com.github.pablolec.play1toolkit.services

import com.github.pablolec.play1toolkit.project.Play1CliCommandId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class Play1CommandExecutionService {

    @Volatile
    var currentCommandId: Play1CliCommandId? = null
        private set

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stopRequestedFlag: Boolean = false

    val isRunning: Boolean
        get() = currentCommandId != null

    val stopRequested: Boolean
        get() = stopRequestedFlag

    @Synchronized
    fun start(commandId: Play1CliCommandId): Boolean {
        if (currentCommandId != null) return false
        currentCommandId = commandId
        stopRequestedFlag = false
        process = null
        return true
    }

    @Synchronized
    fun attachProcess(process: Process) {
        this.process = process
        if (stopRequestedFlag) {
            process.destroy()
            process.destroyForcibly()
        }
    }

    @Synchronized
    fun requestStop() {
        stopRequestedFlag = true
        process?.destroy()
        process?.destroyForcibly()
    }

    @Synchronized
    fun finish() {
        currentCommandId = null
        process = null
        stopRequestedFlag = false
    }

    companion object {
        fun getInstance(project: Project): Play1CommandExecutionService = project.service()
    }
}
