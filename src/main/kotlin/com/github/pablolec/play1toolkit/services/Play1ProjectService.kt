package com.github.pablolec.play1toolkit.services

import com.github.pablolec.play1toolkit.detection.Play1ProjectDetector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class Play1ProjectService(private val project: Project) {

    @Volatile var isPlay1Project: Boolean = false
        private set

    @Volatile var detectionResult: Play1ProjectDetector.DetectionResult? = null
        private set

    fun refresh() {
        val basePath = project.basePath ?: return
        val root = Paths.get(basePath)
        val result = Play1ProjectDetector().detect(root)
        detectionResult = result
        isPlay1Project = result.isPlay1
    }

    companion object {
        fun getInstance(project: Project): Play1ProjectService = project.service()
    }
}
