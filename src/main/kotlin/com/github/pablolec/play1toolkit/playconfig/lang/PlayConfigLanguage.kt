package com.github.pablolec.play1toolkit.playconfig.lang

import com.intellij.lang.Language

object PlayConfigLanguage : Language("PlayConfig") {
    private fun readResolve(): Any = PlayConfigLanguage
}
