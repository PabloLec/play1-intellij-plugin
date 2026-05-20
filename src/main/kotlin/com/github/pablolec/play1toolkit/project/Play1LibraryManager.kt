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

    internal const val FRAMEWORK_LIBRARY_NAME = "Play v1 Framework"
    internal const val PROJECT_LIBRARY_NAME = "Play v1 Project Libraries"
    internal const val LEGACY_LIBRARY_NAME = "Play 1 Framework"

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

        val sourceRoots = mutableListOf<String>()

        val classpathJars = buildProjectClasspathJars(playHome, project.basePath)
        val projectJarRoots = classpathJars.projectJars.map(::toJarUrl)
        val frameworkJarRoots = buildList {
            add(toJarUrl(playJar))
            classpathJars.frameworkJars.forEach { add(toJarUrl(it)) }
        }

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

            val projectLibrary = ensureLibrary(libraryTable, tableModel, PROJECT_LIBRARY_NAME)
            replaceLibraryRoots(projectLibrary, projectJarRoots, emptyList())

            val frameworkLibrary = ensureLibrary(libraryTable, tableModel, FRAMEWORK_LIBRARY_NAME)
            replaceLibraryRoots(frameworkLibrary, frameworkJarRoots, sourceRoots)

            libraryTable.getLibraryByName(LEGACY_LIBRARY_NAME)?.let { legacyLibrary ->
                tableModel.removeLibrary(legacyLibrary)
            }
            tableModel.commit()

            val rootModel = ModuleRootManager.getInstance(module).modifiableModel
            rootModel.orderEntries
                .filterIsInstance<LibraryOrderEntry>()
                .filter { it.libraryName in setOf(PROJECT_LIBRARY_NAME, FRAMEWORK_LIBRARY_NAME, LEGACY_LIBRARY_NAME) }
                .toList()
                .forEach { rootModel.removeOrderEntry(it) }

            libraryTable.getLibraryByName(PROJECT_LIBRARY_NAME)?.let { rootModel.addLibraryEntry(it) }
            libraryTable.getLibraryByName(FRAMEWORK_LIBRARY_NAME)?.let { rootModel.addLibraryEntry(it) }
            rootModel.commit()
        }

        report.ok("Library \"$PROJECT_LIBRARY_NAME\"", "attached to module")
        report.ok("Library \"$FRAMEWORK_LIBRARY_NAME\"", "attached to module")
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

    internal fun managedLibraryNames(): Set<String> =
        setOf(PROJECT_LIBRARY_NAME, FRAMEWORK_LIBRARY_NAME, LEGACY_LIBRARY_NAME)

    private fun ensureLibrary(
        libraryTable: com.intellij.openapi.roots.libraries.LibraryTable,
        tableModel: com.intellij.openapi.roots.libraries.LibraryTable.ModifiableModel,
        name: String,
    ): Library {
        return libraryTable.getLibraryByName(name) ?: tableModel.createLibrary(name)
    }

    private fun replaceLibraryRoots(library: Library, classRoots: List<String>, sourceRoots: List<String>) {
        val libModel = library.modifiableModel
        libModel.getUrls(OrderRootType.CLASSES).forEach { libModel.removeRoot(it, OrderRootType.CLASSES) }
        libModel.getUrls(OrderRootType.SOURCES).forEach { libModel.removeRoot(it, OrderRootType.SOURCES) }
        classRoots.forEach { libModel.addRoot(it, OrderRootType.CLASSES) }
        sourceRoots.forEach { libModel.addRoot(it, OrderRootType.SOURCES) }
        libModel.commit()
    }

    private fun toJarUrl(jar: Path): String =
        VirtualFileManager.constructUrl("jar", jar.toAbsolutePath().toString() + "!/")
}
