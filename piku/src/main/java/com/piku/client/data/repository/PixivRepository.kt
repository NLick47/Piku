package com.piku.client.data.repository

import com.piku.client.data.remote.apiCall
import com.piku.client.domain.model.AppError
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivApiConfig
import com.piku.client.data.remote.pixiv.PixivPageUrls
import com.piku.client.data.remote.pixiv.PixivRankingItem
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.source.SourceWorkText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivRepository @Inject constructor(
    private val api: PixivApi,
) {

    /**
     * 看图页列表，走网页端详情取页接口（每页 URL 由 pixiv 给，不再从缩略图猜）。
     * regular 是 master1200 看图档；original 扩展名不定，仅作缺 regular 时的兜底。
     */
    suspend fun workPages(illustId: Long): Result<List<SourceWorkPage>> = apiCall {
        val response = api.illustPages(illustId)
        if (response.error) {
            // R-18 等登录墙：HTTP 200 但 error=true，重试无意义
            throw AppError.NotFound
        }
        response.body
            .map { page -> page.urls.toSourceWorkPage(page.width, page.height) }
            .filter { it.url.isNotBlank() }
    }

    suspend fun ranking(mode: String, content: String, page: Int): Result<SourcePage> =
        apiCall {
            val response = api.ranking(mode = mode, content = content, page = page)
            SourcePage(
                items = response.contents.map { it.toWork() },
                totalPages = pixivTotalPages(response.rankTotal),
            )
        }

    /** 详情补充文本。简介是 HTML 片段：<br /> 换算行、其余标签剥掉（详情壳按纯文本展示）。 */
    suspend fun workText(illustId: Long): Result<SourceWorkText?> = apiCall {
        val response = api.illustDetail(illustId)
        if (response.error) return@apiCall null
        SourceWorkText(
            description = cleanPixivDescription(response.body.description),
            tags = response.body.tags.tags.map { it.tag }.filter { it.isNotBlank() },
        )
    }
}

/**
 * 尺寸档映射：**打底用 small（540px，几十 KB）**——一开详情页就拉 master1200 是 1 MB 起步，
 * 这条线上直连还得几十秒；**清晰档用 regular（1200px）**，查看器覆盖、图片翻译、分享都用它；
 * **原图只在保存时取**。任何一档缺失都往下一档退，保证至少有一张能显示。
 */
internal fun PixivPageUrls.toSourceWorkPage(width: Int, height: Int): SourceWorkPage = SourceWorkPage(
    url = small.ifBlank { regular }.ifBlank { original },
    fullUrl = regular.ifBlank { original },
    originalUrl = original,
    width = width,
    height = height,
)

internal fun cleanPixivDescription(raw: String): String = raw
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .let(::decodePixivEntities)
    .trim()

private val PIXIV_NAMED_ENTITY = Regex("&(amp|lt|gt|quot|#39|apos|nbsp);")
private val PIXIV_NUMERIC_ENTITY = Regex("&#(\\d+);")

/** 简介里的实体按网页同款语义解码；单次扫描保证不二次解码（"&amp;lt;" 解成字面 "&lt;"） */
private fun decodePixivEntities(text: String): String = text
    .replace(PIXIV_NUMERIC_ENTITY) { m ->
        m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
    }
    .replace(PIXIV_NAMED_ENTITY) { m ->
        when (m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "#39", "apos" -> "'"
            "nbsp" -> " "
            else -> m.value
        }
    }

/** rank_total 换算总页数；0/负数表示未知（匿名接口偶尔返回 0），交由引擎翻到空页为止 */
internal fun pixivTotalPages(rankTotal: Int): Int? =
    if (rankTotal <= 0) null else (rankTotal + PixivApiConfig.PAGE_SIZE - 1) / PixivApiConfig.PAGE_SIZE

internal fun PixivRankingItem.toWork(): Work = Work(
    id = illustId,
    authorId = userId,
    authorName = userName,
    authorAvatarUrl = profileImg,
    // pixiv 没有 poipiku 的分类体系，按既有约定留空：WorkCard 在 categoryName 为空时不渲染该栏
    categoryCd = -1,
    categoryName = "",
    title = title,
    thumbnailUrl = url,
    imageCount = pageCount,
    r18 = contentType.sexual > 0,
    source = WorkSource.PIXIV,
)
