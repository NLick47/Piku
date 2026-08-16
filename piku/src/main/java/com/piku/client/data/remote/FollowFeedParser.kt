package com.piku.client.data.remote

import com.piku.client.domain.model.Work

/**
 * 关注时间线解析器（MyHomePcV.jsp）。
 *
 * 与网格页（NewArrivalPcV 等，IllustThumb 块）不同，关注页每个作品是一个
 * `<div class="IllustItem …" id="IllustItem_{workId}">` 时间线条目：
 * 作者信息在条目头部（IllustItemUser），配图为大图（IllustItemThumbImg）。
 *
 * 未关注任何创作者时页面只有 `#InfoMsg` 欢迎语，解析结果为空列表（由 UI 呈现空态）；
 * 未登录访问时服务端返回登录页，可用 [isLoginPage] 判定会话失效。
 */
object FollowFeedParser {

    /** 条目开标签；捕获 class 属性值用于 R18 标记检测（按位置切片时开标签本身不会被吞掉） */
    private val ITEM_TAG = Regex("""<div class="IllustItem([^"]*)"[^>]*id="IllustItem_\d+">""")

    /** 详情回调同时携带作者 ID 与作品 ID：showIllustDetail(authorId, workId, -1) */
    private val DETAIL_CALL = Regex("""showIllustDetail\((\d+), (\d+), -1\)""")

    /** 作者头像与主页链接：IllustItemUserThumb href="/{authorId}/" style="background-image:url('{avatar}')" */
    private val AUTHOR = Regex("""IllustItemUserThumb" href="/(\d+)/" style="background-image:url\('([^']*)'\)""")

    /** 作者昵称：<h2 class="IllustItemUserName"><a href="/{id}/">{name}</a></h2> */
    private val AUTHOR_NAME = Regex("""IllustItemUserName"><a href="/\d+/">([^<]+)</a></h2>""")

    /** 分类：<a class="Category C{cd}" href="/NewArrivalPcV.jsp?CD={cd}">{name}</a> */
    private val CATEGORY = Regex("""Category C\d+" href="/NewArrivalPcV\.jsp\?CD=(\d+)">([^<]+)</a>""")

    /** 作品说明（标题）：<h1 id="IllustItemDesc_{id}" class="IllustItemDesc">…</h1>（class 与 > 之间可能有空格） */
    private val DESC = Regex(
        """<h1 id="IllustItemDesc_\d+" class="IllustItemDesc"\s*>(.*?)</h1>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** 配图：<img class="IllustItemThumbImg" src="{url}"> */
    private val THUMB = Regex("""<img class="IllustItemThumbImg" src="([^"]+)"""")

    /** 追加图提示，语言无关取第一个 （+N / (+N：显示全部（+4 个图像）、すべて表示（+4枚の画像） */
    private val APPEND_COUNT = Regex("""[(（]\+(\d+)""")

    /** 登录页标记：登录表单 POST 到 /f/LoginUserF.jsp */
    private val LOGIN_FORM = Regex("""LoginUserF\.jsp""")

    fun parse(html: String): List<Work> {
        val tags = ITEM_TAG.findAll(html).toList()
        if (tags.isEmpty()) return emptyList()
        return tags.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = tags.getOrNull(index + 1)?.range?.first ?: html.length
            parseBlock(match.groupValues[1], html.substring(start, end))
        }
    }

    /** 未登录时服务端返回登录页（HTTP 200 + 登录表单），据此可判定会话失效 */
    fun isLoginPage(html: String): Boolean = LOGIN_FORM.containsMatchIn(html)

    private fun parseBlock(classAttr: String, block: String): Work? {
        val call = DETAIL_CALL.find(block)?.groupValues ?: return null
        val authorId = call[1].toLongOrNull() ?: return null
        val workId = call[2].toLongOrNull() ?: return null
        val author = AUTHOR.find(block)?.groupValues
        val authorName = AUTHOR_NAME.find(block)?.groupValues?.get(1) ?: return null
        val category = CATEGORY.find(block)?.groupValues
        val categoryCd = category?.get(1)?.toIntOrNull() ?: 0
        val categoryName = category?.get(2)?.trim().orEmpty()
        val desc = DESC.find(block)?.groupValues?.get(1) ?: return null
        val thumbnail = THUMB.find(block)?.groupValues?.get(1) ?: return null
        val appendCount = APPEND_COUNT.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val r18Class = classAttr.split(Regex("""\s+""")).any { it == "R18" }
        return Work(
            id = workId,
            authorId = authorId,
            authorName = authorName,
            authorAvatarUrl = author?.get(2)?.takeIf { it.isNotBlank() },
            categoryCd = categoryCd,
            categoryName = categoryName,
            title = NewArrivalParser.cleanText(desc),
            thumbnailUrl = thumbnail,
            imageCount = appendCount + 1,
            r18 = r18Class || thumbnail.contains("/img/R-18"),
            warning = thumbnail.contains("/img/warning"),
            loginRequired = thumbnail.contains("/img/publish_login"),
        )
    }
}
