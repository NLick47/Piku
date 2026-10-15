package com.piku.client.source.ao3.data.remote

/**
 * 标签搜索结果页解析。结果行：
 * `<li><span class='canonical'>Fandom: <a class="tag" href="/tags/…">Harry Potter - J. K. Rowling</a> &lrm;(1025170)</span></li>`
 *
 * 圈层值取 anchor 的显示名：href 里 `*d*`/`*s*`/`*a*` 是站点对 `.` `/` `&` 的转义，拼不回标签名。
 * 名字要解实体（`&amp;` 在标签名里很常见，整段剥标签会把它留成字面量）；括号里是作品数。
 */
object Ao3TagSearchParser {

    /** 一条候选：标签显示名 + 作品数（站点偶尔不给，为 null） */
    data class Suggestion(val name: String, val count: Int? = null)

    private val RESULT_LIST = Regex("""<ol[^>]*class="[^"]*tag index group[^"]*"[^>]*>(.*?)</ol>""", RegexOption.DOT_MATCHES_ALL)

    private val ROW = Regex("""<li[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)

    private val TAG_LINK = Regex("""<a[^>]*class="tag"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    /** 作品数紧跟链接：`…</a> &lrm;(1025170)`；`&lrm;` 是读屏用的方向标记 */
    private val TAG_COUNT = Regex("""</a>[^<]*?\(([\d,]+)\)""")

    fun parse(html: String): List<Suggestion> {
        val list = RESULT_LIST.find(html)?.groupValues?.get(1) ?: return emptyList()
        return ROW.findAll(list)
            .mapNotNull { row ->
                val block = row.groupValues[1]
                val name = TAG_LINK.find(block)?.groupValues?.get(1)
                    ?.let(Ao3Html::decodeEntities)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val count = TAG_COUNT.find(block)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
                Suggestion(name, count)
            }
            // 同名去重：选择器按名字做 key，重复会让 LazyColumn 直接崩
            .distinctBy { it.name }
            .toList()
    }
}
