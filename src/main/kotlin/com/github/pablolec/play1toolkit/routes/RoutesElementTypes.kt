package com.github.pablolec.play1toolkit.routes

import com.intellij.psi.tree.IElementType

class RoutesElementType(private val elementName: String) : IElementType(elementName, RoutesLanguage) {
    override fun toString(): String = "RoutesElementType.$elementName"
}

object RoutesElementTypes {
    @JvmField val ROUTE = RoutesElementType("ROUTE")
}
