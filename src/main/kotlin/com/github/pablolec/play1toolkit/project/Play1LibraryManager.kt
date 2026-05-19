package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.github.pablolec.play1toolkit.model.RepairReport
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Play1LibraryManager {

    internal const val LIBRARY_NAME = "Play 1 Framework"

    fun attachLibraries(project: Project, playHome: Path, report: RepairReport) {
        val module = ModuleManager.getInstance(project).modules.firstOrNull()
        if (module == null) {
            report.skipped("Library attachment", "no IntelliJ module found — open via File > Open to create one")
            return
        }

        val frameworkDir = playHome.resolve("framework")
        val playJar = Play1HomeValidator.findPlayJar(frameworkDir)

        if (playJar == null) {
            report.error("Play framework jar", "Not found in $frameworkDir")
            return
        }
        report.ok("Play framework jar", playJar.fileName.toString())

        val libDir = frameworkDir.resolve("lib")
        val srcDir = frameworkDir.resolve("src")
        val projectLibDir = Paths.get(project.basePath ?: "").resolve("lib")

        val jarRoots = mutableListOf<String>()
        val sourceRoots = mutableListOf<String>()

        // Add main play JAR
        jarRoots.add(toJarUrl(playJar))

        // Add framework lib/*.jar
        var frameworkLibCount = 0
        if (Files.isDirectory(libDir)) {
            Files.list(libDir).use { stream ->
                stream.filter { it.toString().endsWith(".jar") }.forEach { jar ->
                    jarRoots.add(toJarUrl(jar))
                    frameworkLibCount++
                }
            }
        }
        report.ok("Framework lib jars", "$frameworkLibCount attached")

        // Add sources if available
        if (Files.isDirectory(srcDir)) {
            sourceRoots.add(srcDir.toUri().toString())
            report.ok("Framework sources", "attached")
        } else {
            report.skipped("Framework sources", "not found")
        }

        // Add project lib/*.jar
        var projectLibCount = 0
        if (Files.isDirectory(projectLibDir)) {
            Files.list(projectLibDir).use { stream ->
                stream.filter { it.toString().endsWith(".jar") }.forEach { jar ->
                    jarRoots.add(toJarUrl(jar))
                    projectLibCount++
                }
            }
        }
        report.ok("Project lib jars", "$projectLibCount attached")

        WriteAction.runAndWait<Exception> {
            val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            val tableModel = libraryTable.modifiableModel

            var library = libraryTable.getLibraryByName(LIBRARY_NAME)
            if (library == null) {
                library = tableModel.createLibrary(LIBRARY_NAME)
            }

            val libModel = library.modifiableModel
            libModel.getUrls(OrderRootType.CLASSES).forEach { libModel.removeRoot(it, OrderRootType.CLASSES) }
            libModel.getUrls(OrderRootType.SOURCES).forEach { libModel.removeRoot(it, OrderRootType.SOURCES) }
            for (url in jarRoots) {
                libModel.addRoot(url, OrderRootType.CLASSES)
            }
            for (url in sourceRoots) {
                libModel.addRoot(url, OrderRootType.SOURCES)
            }
            libModel.commit()
            tableModel.commit()

            val rootModel = ModuleRootManager.getInstance(module).modifiableModel
            val alreadyAttached = rootModel.orderEntries
                .filterIsInstance<LibraryOrderEntry>()
                .any { it.libraryName == LIBRARY_NAME }

            if (!alreadyAttached) {
                rootModel.addLibraryEntry(library)
            }
            rootModel.commit()
        }

        report.ok("Library \"$LIBRARY_NAME\"", "attached to module")
    }

    private fun toJarUrl(jar: Path): String =
        VirtualFileManager.constructUrl("jar", jar.toAbsolutePath().toString() + "!/")
}
