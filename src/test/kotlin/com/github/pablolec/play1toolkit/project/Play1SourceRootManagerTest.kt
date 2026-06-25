package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.model.RepairReport
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Files

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
}
