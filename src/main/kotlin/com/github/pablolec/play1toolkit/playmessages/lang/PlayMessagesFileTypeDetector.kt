package com.github.pablolec.play1toolkit.playmessages.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile

/**
 * Detects Play 1 messages files: conf/messages and conf/messages.<locale>.
 * Only matches files directly inside a conf/ directory, avoiding false positives.
 * Works as a safety net alongside the fileType extension point registration.
 */
class PlayMessagesFileTypeDetector : FileTypeRegistry.FileTypeDetector {

    override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? {
        val name = file.name
        val isMessagesFile = name == "messages" || name.matches(Regex("messages\\.[a-zA-Z][a-zA-Z0-9_\\-]*"))
        if (!isMessagesFile) return null
        // Only recognize files directly inside a conf/ directory
        if (file.parent?.name != "conf") return null
        return PlayMessagesFileType
    }

    override fun getDesiredContentPrefixLength() = 0
}
