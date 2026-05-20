package com.github.pablolec.play1toolkit.templates.model

import com.intellij.openapi.vfs.VirtualFile

data class PlayTemplateFile(
    val logicalPath: String,
    val virtualFile: VirtualFile,
    val controllerName: String?,
    val actionName: String?
)

data class PlayCustomTagInfo(
    val name: String,
    val qualifiedName: String,
    val logicalPath: String,
    val virtualFile: VirtualFile,
    val parameters: Set<String>
)
