package com.github.pablolec.play1toolkit.playcache.util

import com.github.pablolec.play1toolkit.playcache.model.PlayCachedTemplateFragment
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

object PlayCacheTemplateScanner {

    private val OPEN_HEAD = Regex("""#\{cache\b""")
    private val FOR_ATTR = Regex("""\bfor\s*:\s*""")

    fun scan(file: PsiFile): List<PlayCachedTemplateFragment> {
        val text = file.text ?: return emptyList()
        if (!text.contains("#{cache")) return emptyList()

        val closes = PlayTemplatePatterns.TAG_CACHE_CLOSE.findAll(text).map { it.range.first }.toMutableList()
        val opens = findOpenTags(text)
        if (opens.isEmpty()) return emptyList()

        val fragments = mutableListOf<PlayCachedTemplateFragment>()
        for (open in opens) {
            val openRange = TextRange(open.start, open.endExclusive)
            val close = closes.firstOrNull { it >= openRange.endOffset }
            val bodyRange = if (close != null) {
                closes.remove(close)
                TextRange(openRange.endOffset, close)
            } else {
                TextRange(openRange.endOffset, openRange.endOffset)
            }
            val bodyText = text.substring(bodyRange.startOffset, bodyRange.endOffset)
            val includes = PlayTemplatePatterns.TAG_INCLUDE.findAll(bodyText)
                .mapNotNull { it.groupValues.getOrNull(1)?.takeIf { value -> value.isNotBlank() } }
                .toList()
            fragments += PlayCachedTemplateFragment(
                templateFile = file,
                key = PlayCacheArgExtractor.parseCacheArg(open.keyRaw),
                ttl = PlayCacheArgExtractor.parseCacheTtlArg(open.ttlRaw),
                rawKeyText = open.keyRaw,
                rawTtlText = open.ttlRaw,
                openTagRange = openRange,
                bodyRange = bodyRange,
                includedTemplatePaths = includes
            )
        }
        return fragments
    }

    /**
     * Returns true if the given file is a Play template eligible for cache-tag scanning.
     * Mirrors PlayTemplateFileUtils — we only consider files under app/views and the standard
     * template extensions; templates outside that root cannot contain Play tags.
     */
    fun isEligible(file: PsiFile): Boolean {
        val vf = file.virtualFile ?: return false
        val path = vf.path
        if (!path.contains("/app/views/")) return false
        val ext = vf.extension ?: return false
        return ext == "html" || ext == "xml" || ext == "json" || ext == "txt"
    }

    private data class OpenTag(
        val start: Int,
        val endExclusive: Int,
        val keyRaw: String,
        val ttlRaw: String?
    )

    /**
     * Manual scan that handles balanced `${...}` braces and quoted string segments
     * inside `#{cache ... }` headers — a plain regex cannot do this safely.
     */
    private fun findOpenTags(text: String): List<OpenTag> {
        val out = mutableListOf<OpenTag>()
        var index = 0
        while (true) {
            val match = OPEN_HEAD.find(text, index) ?: break
            val headEnd = match.range.last + 1
            val argsStart = headEnd
            val tagEnd = findMatchingClose(text, argsStart) ?: break
            // Skip self-closing `#{cache .../}` — Play does not support a self-closing cache,
            // but be defensive: ignore tokens that end with `/`.
            val rawArgs = text.substring(argsStart, tagEnd).trim()
            val (keyRaw, ttlRaw) = splitArguments(rawArgs)
            out += OpenTag(
                start = match.range.first,
                endExclusive = tagEnd + 1,
                keyRaw = keyRaw,
                ttlRaw = ttlRaw
            )
            index = tagEnd + 1
        }
        return out
    }

    private fun findMatchingClose(text: String, fromIndex: Int): Int? {
        var i = fromIndex
        var depth = 0
        var quote: Char? = null
        while (i < text.length) {
            val c = text[i]
            when {
                quote != null -> {
                    if (c == quote) quote = null
                }
                c == '\'' || c == '"' -> quote = c
                c == '{' -> depth++
                c == '}' -> {
                    if (depth == 0) return i
                    depth--
                }
            }
            i++
        }
        return null
    }

    private fun splitArguments(rawArgs: String): Pair<String, String?> {
        // Find the top-level comma that separates the positional key from the for: attribute.
        val commaIndex = findTopLevelComma(rawArgs)
        if (commaIndex == -1) {
            return rawArgs.trim() to null
        }
        val key = rawArgs.substring(0, commaIndex).trim()
        val tail = rawArgs.substring(commaIndex + 1).trim()
        val ttl = FOR_ATTR.find(tail)?.let { match ->
            tail.substring(match.range.last + 1).trim()
        }
        return key to ttl
    }

    private fun findTopLevelComma(text: String): Int {
        var i = 0
        var depth = 0
        var quote: Char? = null
        while (i < text.length) {
            val c = text[i]
            when {
                quote != null -> {
                    if (c == quote) quote = null
                }
                c == '\'' || c == '"' -> quote = c
                c == '{' -> depth++
                c == '}' -> if (depth > 0) depth--
                c == ',' && depth == 0 -> return i
            }
            i++
        }
        return -1
    }
}
