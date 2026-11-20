package com.piku.client.source.ao3.data.remote

internal object Ao3BookmarkHtml {

    private val CSRF_META = Regex("""<meta name="csrf-token" content="([^"]+)"""")

    private val INPUT_TAG = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)

    private val PSEUD_SELECT = Regex(
        """<select\b[^>]*name="bookmark\[pseud_id\]"[^>]*>(.*?)</select>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val OPTION_TAG = Regex("""<option\b[^>]*>""", RegexOption.IGNORE_CASE)

    private val EDIT_LINK = Regex("""href="/bookmarks/(\d+)/edit"""")

    private const val PSEUD_FIELD = """name="bookmark[pseud_id]""""

    fun csrfToken(html: String): String? =
        CSRF_META.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    fun ownBookmarkId(html: String): Long? =
        EDIT_LINK.find(html)?.groupValues?.get(1)?.toLongOrNull()

    fun pseudId(html: String): Long? {
        val hidden = INPUT_TAG.findAll(html)
            .firstOrNull { it.value.contains(PSEUD_FIELD) }
            ?.let { attr(it.value, "value")?.toLongOrNull() }
        if (hidden != null) return hidden

        val options = PSEUD_SELECT.find(html)
            ?.groupValues?.get(1)
            ?.let { OPTION_TAG.findAll(it).toList() }
            ?: return null
        return (options.firstOrNull { it.value.contains("selected") } ?: options.firstOrNull())
            ?.let { attr(it.value, "value")?.toLongOrNull() }
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name="([^"]*)"""").find(tag)?.groupValues?.get(1)
}
