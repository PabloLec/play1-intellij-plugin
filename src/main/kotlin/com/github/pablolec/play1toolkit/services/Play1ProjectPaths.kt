package com.github.pablolec.play1toolkit.services

import com.intellij.openapi.project.Project

object Play1ProjectPaths {

    fun applicationPath(project: Project): String? {
        val service = Play1ProjectService.getInstance(project)
        service.refresh()
        return service.playApplicationPath ?: project.basePath
    }
}
