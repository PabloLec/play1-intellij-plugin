package com.github.pablolec.play1toolkit.project

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

object Play1ManagedPythonRuntime {

    data class RuntimeArtifact(
        val cacheKey: String,
        val fileName: String,
        val url: String,
        val sha256: String,
        val executableRelativePath: String,
    )

    data class RuntimeProvisionResult(
        val executable: Path?,
        val errorMessage: String? = null,
    )

    private const val PYPY_VERSION = "7.3.19"

    private val linuxX64 = RuntimeArtifact(
        cacheKey = "pypy2.7-v$PYPY_VERSION-linux64",
        fileName = "pypy2.7-v$PYPY_VERSION-linux64.tar.bz2",
        url = "https://downloads.python.org/pypy/pypy2.7-v$PYPY_VERSION-linux64.tar.bz2",
        sha256 = "d38445508c2eaf14ebb380d9c1ded321c5ebeae31c7e66800173d83cb8ddf423",
        executableRelativePath = "bin/pypy",
    )

    private val linuxArm64 = RuntimeArtifact(
        cacheKey = "pypy2.7-v$PYPY_VERSION-aarch64",
        fileName = "pypy2.7-v$PYPY_VERSION-aarch64.tar.bz2",
        url = "https://downloads.python.org/pypy/pypy2.7-v$PYPY_VERSION-aarch64.tar.bz2",
        sha256 = "fe89d4fd4af13f76dfe7315975003518cf176520e3ccec1544a88d174f50910e",
        executableRelativePath = "bin/pypy",
    )

    private val macX64 = RuntimeArtifact(
        cacheKey = "pypy2.7-v$PYPY_VERSION-macos_x86_64",
        fileName = "pypy2.7-v$PYPY_VERSION-macos_x86_64.tar.bz2",
        url = "https://downloads.python.org/pypy/pypy2.7-v$PYPY_VERSION-macos_x86_64.tar.bz2",
        sha256 = "6be28d448d8e64fffc586d9b0ae4d09064a83ccaeb5b8060c651c5cd9ae06878",
        executableRelativePath = "bin/pypy",
    )

    private val macArm64 = RuntimeArtifact(
        cacheKey = "pypy2.7-v$PYPY_VERSION-macos_arm64",
        fileName = "pypy2.7-v$PYPY_VERSION-macos_arm64.tar.bz2",
        url = "https://downloads.python.org/pypy/pypy2.7-v$PYPY_VERSION-macos_arm64.tar.bz2",
        sha256 = "28780e0b908ad6db4b4e096f4237124be79ecc9731946d840d9c8749eb67a759",
        executableRelativePath = "bin/pypy",
    )

    private val windowsX64 = RuntimeArtifact(
        cacheKey = "pypy2.7-v$PYPY_VERSION-win64",
        fileName = "pypy2.7-v$PYPY_VERSION-win64.zip",
        url = "https://downloads.python.org/pypy/pypy2.7-v$PYPY_VERSION-win64.zip",
        sha256 = "fbdcd4fe681981c020a25c1a35225209dc3d651f6117ebe9e4d212b66a2b46ec",
        executableRelativePath = "pypy.exe",
    )

    fun cacheDir(): Path = Paths.get(System.getProperty("user.home"), ".play1toolkit", "runtimes")

    fun detectArtifactForCurrentPlatform(): RuntimeArtifact? =
        detectArtifact(osName = System.getProperty("os.name"), archName = System.getProperty("os.arch"))

    internal fun detectArtifact(osName: String, archName: String): RuntimeArtifact? {
        val os = osName.lowercase(Locale.ROOT)
        val arch = archName.lowercase(Locale.ROOT)
        return when {
            os.contains("linux") && arch in setOf("x86_64", "amd64") -> linuxX64
            os.contains("linux") && arch in setOf("aarch64", "arm64") -> linuxArm64
            os.contains("mac") && arch in setOf("x86_64", "amd64") -> macX64
            os.contains("mac") && arch in setOf("aarch64", "arm64") -> macArm64
            os.contains("win") && arch in setOf("x86_64", "amd64") -> windowsX64
            else -> null
        }
    }

    fun ensurePyPy2(
        indicator: ProgressIndicator?,
        onLine: (line: String, isError: Boolean) -> Unit = { _, _ -> },
    ): RuntimeProvisionResult {
        val artifact = detectArtifactForCurrentPlatform()
            ?: return RuntimeProvisionResult(
                executable = null,
                errorMessage = "unsupported platform: os=${System.getProperty("os.name")}, arch=${System.getProperty("os.arch")}",
            )
        val installDir = cacheDir().resolve(artifact.cacheKey)
        findExecutable(installDir, artifact)?.let { return RuntimeProvisionResult(it) }

        Files.createDirectories(cacheDir())
        val archivePath = cacheDir().resolve(artifact.fileName)
        val tempArchive = archivePath.resolveSibling("${artifact.fileName}.part")
        val tempExtractDir = cacheDir().resolve("${artifact.cacheKey}.tmp")

        try {
            indicator?.text = "Downloading managed PyPy 2.7 runtime..."
            onLine("~ Python 2 not found. Downloading managed PyPy 2.7 runtime for ${artifact.cacheKey}.", false)
            downloadFile(artifact.url, tempArchive, indicator)
            verifySha256(tempArchive, artifact.sha256)

            tempExtractDir.toFile().deleteRecursively()
            Files.createDirectories(tempExtractDir)
            indicator?.text = "Extracting managed PyPy 2.7 runtime..."
            extractArchive(tempArchive, tempExtractDir, artifact)

            val extractedRoot = tempExtractDir.resolve(artifact.cacheKey)
            if (!Files.isDirectory(extractedRoot)) {
                throw IOException("archive did not extract expected directory ${artifact.cacheKey}")
            }

            installDir.toFile().deleteRecursively()
            Files.move(extractedRoot, installDir, StandardCopyOption.REPLACE_EXISTING)
            tempExtractDir.toFile().deleteRecursively()
            Files.move(tempArchive, archivePath, StandardCopyOption.REPLACE_EXISTING)

            val executable = findExecutable(installDir, artifact)
                ?: throw IOException("PyPy executable not found after extraction")
            onLine("~ Managed PyPy ready: $executable", false)
            return RuntimeProvisionResult(executable)
        } catch (e: ProcessCanceledException) {
            tempArchive.toFile().delete()
            tempExtractDir.toFile().deleteRecursively()
            installDir.toFile().deleteRecursively()
            throw e
        } catch (e: Exception) {
            tempArchive.toFile().delete()
            tempExtractDir.toFile().deleteRecursively()
            installDir.toFile().deleteRecursively()
            val message = e.message ?: e::class.java.simpleName
            onLine("~ Managed PyPy provisioning failed: $message", true)
            return RuntimeProvisionResult(executable = null, errorMessage = message)
        }
    }

    private fun findExecutable(installDir: Path, artifact: RuntimeArtifact): Path? {
        val candidate = installDir.resolve(artifact.executableRelativePath)
        return if (Files.isRegularFile(candidate)) candidate else null
    }

    private fun downloadFile(urlString: String, dest: Path, indicator: ProgressIndicator?) {
        var url = urlString
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = URI.create(url).toURL().openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Play1-Toolkit-IntelliJ-Plugin")
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                url = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                if (++redirects > 10) break
            } else {
                break
            }
        }

        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            Files.newOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var downloaded = 0L
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    if (total > 0 && indicator != null) {
                        indicator.fraction = downloaded.toDouble() / total.toDouble()
                    }
                    if (indicator?.isCanceled == true) throw ProcessCanceledException()
                }
            }
        }
    }

    private fun verifySha256(file: Path, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffered = BufferedInputStream(input)
            val buffer = ByteArray(8192)
            var read: Int
            while (buffered.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true)) {
            "checksum mismatch for ${file.fileName}: expected $expected, got $actual"
        }
    }

    private fun extractArchive(archive: Path, destDir: Path, artifact: RuntimeArtifact) {
        if (artifact.fileName.endsWith(".zip")) {
            extractZip(archive, destDir)
            return
        }

        val process = ProcessBuilder("tar", "-xjf", archive.toAbsolutePath().toString(), "-C", destDir.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "tar extraction failed: $output" }
    }

    private fun extractZip(archive: Path, destDir: Path) {
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = destDir.resolve(entry.name).normalize()
                check(target.startsWith(destDir)) { "zip entry escapes target dir: ${entry.name}" }
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                    if (target.fileName.toString().equals("pypy", ignoreCase = true) ||
                        target.fileName.toString().equals("pypy.exe", ignoreCase = true)
                    ) {
                        target.toFile().setExecutable(true)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
