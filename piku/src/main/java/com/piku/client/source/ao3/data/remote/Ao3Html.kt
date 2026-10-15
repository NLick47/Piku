package com.piku.client.source.ao3.data.remote

internal object Ao3Html {

    private val BR = Regex("""<br\s*/?>""")
    private val PARAGRAPH_END = Regex("""</p>""")
    private val TAG = Regex("<[^>]*>")
    private val NUMERIC_DEC = Regex("""&#(\d+);""")
    private val NUMERIC_HEX = Regex("""&#x([0-9a-fA-F]+);""")

    /** 标签剥掉、段落换成换行、实体解开；正文与摘要都用它 */
    fun cleanText(raw: String): String =
        raw.replace(BR, "\n")
            .replace(PARAGRAPH_END, "\n\n")
            .replace(TAG, "")
            .let(::decodeEntities)
            .trim()

    fun decodeEntities(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&rsquo;", "\u2019")
            .replace("&lsquo;", "\u2018")
            .replace("&ldquo;", "\u201C")
            .replace("&rdquo;", "\u201D")
            // 数字引用最后解：AO3 正文里 `&#10;`（换行）与 `&#8217;`（弯引号）都是常见写法
            .replace(NUMERIC_HEX) { codePoint(it.groupValues[1], radix = 16) }
            .replace(NUMERIC_DEC) { codePoint(it.groupValues[1], radix = 10) }

    private fun codePoint(raw: String, radix: Int): String {
        val value = raw.toIntOrNull(radix) ?: return "&#$raw;"
        // 代理区与越界码点没有对应字符：原样留着比抛异常好（正文里出现也不能崩）
        if (value !in 1..0x10FFFF || value in 0xD800..0xDFFF) return "&#$raw;"
        return String(Character.toChars(value))
    }
}
