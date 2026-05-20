package com.github.pablolec.play1toolkit.response

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object PlayResponseIcons {
    val HTML: Icon = load("html")
    val JSON: Icon = load("json")
    val XML: Icon = load("xml")
    val TEXT: Icon = load("text")
    val BINARY: Icon = load("binary")
    val REDIRECT: Icon = load("redirect")
    val STATUS: Icon = load("status")
    val ERROR: Icon = load("error")
    val MIXED: Icon = load("mixed")
    val UNKNOWN: Icon = load("unknown")

    fun forKind(kind: PlayResponseKind): Icon = when (kind) {
        PlayResponseKind.HTML -> HTML
        PlayResponseKind.JSON -> JSON
        PlayResponseKind.XML -> XML
        PlayResponseKind.TEXT -> TEXT
        PlayResponseKind.BINARY -> BINARY
        PlayResponseKind.REDIRECT -> REDIRECT
        PlayResponseKind.STATUS -> STATUS
        PlayResponseKind.ERROR -> ERROR
        PlayResponseKind.MIXED -> MIXED
        PlayResponseKind.UNKNOWN -> UNKNOWN
    }

    private fun load(name: String): Icon = IconLoader.getIcon("/icons/response/$name.svg", PlayResponseIcons::class.java)
}
