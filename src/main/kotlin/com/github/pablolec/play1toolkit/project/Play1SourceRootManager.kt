package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.model.RepairReport
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

object Play1SourceRootManager {

    fun configureSourceRoots(project: Project, report: RepairReport, applicationPath: String? = project.basePath) {
        val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: run {
            report.skipped("Source roots", "no IntelliJ module found")
            return
        }
        val basePath = applicationPath ?: return

        WriteAction.runAndWait<Exception> {
            val rootModel = ModuleRootManager.getInstance(module).modifiableModel
            try {
                val applicationRoot = LocalFileSystem.getInstance().findFileByPath(basePath)
                    ?: return@runAndWait Unit.also {
                        report.error("Source roots", "Cannot locate Play application root on disk")
                    }
                val contentEntry = rootModel.contentEntries.firstOrNull { entry ->
                    val rootFile = entry.file ?: return@firstOrNull false
                    rootFile == applicationRoot || VfsUtil.isAncestor(rootFile, applicationRoot, false)
                } ?: rootModel.addContentEntry(applicationRoot)

                configureRoot(contentEntry, "$basePath/app", JavaSourceRootType.SOURCE, report, "Source root app/")
                configureRoot(contentEntry, "$basePath/test", JavaSourceRootType.TEST_SOURCE, report, "Test root test/")
                configureResourceRoot(contentEntry, "$basePath/conf", report, "Resources root conf/")
                configureTestResourceRoot(contentEntry, "$basePath/test/resources", report, "Test resources root test/resources/")
                configureTestResourceRoot(contentEntry, "$basePath/test/conf", report, "Test resources root test/conf/")

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
            it.file == vFile && it.rootType == type
        }

        if (!alreadyConfigured) {
            removeConflictingSourceFolders(contentEntry, vFile)
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
            it.file == vFile && it.rootType == JavaResourceRootType.RESOURCE
        }

        if (!alreadyConfigured) {
            removeConflictingSourceFolders(contentEntry, vFile)
            contentEntry.addSourceFolder(vFile, JavaResourceRootType.RESOURCE)
        }
        report.ok(label, "configured")
    }

    private fun configureTestResourceRoot(
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
            it.file == vFile && it.rootType == JavaResourceRootType.TEST_RESOURCE
        }

        if (!alreadyConfigured) {
            removeConflictingSourceFolders(contentEntry, vFile)
            contentEntry.addSourceFolder(vFile, JavaResourceRootType.TEST_RESOURCE)
        }
        report.ok(label, "configured")
    }

    private fun removeConflictingSourceFolders(
        contentEntry: com.intellij.openapi.roots.ContentEntry,
        vFile: com.intellij.openapi.vfs.VirtualFile,
    ) {
        contentEntry.sourceFolders
            .filter { it.file == vFile }
            .forEach { contentEntry.removeSourceFolder(it) }
    }
}
