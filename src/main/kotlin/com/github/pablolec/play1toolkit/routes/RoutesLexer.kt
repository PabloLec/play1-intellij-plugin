package com.github.pablolec.play1toolkit.routes

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Hand-written lexer for Play 1 conf/routes files.
 *
 * Line format:
 *   HTTP_METHOD  /path/{param}  Controller.action
 *   HTTP_METHOD  /path/         staticDir:public
 *   HTTP_METHOD  /path          module:crud
 *   # comment
 *   (blank line)
 *
 * State machine:
 *   LINE_START      → start of any line
 *   AFTER_METHOD    → after HTTP_METHOD token, expect whitespace then path
 *   IN_PATH         → reading URL path characters
 *   AFTER_PATH      → after path whitespace, reading action part
 *   IN_CONTROLLER   → after CONTROLLER_NAME, expect DOT
 *   IN_ACTION       → after DOT, reading action name
 */
class RoutesLexer : LexerBase() {

    companion object {
        const val STATE_LINE_START = 0
        const val STATE_AFTER_METHOD = 1
        const val STATE_IN_PATH = 2
        const val STATE_AFTER_PATH = 3
        const val STATE_IN_CONTROLLER = 4
        const val STATE_IN_ACTION = 5
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
            STATE_AFTER_METHOD -> fromAfterMethod()
            STATE_IN_PATH -> fromInPath()
            STATE_AFTER_PATH -> fromAfterPath()
            STATE_IN_CONTROLLER -> fromInController()
            STATE_IN_ACTION -> fromInAction()
            else -> badChar()
        }
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private fun fromLineStart(): IElementType {
        return when {
            cur() == '\n' -> {
                tokenEnd = tokenStart + 1
                // stay in LINE_START
                RoutesTokenTypes.NEWLINE
            }
            cur() == '#' -> {
                scanToEndOfLine()
                // stay in LINE_START
                RoutesTokenTypes.COMMENT
            }
            isWhitespace(cur()) -> {
                scanWhitespace()
                // stay in LINE_START (leading whitespace on a line)
                RoutesTokenTypes.WHITESPACE
            }
            else -> {
                // Read the HTTP method (GET, POST, *, …)
                scanNonWhitespaceNonNewline()
                state = STATE_AFTER_METHOD
                RoutesTokenTypes.HTTP_METHOD
            }
        }
    }

    private fun fromAfterMethod(): IElementType {
        return when {
            cur() == '\n' -> newlineAndReset()
            isWhitespace(cur()) -> {
                scanWhitespace()
                state = STATE_IN_PATH
                RoutesTokenTypes.WHITESPACE
            }
            else -> badChar()
        }
    }

    private fun fromInPath(): IElementType {
        return when {
            cur() == '\n' -> newlineAndReset()
            isWhitespace(cur()) -> {
                scanWhitespace()
                state = STATE_AFTER_PATH
                RoutesTokenTypes.WHITESPACE
            }
            cur() == '{' -> {
                // Scan entire {…} block as PATH_PARAM
                tokenEnd = tokenStart + 1
                while (tokenEnd < bufferEnd && buffer[tokenEnd] != '}' && buffer[tokenEnd] != '\n') {
                    tokenEnd++
                }
                if (tokenEnd < bufferEnd && buffer[tokenEnd] == '}') tokenEnd++
                RoutesTokenTypes.PATH_PARAM
            }
            else -> {
                // Scan regular path chars until whitespace, '{', or newline
                tokenEnd = tokenStart
                while (tokenEnd < bufferEnd &&
                    !isWhitespace(buffer[tokenEnd]) &&
                    buffer[tokenEnd] != '{' &&
                    buffer[tokenEnd] != '\n'
                ) {
                    tokenEnd++
                }
                RoutesTokenTypes.PATH
            }
        }
    }

    private fun fromAfterPath(): IElementType {
        if (cur() == '\n') return newlineAndReset()
        if (isWhitespace(cur())) {
            scanWhitespace()
            return RoutesTokenTypes.WHITESPACE
        }

        val rest = buffer.substring(tokenStart, bufferEnd)
        return when {
            rest.startsWith("staticDir:") || rest.startsWith("staticFile:") -> {
                scanToEndOfLine()
                state = STATE_LINE_START
                RoutesTokenTypes.STATIC_REF
            }
            rest.startsWith("module:") -> {
                scanToEndOfLine()
                state = STATE_LINE_START
                RoutesTokenTypes.MODULE_REF
            }
            else -> {
                // Scan the full "pkg.Class.action" token then split on the LAST dot.
                // e.g., "login.LoginCtl.doSomething" → CONTROLLER_NAME="login.LoginCtl"
                //        "Application.index"          → CONTROLLER_NAME="Application"
                var lineEnd = tokenStart
                while (lineEnd < bufferEnd && !isWhitespace(buffer[lineEnd]) && buffer[lineEnd] != '\n') {
                    lineEnd++
                }
                val actionStr = buffer.substring(tokenStart, lineEnd)
                val lastDot = actionStr.lastIndexOf('.')
                tokenEnd = if (lastDot >= 0) tokenStart + lastDot else lineEnd
                state = STATE_IN_CONTROLLER
                RoutesTokenTypes.CONTROLLER_NAME
            }
        }
    }

    private fun fromInController(): IElementType {
        return when {
            cur() == '.' -> {
                tokenEnd = tokenStart + 1
                state = STATE_IN_ACTION
                RoutesTokenTypes.DOT
            }
            cur() == '\n' -> newlineAndReset()
            else -> badChar()
        }
    }

    private fun fromInAction(): IElementType {
        if (cur() == '\n') return newlineAndReset()
        if (isWhitespace(cur())) {
            scanWhitespace()
            state = STATE_LINE_START
            return RoutesTokenTypes.WHITESPACE
        }
        // Scan action name until whitespace or newline
        tokenEnd = tokenStart
        while (tokenEnd < bufferEnd &&
            !isWhitespace(buffer[tokenEnd]) &&
            buffer[tokenEnd] != '\n'
        ) {
            tokenEnd++
        }
        state = STATE_LINE_START
        return RoutesTokenTypes.ACTION_NAME
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun newlineAndReset(): IElementType {
        tokenEnd = tokenStart + 1
        state = STATE_LINE_START
        return RoutesTokenTypes.NEWLINE
    }

    private fun badChar(): IElementType {
        tokenEnd = tokenStart + 1
        state = STATE_LINE_START
        return RoutesTokenTypes.BAD_CHARACTER
    }

    private fun cur(): Char = buffer[tokenStart]

    private fun isWhitespace(c: Char) = c == ' ' || c == '\t'

    private fun scanWhitespace() {
        tokenEnd = tokenStart
        while (tokenEnd < bufferEnd && isWhitespace(buffer[tokenEnd])) tokenEnd++
    }

    private fun scanNonWhitespaceNonNewline() {
        tokenEnd = tokenStart
        while (tokenEnd < bufferEnd && !isWhitespace(buffer[tokenEnd]) && buffer[tokenEnd] != '\n') {
            tokenEnd++
        }
    }

    private fun scanToEndOfLine() {
        tokenEnd = tokenStart
        while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n') tokenEnd++
    }

    // ── LexerBase contract ────────────────────────────────────────────────────

    override fun getState(): Int = state
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = bufferEnd
}
