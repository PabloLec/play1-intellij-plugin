package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.model.RepairReport
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

object Play1SourceRootManager {

    fun configureSourceRoots(project: Project, report: RepairReport) {
        val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: return
        val basePath = project.basePath ?: return

        WriteAction.runAndWait<Exception> {
            val rootModel = ModuleRootManager.getInstance(module).modifiableModel
            try {
                val contentEntry = rootModel.contentEntries.firstOrNull()
                    ?: run {
                        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath)
                            ?: return@runAndWait Unit.also {
                                report.error("Source roots", "Cannot locate project root on disk")
                            }
                        rootModel.addContentEntry(projectRoot)
                    }

                configureRoot(contentEntry, "$basePath/app", JavaSourceRootType.SOURCE, report, "Source root app/")
                configureRoot(contentEntry, "$basePath/test", JavaSourceRootType.TEST_SOURCE, report, "Test root test/")
                configureResourceRoot(contentEntry, "$basePath/conf", report, "Resources root conf/")

                rootModel.commit()
            } catch (e: Exception) {
                rootModel.dispose()
                report.error("Source roots", e.message ?: "Unknown error")
            }
        }
    }

    private fun configureRoot(
        contentEntry: com.intellij.openapi.roots.ContentEntry,
        path: String,
        type: JavaSourceRootType,
        report: RepairReport,
        label: String
    ) {
        val vFile = LocalFileSystem.getInstance().findFileByPath(path)
        if (vFile == null || !vFile.exists()) {
            report.skipped(label, "directory not found")
            return
        }

        val alreadyConfigured = contentEntry.sourceFolders.any {
            VfsUtil.isAncestor(vFile, it.file ?: return@any false, false) &&
                    it.rootType == type
        }

        if (!alreadyConfigured) {
            contentEntry.addSourceFolder(vFile, type)
        }
        report.ok(label, "configured")
    }

    private fun configureResourceRoot(
        contentEntry: com.intellij.openapi.roots.ContentEntry,
        path: String,
        report: RepairReport,
        label: String
    ) {
        val vFile = LocalFileSystem.getInstance().findFileByPath(path)
        if (vFile == null || !vFile.exists()) {
            report.skipped(label, "directory not found")
            return
        }

        val alreadyConfigured = contentEntry.sourceFolders.any {
            VfsUtil.isAncestor(vFile, it.file ?: return@any false, false) &&
                    it.rootType == JavaResourceRootType.RESOURCE
        }

        if (!alreadyConfigured) {
            contentEntry.addSourceFolder(vFile, JavaResourceRootType.RESOURCE)
        }
        report.ok(label, "configured")
    }
}
