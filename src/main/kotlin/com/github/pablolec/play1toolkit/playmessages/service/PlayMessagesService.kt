package com.github.pablolec.play1toolkit.playmessages.service

import com.github.pablolec.play1toolkit.playmessages.model.PlayMessageEntry
import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesFile
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class PlayMessagesService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): PlayMessagesService =
            project.getService(PlayMessagesService::class.java)

        /** Count Java-style format specifiers: %s, %d, %f, etc. %% is a literal and not counted. */
        fun countPlaceholders(value: String): Int {
            var count = 0
            var i = 0
            while (i < value.length - 1) {
                if (value[i] == '%') {
                    if (value[i + 1] == '%') {
                        i += 2 // skip %%
                    } else if (value[i + 1].isLetter()) {
                        count++
                        i += 2
                    } else {
                        i++
                    }
                } else {
                    i++
                }
            }
            return count
        }

        /** Extract locale from filename: "messages" → null, "messages.fr" → "fr" */
        fun localeFromFileName(name: String): String? {
            val dot = name.indexOf('.')
            return if (dot < 0) null else name.substring(dot + 1)
        }
    }

    fun getMessagesFiles(): List<PlayMessagesFile> {
        if (DumbService.isDumb(project)) return emptyList()
        val basePath = project.basePath ?: return emptyList()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
        val confDir = baseDir.findChild("conf") ?: return emptyList()
        return confDir.children
            .filter { it.name == "messages" || it.name.startsWith("messages.") }
            .mapNotNull { PsiManager.getInstance(project).findFile(it) as? PlayMessagesFile }
    }

    fun allEntries(): List<PlayMessageEntry> {
        val files = getMessagesFiles()
        if (files.isEmpty()) return emptyList()
        return files.flatMap { file ->
            CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result.create(
                    buildEntries(file),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        }
    }

    private fun buildEntries(file: PlayMessagesFile): List<PlayMessageEntry> {
        val doc = PsiDocumentManager.getInstance(project).getDocument(file)
        return file.getProperties().map { prop ->
            val line = if (doc != null) doc.getLineNumber(prop.textOffset) + 1 else 0
            PlayMessageEntry(
                key = prop.key,
                locale = file.locale,
                value = prop.valueText,
                property = prop,
                lineNumber = line
            )
        }
    }

    fun allKeys(): List<String> = allEntries().map { it.key }.distinct().sorted()

    fun localesAvailable(): List<String> = allEntries().mapNotNull { it.locale }.distinct().sorted()

    fun entriesForKey(key: String): List<PlayMessageEntry> =
        allEntries().filter { it.key == key }.sortedWith(compareBy(nullsFirst()) { it.locale })

    fun defaultEntry(key: String): PlayMessageEntry? = entriesForKey(key).firstOrNull { it.locale == null }
}
