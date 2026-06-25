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

    fun attachLibraries(
        project: Project,
        playHome: Path,
        report: RepairReport,
        applicationPath: String? = project.basePath,
    ) {
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
        val sourceRoots = mutableListOf<String>()

        val classpathJars = buildProjectClasspathJars(playHome, applicationPath)
        val projectJarRoots = (classpathJars.projectJars + classpathJars.supplementalProjectJars).map(::toJarUrl)
        val frameworkJarRoots = buildList {
            add(toJarUrl(playJar))
            classpathJars.frameworkJars.forEach { add(toJarUrl(it)) }
        }

        report.ok(
            "Project lib jars",
            buildString {
                append("${classpathJars.projectJars.size} attached")
                if (classpathJars.supplementalProjectJars.isNotEmpty()) {
                    append(", ${classpathJars.supplementalProjectJars.size} supplemented from local dependency cache")
                }
            }
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
        val supplementalProjectJars: List<Path>,
        val frameworkJars: List<Path>,
        val overriddenFrameworkJars: List<Path>,
    )

    internal fun buildProjectClasspathJars(playHome: Path, projectBasePath: String?): ClasspathJars =
        buildProjectClasspathJars(playHome, projectBasePath, Paths.get(System.getProperty("user.home")))

    internal fun buildProjectClasspathJarsForTest(playHome: Path, projectBasePath: String?, homeDir: Path): ClasspathJars =
        buildProjectClasspathJars(playHome, projectBasePath, homeDir)

    private fun buildProjectClasspathJars(playHome: Path, projectBasePath: String?, homeDir: Path): ClasspathJars {
        val frameworkLibDir = playHome.resolve("framework").resolve("lib")
        val projectDir = projectBasePath?.let { Paths.get(it) }
        val projectLibDir = projectDir?.resolve("lib")

        val projectJars = listJarFiles(projectLibDir)
        val supplementalProjectJars = resolveSupplementalProjectJars(projectDir, projectJars, homeDir)
        val projectKeys = (projectJars + supplementalProjectJars).mapTo(linkedSetOf(), ::artifactKey)

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
            supplementalProjectJars = supplementalProjectJars,
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

    internal data class DeclaredDependency(
        val group: String,
        val artifact: String,
        val version: String,
    )

    internal fun resolveSupplementalProjectJars(
        projectDir: Path?,
        existingProjectJars: List<Path>,
        homeDir: Path = Paths.get(System.getProperty("user.home")),
    ): List<Path> {
        if (projectDir == null) return emptyList()
        val declaredDependencies = parseDeclaredDependencies(projectDir.resolve("conf").resolve("dependencies.yml"))
        if (declaredDependencies.isEmpty()) return emptyList()

        val existingKeys = existingProjectJars.mapTo(linkedSetOf(), ::artifactKey)
        val seenResolvedJars = linkedSetOf<Path>()

        return declaredDependencies
            .asSequence()
            .filterNot { dependency -> dependency.artifact in existingKeys }
            .mapNotNull { dependency -> resolveFromLocalCaches(dependency, homeDir) }
            .filter { resolvedJar -> seenResolvedJars.add(resolvedJar) }
            .sortedBy { it.fileName.toString().lowercase(Locale.ROOT) }
            .toList()
    }

    internal fun parseDeclaredDependencies(dependenciesFile: Path): List<DeclaredDependency> {
        if (!Files.isRegularFile(dependenciesFile)) return emptyList()

        val entriesByArtifact = linkedMapOf<String, DeclaredDependency>()
        Files.newBufferedReader(dependenciesFile).use { reader ->
            reader.forEachLine { rawLine ->
                val line = rawLine.substringBefore('#')
                val match = DECLARED_DEPENDENCY_REGEX.matchEntire(line) ?: return@forEachLine
                val dependency = DeclaredDependency(
                    group = match.groupValues[1],
                    artifact = match.groupValues[2],
                    version = match.groupValues[3],
                )
                entriesByArtifact[dependency.artifact] = dependency
            }
        }
        return entriesByArtifact.values.toList()
    }

    internal fun resolveFromLocalCaches(
        dependency: DeclaredDependency,
        homeDir: Path = Paths.get(System.getProperty("user.home")),
    ): Path? {
        val mavenJar = homeDir
            .resolve(".m2")
            .resolve("repository")
            .resolve(dependency.group.replace('.', '/'))
            .resolve(dependency.artifact)
            .resolve(dependency.version)
            .resolve("${dependency.artifact}-${dependency.version}.jar")
        if (Files.isRegularFile(mavenJar)) {
            return mavenJar
        }

        val ivyJar = homeDir
            .resolve(".ivy2")
            .resolve("cache")
            .resolve(dependency.group)
            .resolve(dependency.artifact)
            .resolve("jars")
            .resolve("${dependency.artifact}-${dependency.version}.jar")
        if (Files.isRegularFile(ivyJar)) {
            return ivyJar
        }

        return null
    }

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

    private val DECLARED_DEPENDENCY_REGEX =
        Regex("""^\s{2}-\s+([A-Za-z0-9_.-]+)\s*->\s*([A-Za-z0-9_.-]+)\s+([^:\s]+)\s*:?\s*$""")
}
