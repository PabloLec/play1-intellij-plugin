package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.model.RepairReport
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Paths

object Play1SourceRootManager {

    fun configureSourceRoots(project: Project, report: RepairReport, applicationPath: String? = project.basePath) {
        val module = Play1ModuleResolver.findModule(project, applicationPath) ?: run {
            report.skipped("Source roots", "no IntelliJ module found")
            return
        }
        val basePath = applicationPath ?: return
        val base = Paths.get(basePath)
        val localFileSystem = LocalFileSystem.getInstance()
        val applicationRoot = localFileSystem.refreshAndFindFileByPath(basePath) ?: run {
            report.error("Source roots", "Cannot locate Play application root on disk")
            return
        }
        val sourceRoots = listOf(
            RootSpec(localFileSystem.refreshAndFindFileByPath(base.resolve("app").toString()), JavaSourceRootType.SOURCE, "Source root app/"),
            RootSpec(localFileSystem.refreshAndFindFileByPath(base.resolve("test").toString()), JavaSourceRootType.TEST_SOURCE, "Test root test/"),
            RootSpec(localFileSystem.refreshAndFindFileByPath(base.resolve("conf").toString()), JavaResourceRootType.RESOURCE, "Resources root conf/"),
            RootSpec(
                localFileSystem.refreshAndFindFileByPath(base.resolve("test").resolve("resources").toString()),
                JavaResourceRootType.TEST_RESOURCE,
                "Test resources root test/resources/"
            ),
            RootSpec(
                localFileSystem.refreshAndFindFileByPath(base.resolve("test").resolve("conf").toString()),
                JavaResourceRootType.TEST_RESOURCE,
                "Test resources root test/conf/"
            ),
        )

        WriteAction.runAndWait<Exception> {
            val rootModel = ModuleRootManager.getInstance(module).modifiableModel
            try {
                val contentEntry = rootModel.contentEntries.firstOrNull { entry ->
                    val rootFile = entry.file ?: return@firstOrNull false
                    rootFile == applicationRoot || VfsUtil.isAncestor(rootFile, applicationRoot, false)
                } ?: rootModel.addContentEntry(applicationRoot)

                sourceRoots.forEach { root ->
                    configureRoot(contentEntry, root, report)
                }

                rootModel.commit()
            } catch (e: Exception) {
                rootModel.dispose()
                report.error("Source roots", e.message ?: "Unknown error")
            }
        }
    }

    private fun configureRoot(
        contentEntry: com.intellij.openapi.roots.ContentEntry,
        root: RootSpec,
        report: RepairReport,
    ) {
        val vFile = root.file
        if (vFile == null || !vFile.exists()) {
            report.skipped(root.label, "directory not found")
            return
        }

        val alreadyConfigured = contentEntry.sourceFolders.any {
            it.file == vFile && it.rootType == root.type
        }

        if (!alreadyConfigured) {
            removeConflictingSourceFolders(contentEntry, vFile)
            contentEntry.addSourceFolder(vFile, root.type)
        }
        report.ok(root.label, "configured")
    }

    private fun removeConflictingSourceFolders(
        contentEntry: com.intellij.openapi.roots.ContentEntry,
        vFile: com.intellij.openapi.vfs.VirtualFile,
    ) {
        contentEntry.sourceFolders
            .filter { it.file == vFile }
            .forEach { contentEntry.removeSourceFolder(it) }
    }

    private data class RootSpec(
        val file: com.intellij.openapi.vfs.VirtualFile?,
        val type: org.jetbrains.jps.model.module.JpsModuleSourceRootType<*>,
        val label: String,
    )
}
