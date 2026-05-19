package com.github.pablolec.play1toolkit.config

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.APP)
@State(
    name = "Play1Settings",
    storages = [Storage("Play1Settings.xml")]
)
class Play1Settings : PersistentStateComponent<Play1Settings.State> {

    data class State(
        var playHome: String = "",
        var depsPlayHome: String = "",
        var defaultPlayId: String = "dev",
        var defaultHttpPort: Int = 9000,
        var defaultDebugPort: Int = 5005,
        var autoRepairOnOpen: Boolean = true
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var playHome: String
        get() = state.playHome
        set(value) { state.playHome = value }

    var depsPlayHome: String
        get() = state.depsPlayHome
        set(value) { state.depsPlayHome = value }

    var defaultPlayId: String
        get() = state.defaultPlayId
        set(value) { state.defaultPlayId = value }

    var defaultHttpPort: Int
        get() = state.defaultHttpPort
        set(value) { state.defaultHttpPort = value }

    var defaultDebugPort: Int
        get() = state.defaultDebugPort
        set(value) { state.defaultDebugPort = value }

    var autoRepairOnOpen: Boolean
        get() = state.autoRepairOnOpen
        set(value) { state.autoRepairOnOpen = value }

    companion object {
        fun getInstance(): Play1Settings = service()
    }
}
