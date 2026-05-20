package com.github.pablolec.play1toolkit.services

import com.github.pablolec.play1toolkit.project.Play1CliCommandId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class Play1CommandExecutionService {

    data class State(
        val currentCommandId: Play1CliCommandId?,
        val stopRequested: Boolean,
    ) {
        val isRunning: Boolean
            get() = currentCommandId != null
    }

    @Volatile
    var currentCommandId: Play1CliCommandId? = null
        private set

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stopRequestedFlag: Boolean = false

    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()

    val isRunning: Boolean
        get() = currentCommandId != null

    val stopRequested: Boolean
        get() = stopRequestedFlag

    val state: State
        get() = State(
            currentCommandId = currentCommandId,
            stopRequested = stopRequestedFlag,
        )

    @Synchronized
    fun start(commandId: Play1CliCommandId): Boolean {
        if (currentCommandId != null) return false
        currentCommandId = commandId
        stopRequestedFlag = false
        process = null
        notifyListeners()
        return true
    }

    @Synchronized
    fun attachProcess(process: Process) {
        this.process = process
        if (stopRequestedFlag) {
            process.destroy()
            process.destroyForcibly()
        }
        notifyListeners()
    }

    @Synchronized
    fun requestStop() {
        stopRequestedFlag = true
        process?.destroy()
        process?.destroyForcibly()
        notifyListeners()
    }

    @Synchronized
    fun finish() {
        currentCommandId = null
        process = null
        stopRequestedFlag = false
        notifyListeners()
    }

    fun addListener(listener: (State) -> Unit): () -> Unit {
        listeners += listener
        listener(state)
        return { listeners.remove(listener) }
    }

    private fun notifyListeners() {
        val snapshot = state
        listeners.forEach { it(snapshot) }
    }

    companion object {
        fun getInstance(project: Project): Play1CommandExecutionService = project.service()
    }
}
