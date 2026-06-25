package com.github.pablolec.play1toolkit.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "Play1ProjectSettings", storages = [Storage("Play1ProjectSettings.xml")])
class Play1ProjectSettings : PersistentStateComponent<Play1ProjectSettings.State> {

    data class State(
        var playApplicationPath: String = "",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var playApplicationPath: String
        get() = state.playApplicationPath
        set(value) {
            state.playApplicationPath = value.trim()
        }

    companion object {
        fun getInstance(project: Project): Play1ProjectSettings =
            project.getService(Play1ProjectSettings::class.java)
    }
}
