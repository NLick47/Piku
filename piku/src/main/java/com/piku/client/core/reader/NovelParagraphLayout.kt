package com.piku.client.core.reader

internal object NovelParagraphLayout {

    internal const val INDENT_CHARS = 2

    private const val ALREADY_INDENTED_RATIO = 0.3f
    private const val BARE_BOUNDARY_RATIO = 0.5f
    private const val VERSE_LINE_RATIO = 0.5f
    private const val VERSE_MAX_LINE_CHARS = 12
    private const val INDENT_LEADING = "\u3000 \t"
    private const val SENTENCE_END = "。！？…‥‼⁉」』）〕”’♪☆★、,?!."

    fun shouldIndent(text: String): Boolean {
        val lines = text.replace("\u200b", "").split('\n')
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.size < 2) return false
        if (alreadyIndented(nonBlank)) return false
        if (!mostlyBareBoundaries(lines)) return false
        if (looksLikeVerse(nonBlank)) return false
        return true
    }

    fun withParagraphGap(text: String): String {
        val chars = text.toCharArray()
        val out = StringBuilder(chars.size + chars.size / 8)
        for (i in chars.indices) {
            out.append(chars[i])
            if (chars[i] == '\n') {
                val prevBlank = i == 0 || chars[i - 1] == '\n'
                val nextBlank = i == chars.lastIndex || chars[i + 1] == '\n'
                if (!prevBlank && !nextBlank) out.append('\n')
            }
        }
        return out.toString()
    }

    private fun alreadyIndented(nonBlank: List<String>): Boolean {
        val indented = nonBlank.count { it.first() in INDENT_LEADING }
        return indented >= nonBlank.size * ALREADY_INDENTED_RATIO
    }

    private fun mostlyBareBoundaries(lines: List<String>): Boolean {
        val indexes = lines.indices.filter { lines[it].isNotBlank() }
        if (indexes.size < 2) return false
        val boundaries = indexes.size - 1
        val bare = indexes.zipWithNext().count { (a, b) -> b == a + 1 }
        return bare >= boundaries * BARE_BOUNDARY_RATIO
    }

    private fun looksLikeVerse(nonBlank: List<String>): Boolean {
        val verseLines = nonBlank.count { line ->
            line.length <= VERSE_MAX_LINE_CHARS && line.last() !in SENTENCE_END
        }
        return verseLines >= nonBlank.size * VERSE_LINE_RATIO
    }
}
