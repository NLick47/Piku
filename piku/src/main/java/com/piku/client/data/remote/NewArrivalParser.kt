package com.piku.client.data.remote

import com.piku.client.domain.model.Work
import java.net.URLDecoder

object NewArrivalParser {

    private val AUTHOR_ID = Regex("""<a class="IllustUser"[^>]*href="/(\d+)/"""")
    private val AVATAR = Regex("""IllustUserThumb" src="([^"]+)" alt="([^"]*)"""")
    private val AUTHOR_NAME = Regex("""IllustUserName">([^<]+)</h2>""")
    private val CATEGORY_CD = Regex("""CategoryInfo" href="[^"]*\?CD=(\d+)"""")
    private val CATEGORY_NAME = Regex("""Category C\d+">([^<]+)</span>""")
    private val WORK = Regex("""<a class="IllustInfo" href="/\d+/(\d+)\.html"><span class="IllustInfoDesc">(.*?)</span></a>""", RegexOption.DOT_MATCHES_ALL)
    private val THUMB = Regex("""IllustThumbImgPic" src="([^"]+)"""")
    private val IMAGE_COUNT = Regex("""far fa-images"></i>\s*(\d+)</span>""")

    /**
     * 作品块起始。主页/关注/搜索为 `<div class="IllustThumb">`，マイボックス（自己的作品列表）
     * 多带一个类名（`IllustThumb IllustThumbMyBoxPc`），故允许 IllustThumb 后跟其余类名；
     * `IllustThumbImg` / `IllustThumbList` 等不以引号收尾，不会被误切。
     */
    private val THUMB_BLOCK = Regex("""<div class="IllustThumb(?: [^"]*)?"\s*>""")

    /** 非公開标记：`<span class="IllustInfoCenter"><span class="Publish Private"></span></span>` */
    private val PRIVATE = Regex("""class="Publish Private"""")

    /**
     * 解析作品网格页。
     *
     * 后三个参数用于 マイボックス 列表：该列表整页只有页主一个作者，每个作品块内不带
     * `IllustUser` 作者区，故用调用方给的页主资料回填（块内自带作者时仍以块内为准）。
     * 默认值（0 / 空）表示不回填——缺作者信息的块按原行为丢弃。
     */
    fun parse(
        html: String,
        authorFallbackId: Long = 0L,
        authorFallbackName: String = "",
        authorFallbackAvatarUrl: String? = null,
    ): List<Work> =
        THUMB_BLOCK.split(html)
            .drop(1)
            .mapNotNull { block ->
                val authorId = AUTHOR_ID.find(block)?.groupValues?.get(1)?.toLongOrNull()
                    ?: authorFallbackId.takeIf { it > 0L }
                    ?: return@mapNotNull null
                val avatar = AVATAR.find(block)?.groupValues
                val authorName = AUTHOR_NAME.find(block)?.groupValues?.get(1) ?: authorFallbackName
                val categoryCd = CATEGORY_CD.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                val categoryName = CATEGORY_NAME.find(block)?.groupValues?.get(1) ?: ""
                val work = WORK.find(block)?.groupValues ?: return@mapNotNull null
                val workId = work[1].toLongOrNull() ?: return@mapNotNull null
                val thumbnail = THUMB.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val imageCount = IMAGE_COUNT.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                Work(
                    id = workId,
                    authorId = authorId,
                    authorName = authorName,
                    authorAvatarUrl = avatar?.get(1) ?: authorFallbackAvatarUrl,
                    categoryCd = categoryCd,
                    categoryName = categoryName,
                    title = cleanText(work[2]),
                    thumbnailUrl = thumbnail,
                    imageCount = imageCount,
                    r18 = thumbnail.contains("/img/R-18"),
                    warning = thumbnail.contains("/img/warning"),
                    loginRequired = thumbnail.contains("/img/publish_login"),
                    isPrivate = PRIVATE.containsMatchIn(block),
                )
            }

    internal fun cleanText(raw: String): String =
        raw.replace(Regex("""<br\s*/?>"""), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
}