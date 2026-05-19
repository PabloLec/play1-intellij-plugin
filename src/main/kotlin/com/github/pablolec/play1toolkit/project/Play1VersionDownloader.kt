package com.github.pablolec.play1toolkit.project

import com.github.pablolec.play1toolkit.detection.Play1HomeValidator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

object Play1VersionDownloader {

    data class PlayRelease(val version: String, val zipUrl: String, val description: String)

    val KNOWN_RELEASES = listOf(
        PlayRelease(
            "1.5.3",
            "https://github.com/playframework/play1/releases/download/1.5.3/play-1.5.3.zip",
            "Play 1.5.3 — recommended for dependency resolution"
        ),
        PlayRelease(
            "1.4.6",
            "https://github.com/playframework/play1/releases/download/1.4.6/play-1.4.6.zip",
            "Play 1.4.6"
        ),
    )

    val RECOMMENDED_FOR_DEPS: PlayRelease = KNOWN_RELEASES.first()

    fun cacheDir(): Path = Paths.get(System.getProperty("user.home"), ".play1toolkit", "versions")

    fun getInstalledPath(version: String): Path? {
        val dir = cacheDir().resolve("play-$version")
        if (!dir.toFile().exists()) return null
        return if (Play1HomeValidator.validate(dir).valid) dir else null
    }

    fun isInstalled(version: String): Boolean = getInstalledPath(version) != null

    fun listInstalled(): List<String> {
        val dir = cacheDir().toFile()
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isDirectory && f.name.startsWith("play-") }
            ?.filter { Play1HomeValidator.validate(it.toPath()).valid }
            ?.map { it.name.removePrefix("play-") }
            ?.sorted()
            ?: emptyList()
    }

    fun download(release: PlayRelease, indicator: ProgressIndicator): Path? {
        val destDir = cacheDir().resolve("play-${release.version}")
        if (isInstalled(release.version)) return destDir

        Files.createDirectories(cacheDir())

        val tempZip = Files.createTempFile("play-${release.version}-", ".zip")
        try {
            indicator.text = "Downloading Play ${release.version}..."
            indicator.isIndeterminate = false
            downloadFile(release.zipUrl, tempZip, indicator)

            indicator.text = "Extracting Play ${release.version}..."
            indicator.fraction = 0.0
            extractZip(tempZip, destDir, indicator)

            val validation = Play1HomeValidator.validate(destDir)
            if (!validation.valid) {
                destDir.toFile().deleteRecursively()
                return null
            }

            return destDir
        } catch (e: ProcessCanceledException) {
            destDir.toFile().deleteRecursively()
            throw e
        } catch (_: Exception) {
            destDir.toFile().deleteRecursively()
            return null
        } finally {
            tempZip.toFile().delete()
        }
    }

    private fun downloadFile(rawUrl: String, dest: Path, indicator: ProgressIndicator) {
        var url = rawUrl
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
                    if (total > 0) indicator.fraction = (downloaded.toDouble() / total) * 0.7
                    if (indicator.isCanceled) throw ProcessCanceledException()
                }
            }
        }
    }

    private fun extractZip(zipFile: Path, destDir: Path, indicator: ProgressIndicator) {
        ZipFile(zipFile.toFile()).use { zip ->
            val entries = zip.entries().toList()
            val rootPrefix = entries.firstOrNull()?.name?.let {
                val slash = it.indexOf('/')
                if (slash >= 0) it.substring(0, slash + 1) else ""
            } ?: ""

            entries.forEachIndexed { i, entry ->
                indicator.fraction = 0.7 + (i.toDouble() / entries.size) * 0.3
                if (indicator.isCanceled) throw ProcessCanceledException()

                val relativeName = if (rootPrefix.isNotEmpty() && entry.name.startsWith(rootPrefix))
                    entry.name.substring(rootPrefix.length)
                else entry.name

                if (relativeName.isBlank()) return@forEachIndexed

                val target = destDir.resolve(relativeName)
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                    if (relativeName == "play" || relativeName == "play.bat") {
                        target.toFile().setExecutable(true)
                    }
                }
            }
        }
    }
}
