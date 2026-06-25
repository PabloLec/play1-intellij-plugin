package com.github.pablolec.play1toolkit.services

import com.github.pablolec.play1toolkit.config.Play1ProjectSettings
import com.github.pablolec.play1toolkit.detection.Play1ProjectDetector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class Play1ProjectService(private val project: Project) {

    @Volatile var isPlay1Project: Boolean = false
        private set

    @Volatile var detectionResult: Play1ProjectDetector.DetectionResult? = null
        private set

    @Volatile var playApplicationRoot: Path? = null
        private set

    val playApplicationPath: String?
        get() = playApplicationRoot?.toString()

    fun refresh() {
        val basePath = project.basePath ?: return
        val settingsPath = Play1ProjectSettings.getInstance(project).playApplicationPath
        val detector = Play1ProjectDetector()
        val result = if (settingsPath.isNotBlank()) {
            detector.detectAt(Paths.get(settingsPath))
        } else {
            detector.detect(Paths.get(basePath))
        }
        detectionResult = result
        isPlay1Project = result.isPlay1
        playApplicationRoot = result.projectRoot
    }

    companion object {
        fun getInstance(project: Project): Play1ProjectService = project.service()
    }
}
