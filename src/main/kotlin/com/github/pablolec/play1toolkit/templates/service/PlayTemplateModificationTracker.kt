package com.github.pablolec.play1toolkit.templates.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

@Service(Service.Level.PROJECT)
class PlayTemplateModificationTracker {

    private val tracker = SimpleModificationTracker()

    fun modificationTracker(): SimpleModificationTracker = tracker

    fun incModificationCount() {
        tracker.incModificationCount()
    }

    companion object {
        fun getInstance(project: Project): PlayTemplateModificationTracker =
            project.getService(PlayTemplateModificationTracker::class.java)
    }
}
