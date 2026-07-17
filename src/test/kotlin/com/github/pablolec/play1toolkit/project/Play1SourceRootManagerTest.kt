package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.model.RepairReport
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Files
import java.nio.file.Paths

class Play1SourceRootManagerTest : BasePlatformTestCase() {

    fun `test configures Play source test and resource roots under nested application root`() {
        val applicationRoot = Files.createTempDirectory("play-root-test")
        Files.createDirectories(applicationRoot.resolve("app/controllers"))
        Files.createDirectories(applicationRoot.resolve("test/resources"))
        Files.createDirectories(applicationRoot.resolve("conf"))
        Files.writeString(applicationRoot.resolve("app/controllers/Application.java"), "class Application {}")
        Files.writeString(applicationRoot.resolve("test/ApplicationTest.java"), "class ApplicationTest {}")
        Files.writeString(applicationRoot.resolve("conf/application.conf"), "application.mode=dev")
        Files.writeString(applicationRoot.resolve("test/resources/test-data.txt"), "data")

        val applicationPath = applicationRoot.toString()
        val report = RepairReport(project.name)

        Play1SourceRootManager.configureSourceRoots(project, report, applicationPath)

        val sourceFolders = ModuleRootManager.getInstance(module).contentEntries.flatMap { it.sourceFolders.toList() }
        assertTrue(sourceFolders.joinToString("\n") { "${it.rootType}: ${it.file?.path}" }, sourceFolders.any {
            it.file?.path == applicationRoot.resolve("app").toString() &&
                it.rootType == JavaSourceRootType.SOURCE
        })
        assertTrue(sourceFolders.joinToString("\n") { "${it.rootType}: ${it.file?.path}" }, sourceFolders.any {
            it.file?.path == applicationRoot.resolve("test").toString() &&
                it.rootType == JavaSourceRootType.TEST_SOURCE
        })
        assertTrue(sourceFolders.joinToString("\n") { "${it.rootType}: ${it.file?.path}" }, sourceFolders.any {
            it.file?.path == applicationRoot.resolve("conf").toString() &&
                it.rootType == JavaResourceRootType.RESOURCE
        })
        assertTrue(sourceFolders.joinToString("\n") { "${it.rootType}: ${it.file?.path}" }, sourceFolders.any {
            it.file?.path == applicationRoot.resolve("test/resources").toString() &&
            it.rootType == JavaResourceRootType.TEST_RESOURCE
        })
    }

    fun `test configures Play roots when workspace parent is the module content root`() {
        val workspaceRoot = Files.createTempDirectory("play-workspace-test")
        val applicationRoot = workspaceRoot.resolve("apps/sample")
        Files.createDirectories(applicationRoot.resolve("conf"))
        Files.createDirectories(applicationRoot.resolve("app/controllers"))
        Files.createDirectories(applicationRoot.resolve("test/resources"))
        Files.writeString(applicationRoot.resolve("conf/application.conf"), "application.name=sample")
        Files.writeString(applicationRoot.resolve("conf/routes"), "GET / Application.index")
        Files.writeString(applicationRoot.resolve("app/controllers/Application.java"), "class Application {}")
        Files.writeString(applicationRoot.resolve("test/ApplicationTest.java"), "class ApplicationTest {}")
        Files.writeString(applicationRoot.resolve("test/resources/data.txt"), "data")
        val workspaceVFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(workspaceRoot)!!

        WriteAction.runAndWait<Exception> {
            val model = ModuleRootManager.getInstance(module).modifiableModel
            try {
                model.addContentEntry(workspaceVFile)
                model.commit()
            } catch (e: Exception) {
                model.dispose()
                throw e
            }
        }

        val applicationPath = Paths.get(workspaceRoot.toString(), "apps", "sample").toString()
        val normalizedApplicationPath = applicationPath.replace('\\', '/')
        val report = RepairReport(project.name)

        Play1SourceRootManager.configureSourceRoots(project, report, applicationPath)

        val sourceFolders = ModuleRootManager.getInstance(module).contentEntries.flatMap { it.sourceFolders.toList() }
        val normalizedRoots = sourceFolders.map {
            it.rootType to it.file?.path?.replace('\\', '/')
        }

        assertTrue(normalizedRoots.toString(), normalizedRoots.contains(JavaSourceRootType.SOURCE to "$normalizedApplicationPath/app"))
        assertTrue(normalizedRoots.toString(), normalizedRoots.contains(JavaSourceRootType.TEST_SOURCE to "$normalizedApplicationPath/test"))
        assertTrue(normalizedRoots.toString(), normalizedRoots.contains(JavaResourceRootType.RESOURCE to "$normalizedApplicationPath/conf"))
        assertTrue(
            normalizedRoots.toString(),
            normalizedRoots.contains(JavaResourceRootType.TEST_RESOURCE to "$normalizedApplicationPath/test/resources")
        )
    }
}
