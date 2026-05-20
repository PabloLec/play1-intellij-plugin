package com.github.pablolec.play1toolkit.templates

import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

object PlayTemplateTextAttributes {
    val NAVIGABLE_REFERENCE: TextAttributes = TextAttributes(
        null,
        null,
        JBColor(Color(0x0033CC), Color(0x6897BB)),
        EffectType.LINE_UNDERSCORE,
        Font.BOLD
    )
}
