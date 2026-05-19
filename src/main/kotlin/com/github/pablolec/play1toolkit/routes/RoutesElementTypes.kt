package com.github.pablolec.play1toolkit.routes

import com.intellij.psi.tree.IElementType

class RoutesElementType(debugName: String) : IElementType(debugName, RoutesLanguage) {
    override fun toString(): String = "RoutesElementType.$debugName"
}

object RoutesElementTypes {
    @JvmField val ROUTE = RoutesElementType("ROUTE")
}
