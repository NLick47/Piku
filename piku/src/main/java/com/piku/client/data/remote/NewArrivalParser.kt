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

    fun parse(html: String): List<Work> =
        html.split("<div class=\"IllustThumb\">")
            .drop(1)
            .mapNotNull { block ->
                val authorId = AUTHOR_ID.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
                val avatar = AVATAR.find(block)?.groupValues
                val authorName = AUTHOR_NAME.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
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
                    authorAvatarUrl = avatar?.get(1),
                    categoryCd = categoryCd,
                    categoryName = categoryName,
                    title = cleanText(work[2]),
                    thumbnailUrl = thumbnail,
                    imageCount = imageCount,
                    r18 = thumbnail.contains("/img/R-18"),
                    warning = thumbnail.contains("/img/warning"),
                    loginRequired = thumbnail.contains("/img/publish_login"),
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