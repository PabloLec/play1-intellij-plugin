package com.github.pablolec.play1toolkit.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

object Play1ModuleResolver {

    fun findModule(project: Project, applicationPath: String?): Module? {
        return ApplicationManager.getApplication().runReadAction<Module?> {
            val modules = ModuleManager.getInstance(project).modules
            if (modules.isEmpty()) return@runReadAction null

            val applicationRoot = applicationPath
                ?.let { LocalFileSystem.getInstance().findFileByPath(it) }

            if (applicationRoot != null) {
                modules.firstOrNull { module ->
                    ModuleRootManager.getInstance(module).contentRoots.any { contentRoot ->
                        contentRoot == applicationRoot || VfsUtil.isAncestor(contentRoot, applicationRoot, false)
                    }
                }?.let { return@runReadAction it }
            }

            modules.firstOrNull()
        }
    }
}
