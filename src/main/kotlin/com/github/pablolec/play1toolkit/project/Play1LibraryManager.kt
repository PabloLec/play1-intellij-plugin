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
import java.util.Locale

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

        val classpathJars = buildProjectClasspathJars(playHome, project.basePath)
        classpathJars.projectJars.forEach { jarRoots.add(toJarUrl(it)) }
        classpathJars.frameworkJars.forEach { jarRoots.add(toJarUrl(it)) }

        report.ok(
            "Project lib jars",
            "${classpathJars.projectJars.size} attached"
        )
        report.ok(
            "Framework lib jars",
            buildString {
                append("${classpathJars.frameworkJars.size} attached")
                if (classpathJars.overriddenFrameworkJars.isNotEmpty()) {
                    append(", ${classpathJars.overriddenFrameworkJars.size} overridden by project lib/")
                }
            }
        )

        // Add sources if available
        if (Files.isDirectory(srcDir)) {
            sourceRoots.add(srcDir.toUri().toString())
            report.ok("Framework sources", "attached")
        } else {
            report.skipped("Framework sources", "not found")
        }

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

    internal data class ClasspathJars(
        val projectJars: List<Path>,
        val frameworkJars: List<Path>,
        val overriddenFrameworkJars: List<Path>,
    )

    internal fun buildProjectClasspathJars(playHome: Path, projectBasePath: String?): ClasspathJars {
        val frameworkLibDir = playHome.resolve("framework").resolve("lib")
        val projectLibDir = projectBasePath?.let { Paths.get(it).resolve("lib") }

        val projectJars = listJarFiles(projectLibDir)
        val projectKeys = projectJars.mapTo(linkedSetOf(), ::artifactKey)

        val keptFrameworkJars = mutableListOf<Path>()
        val overriddenFrameworkJars = mutableListOf<Path>()

        listJarFiles(frameworkLibDir).forEach { jar ->
            if (artifactKey(jar) in projectKeys) {
                overriddenFrameworkJars.add(jar)
            } else {
                keptFrameworkJars.add(jar)
            }
        }

        return ClasspathJars(
            projectJars = projectJars,
            frameworkJars = keptFrameworkJars,
            overriddenFrameworkJars = overriddenFrameworkJars,
        )
    }

    private fun listJarFiles(dir: Path?): List<Path> {
        if (dir == null || !Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream
                .filter { path -> path.fileName.toString().lowercase(Locale.ROOT).endsWith(".jar") }
                .sorted { a, b -> a.fileName.toString().compareTo(b.fileName.toString()) }
                .toList()
        }
    }

    internal fun artifactKey(jar: Path): String {
        val name = jar.fileName.toString().removeSuffix(".jar")
        return name.replace(Regex("-\\d.*$"), "")
    }

    private fun toJarUrl(jar: Path): String =
        VirtualFileManager.constructUrl("jar", jar.toAbsolutePath().toString() + "!/")
}
