package com.github.pablolec.play1toolkit.playconfig.settings

import com.github.pablolec.play1toolkit.playconfig.model.PlayConfigWrapperMethod
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "PlayConfigSettings", storages = [Storage("PlayConfigSettings.xml")])
class PlayConfigProjectSettings : PersistentStateComponent<PlayConfigProjectSettings.State> {

    data class WrapperMethodState(
        var fqClassName: String = "",
        var methodName: String = "",
        var keyArgIndex: Int = 0
    )

    data class State(
        var activeFrameworkId: String = "",
        var wrapperMethods: MutableList<WrapperMethodState> = mutableListOf(),
        var additionalKnownKeyPrefixes: MutableList<String> = mutableListOf()
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    var activeFrameworkId: String
        get() = state.activeFrameworkId
        set(v) { state.activeFrameworkId = v }

    val wrapperMethods: List<PlayConfigWrapperMethod>
        get() = state.wrapperMethods.map { PlayConfigWrapperMethod(it.fqClassName, it.methodName, it.keyArgIndex) }

    val additionalKnownKeyPrefixes: List<String>
        get() = state.additionalKnownKeyPrefixes.toList()

    fun setWrapperMethods(methods: List<PlayConfigWrapperMethod>) {
        state.wrapperMethods = methods.map { WrapperMethodState(it.fqClassName, it.methodName, it.keyArgIndex) }.toMutableList()
    }

    fun setAdditionalPrefixes(prefixes: List<String>) {
        state.additionalKnownKeyPrefixes = prefixes.toMutableList()
    }

    companion object {
        fun getInstance(project: Project): PlayConfigProjectSettings =
            project.getService(PlayConfigProjectSettings::class.java)
    }
}
