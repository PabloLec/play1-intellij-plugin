package com.github.pablolec.play1toolkit.playmessages.lang

import com.intellij.lang.Language

object PlayMessagesLanguage : Language("PlayMessages") {
    private fun readResolve(): Any = PlayMessagesLanguage
}
