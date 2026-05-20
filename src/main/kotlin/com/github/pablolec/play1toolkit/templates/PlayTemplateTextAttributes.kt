package com.github.pablolec.play1toolkit.templates

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Font

object PlayTemplateTextAttributes {
    val NAVIGABLE_REFERENCE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "PLAY_TEMPLATE_NAVIGABLE_REFERENCE",
        TextAttributes(
            null,
            null,
            null,
            EffectType.LINE_UNDERSCORE,
            Font.BOLD
        )
    )

    val NAVIGABLE_REFERENCE_FALLBACK: Array<TextAttributesKey> = arrayOf(
        NAVIGABLE_REFERENCE,
        DefaultLanguageHighlighterColors.IDENTIFIER,
        HighlighterColors.TEXT
    )
}
