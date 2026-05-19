package com.github.pablolec.play1toolkit.routes

import com.intellij.lang.Language

object RoutesLanguage : Language("Routes") {
    private fun readResolve(): Any = RoutesLanguage
}
