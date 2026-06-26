package com.github.pablolec.play1toolkit.services

import com.github.pablolec.play1toolkit.config.Play1ProjectSettings
import com.github.pablolec.play1toolkit.detection.Play1ProjectDetector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
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

    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

    @Volatile
    private var lastDetectionInput: DetectionInput? = null

    fun refresh() {
        refreshNow(reason = "explicit refresh", force = true)
    }

    @Synchronized
    fun refreshNow(reason: String = "explicit refresh", force: Boolean = true) {
        val basePath = project.basePath ?: return
        val settingsPath = Play1ProjectSettings.getInstance(project).playApplicationPath
        val input = DetectionInput(basePath = basePath, settingsPath = settingsPath)
        if (!force && detectionResult != null && input == lastDetectionInput) return

        val detector = Play1ProjectDetector()
        val result = if (settingsPath.isNotBlank()) {
            detector.detectAt(Paths.get(settingsPath))
        } else {
            detector.detect(Paths.get(basePath))
        }
        lastDetectionInput = input
        detectionResult = result
        isPlay1Project = result.isPlay1
        playApplicationRoot = result.projectRoot
    }

    fun scheduleRefresh(reason: String) {
        if (project.isDisposed) return
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({
            if (!project.isDisposed) {
                refreshNow(reason = reason, force = true)
            }
        }, REFRESH_DEBOUNCE_MS)
    }

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 500

        fun getInstance(project: Project): Play1ProjectService = project.service()
    }

    private data class DetectionInput(
        val basePath: String,
        val settingsPath: String,
    )
}
