package com.github.pablolec.play1toolkit.playconfig.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Hand-written lexer for Play 1 conf/application.conf files.
 *
 * Line format:
 *   key=value
 *   %profile.key=value
 *   # comment
 *   ; comment
 *   (blank line)
 *
 * State machine:
 *   LINE_START  → start of any line; decides if comment, key or blank
 *   AFTER_KEY   → after KEY token; expects SEPARATOR (=) or whitespace around it
 *   IN_VALUE    → reading the value until end of line; emits ENV_PLACEHOLDER for ${...}
 */
class PlayConfigLexer : LexerBase() {

    companion object {
        const val STATE_LINE_START = 0
        const val STATE_AFTER_KEY = 1
        const val STATE_IN_VALUE = 2
    }

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var state = STATE_LINE_START
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.state = initialState
        advance()
    }

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= bufferEnd) {
            tokenType = null
            return
        }
        tokenType = when (state) {
            STATE_LINE_START -> fromLineStart()
            STATE_AFTER_KEY -> fromAfterKey()
            STATE_IN_VALUE -> fromInValue()
            else -> badChar()
        }
    }

    private fun fromLineStart(): IElementType {
        val c = cur()
        return when {
            c == '\n' -> {
                tokenEnd = tokenStart + 1
                PlayConfigTokenTypes.NEWLINE
            }
            c == '#' || c == ';' -> {
                scanToEndOfLine()
                PlayConfigTokenTypes.COMMENT
            }
            c == ' ' || c == '\t' -> {
                scanWhitespace()
                PlayConfigTokenTypes.WHITESPACE
            }
            else -> {
                // Read key: %profile.logicalkey or logicalkey
                // Key chars: alphanumeric, ., -, _, %, and anything until = or whitespace or newline
                tokenEnd = tokenStart
                while (tokenEnd < bufferEnd) {
                    val ch = buffer[tokenEnd]
                    if (ch == '=' || ch == '\n') break
                    // stop at whitespace-before-= to let AFTER_KEY handle it
                    if ((ch == ' ' || ch == '\t') && hasEqualsAhead(tokenEnd)) break
                    tokenEnd++
                }
                if (tokenEnd == tokenStart) return badChar()
                state = STATE_AFTER_KEY
                PlayConfigTokenTypes.KEY
            }
        }
    }

    private fun hasEqualsAhead(from: Int): Boolean {
        var i = from
        while (i < bufferEnd && (buffer[i] == ' ' || buffer[i] == '\t')) i++
        return i < bufferEnd && buffer[i] == '='
    }

    private fun fromAfterKey(): IElementType {
        val c = cur()
        return when {
            c == '\n' -> newlineAndReset()
            c == ' ' || c == '\t' -> {
                scanWhitespace()
                PlayConfigTokenTypes.WHITESPACE
            }
            c == '=' -> {
                tokenEnd = tokenStart + 1
                state = STATE_IN_VALUE
                PlayConfigTokenTypes.SEPARATOR
            }
            else -> {
                // malformed line, skip to end
                scanToEndOfLine()
                state = STATE_LINE_START
                PlayConfigTokenTypes.BAD_CHARACTER
            }
        }
    }

    private fun fromInValue(): IElementType {
        val c = cur()
        return when {
            c == '\n' -> newlineAndReset()
            c == ' ' || c == '\t' -> {
                scanWhitespace()
                PlayConfigTokenTypes.WHITESPACE
            }
            c == '$' && tokenStart + 1 < bufferEnd && buffer[tokenStart + 1] == '{' -> {
                // ENV_PLACEHOLDER: ${VAR_NAME}
                tokenEnd = tokenStart + 2
                while (tokenEnd < bufferEnd && buffer[tokenEnd] != '}' && buffer[tokenEnd] != '\n') {
                    tokenEnd++
                }
                if (tokenEnd < bufferEnd && buffer[tokenEnd] == '}') tokenEnd++
                PlayConfigTokenTypes.ENV_PLACEHOLDER
            }
            else -> {
                // Scan value chars until newline or another ${
                tokenEnd = tokenStart
                while (tokenEnd < bufferEnd) {
                    val ch = buffer[tokenEnd]
                    if (ch == '\n') break
                    if (ch == '$' && tokenEnd + 1 < bufferEnd && buffer[tokenEnd + 1] == '{') break
                    tokenEnd++
                }
                if (tokenEnd == tokenStart) return badChar()
                PlayConfigTokenTypes.VALUE
            }
        }
    }

    private fun newlineAndReset(): IElementType {
        tokenEnd = tokenStart + 1
        state = STATE_LINE_START
        return PlayConfigTokenTypes.NEWLINE
    }

    private fun badChar(): IElementType {
        tokenEnd = tokenStart + 1
        state = STATE_LINE_START
        return PlayConfigTokenTypes.BAD_CHARACTER
    }

    private fun cur(): Char = buffer[tokenStart]

    private fun scanWhitespace() {
        tokenEnd = tokenStart
        while (tokenEnd < bufferEnd && (buffer[tokenEnd] == ' ' || buffer[tokenEnd] == '\t')) tokenEnd++
    }

    private fun scanToEndOfLine() {
        tokenEnd = tokenStart
        while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n') tokenEnd++
    }

    override fun getState(): Int = state
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = bufferEnd
}
