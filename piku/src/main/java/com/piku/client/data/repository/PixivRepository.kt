package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.remote.apiCall
import com.piku.client.data.auth.PixivAuthEndpoints
import com.piku.client.data.auth.PixivAuthRuntime
import com.piku.client.domain.model.AppError
import com.piku.client.data.remote.pixiv.PixivAppActionResponse
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivApiConfig
import com.piku.client.data.remote.pixiv.PixivAppApi
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.remote.pixiv.PixivAppIllust
import com.piku.client.data.remote.pixiv.PixivIllustBody
import com.piku.client.data.remote.pixiv.PixivWorkCard
import com.piku.client.data.remote.pixiv.PixivPageUrls
import com.piku.client.data.remote.pixiv.PixivRankingItem
import com.piku.client.data.remote.pixiv.PixivUserPreview
import com.piku.client.data.remote.pixiv.PixivTrendTag
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.FollowUserPage
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.WorkStats
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceSuggestion
import com.piku.client.domain.source.SourceTrendingTag
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.source.SourceWorkText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivRepository @Inject constructor(
    private val api: PixivApi,
    // 登录后个性化数据在 app-api 域 令牌只对该域生效
    private val appApi: PixivAppApi,
    private val endpoints: PixivAuthEndpoints,
    private val runtime: PixivAuthRuntime,
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

    // 个性化推荐 按 offset 翻页 签名头须同一时间串算出 故在此算一对再传下
    suspend fun recommendedFeed(offset: Int): Result<List<Work>> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.recommended(
            clientTime = signature.time,
            clientHash = signature.hash,
            offset = offset,
        )
        response.illusts.mapNotNull { it.toWork() }
    }

    suspend fun searchWorks(
        word: String,
        searchTarget: String?,
        sort: String?,
        duration: String?,
        hideAi: Boolean,
        offset: Int,
    ): Result<SourcePage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.searchIllust(
            clientTime = signature.time,
            clientHash = signature.hash,
            word = word,
            searchTarget = searchTarget,
            sort = sort,
            duration = duration,
            searchAiType = if (hideAi) 0 else null,
            offset = offset,
        )
        SourcePage(items = response.illusts.mapNotNull { it.toWork() })
    }

    suspend fun searchUsers(word: String, offset: Int): Result<List<FollowUser>> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.searchUser(
            clientTime = signature.time,
            clientHash = signature.hash,
            word = word,
            offset = offset,
        )
        response.userPreviews.mapNotNull { it.toFollowUser() }
    }

    /**
     * 我的关注列表。user_id 是**自己**的数字 id（调用方从登录令牌里取）；
     * offset 翻页，total 接口不保证给（null = 未知，翻页以空页为准）。
     */
    suspend fun userFollowing(userId: Long, offset: Int): Result<FollowUserPage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.userFollowing(
            clientTime = signature.time,
            clientHash = signature.hash,
            userId = userId,
            offset = offset.takeIf { it > 0 },
        )
        FollowUserPage(
            users = response.userPreviews.mapNotNull { it.toFollowUser() },
            total = response.total,
        )
    }

    suspend fun suggest(word: String): Result<List<SourceSuggestion>> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.autocomplete(
            clientTime = signature.time,
            clientHash = signature.hash,
            word = word,
        )
        response.tags
            .filter { it.name.isNotBlank() }
            .map { SourceSuggestion(name = it.name, translatedName = it.translatedName) }
    }

    suspend fun trendingTags(): Result<List<SourceTrendingTag>> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.trendingTags(
            clientTime = signature.time,
            clientHash = signature.hash,
        )
        response.trendTags.mapNotNull { it.toTrendingTag() }
    }

    /** 关注流：已关注画师的新作，时间倒序，与推荐同为 offset 翻页。需登录，未登录时调用方不该发起 */
    suspend fun followFeed(offset: Int): Result<List<Work>> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.followFeed(
            clientTime = signature.time,
            clientHash = signature.hash,
            offset = offset,
        )
        response.illusts.mapNotNull { it.toWork() }
    }

    suspend fun newFeed(contentType: String, cursor: Long?): Result<SourcePage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.illustNew(
            clientTime = signature.time,
            clientHash = signature.hash,
            contentType = contentType,
            maxIllustId = cursor,
        )
        SourcePage(
            items = response.illusts.mapNotNull { it.toWork() },
            nextCursor = pixivNewFeedCursor(response.nextUrl)?.toString(),
        )
    }

    /**
     * 详情补充文本与统计。简介是 HTML 片段：<br /> 换算行、其余标签剥掉（详情壳按纯文本展示）。
     * 计数与元信息同一个接口就带出来了，不再多打一次请求。
     */
    suspend fun workText(illustId: Long): Result<SourceWorkText?> = apiCall {
        val response = api.illustDetail(illustId)
        if (response.error) return@apiCall null
        SourceWorkText(
            description = cleanPixivDescription(response.body.description),
            tags = response.body.tags.tags.map { it.tag }.filter { it.isNotBlank() },
            stats = response.body.toWorkStats(),
        )
    }

    /** 详情页底部的相关作品；取不到就当没有，详情页照常展示 */
    suspend fun recommend(illustId: Long): Result<List<Work>> = apiCall {
        val response = api.recommend(illustId)
        if (response.error) return@apiCall emptyList()
        response.body.illusts.mapNotNull { it.toWork() }
    }

    // ---------------- 关注与云端收藏（app-api；Bearer 由传输层按主机补） ----------------

    /** 登录用户视角的作品状态：是否已收藏（云端）、是否已关注作者。未登录时调用方不该发起 */
    suspend fun illustState(illustId: Long): Result<PixivIllustState> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.illustState(
            clientTime = signature.time,
            clientHash = signature.hash,
            illustId = illustId,
        )
        if (!response.illust.visible) throw AppError.NotFound
        PixivIllustState(
            isBookmarked = response.illust.isBookmarked,
            isFollowed = response.illust.user.isFollowed,
        )
    }

    /** 关注/取消关注作者。关注带可见性（默认公开），取关不需要 */
    suspend fun followUser(userId: Long, follow: Boolean): Result<Unit> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = if (follow) {
            appApi.followAdd(
                clientTime = signature.time,
                clientHash = signature.hash,
                userId = userId,
            )
        } else {
            appApi.followDelete(
                clientTime = signature.time,
                clientHash = signature.hash,
                userId = userId,
            )
        }
        requireNoActionError(response)
    }

    /** 加入/取消云端收藏。默认公开收藏；带标签、私密收藏的精细管理交给 P 站本家 */
    suspend fun bookmarkIllust(illustId: Long, add: Boolean): Result<Unit> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = if (add) {
            appApi.bookmarkAdd(
                clientTime = signature.time,
                clientHash = signature.hash,
                illustId = illustId,
            )
        } else {
            appApi.bookmarkDelete(
                clientTime = signature.time,
                clientHash = signature.hash,
                illustId = illustId,
            )
        }
        requireNoActionError(response)
    }

    /** 200 但带 error 块的拒绝（如校验不过）：不能当成成功吞掉 */
    private fun requireNoActionError(response: PixivAppActionResponse) {
        val error = response.error ?: return
        Log.d(
            TAG,
            "pixiv action rejected: reason=${error.reason} message=${error.message} " +
                "userMessage=${error.userMessage}",
        )
        throw AppError.Unknown
    }
}

/** 作品的登录态快照，供详情页回显按钮状态 */
data class PixivIllustState(
    val isBookmarked: Boolean,
    val isFollowed: Boolean,
)

private const val TAG = "PikuDiag"

/** 相关作品卡片只需要 id/标题/缩略图/作者/页数；id 解析不出来的是占位条目，直接丢掉 */
internal fun PixivWorkCard.toWork(): Work? {
    val illustId = illustId
    if (illustId <= 0 || url.isBlank()) return null
    return Work(
        id = illustId,
        authorId = authorIdLong,
        authorName = userName,
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = title,
        thumbnailUrl = url,
        imageCount = pageCount,
        r18 = xRestrict > 0,
        source = WorkSource.PIXIV,
    )
}

// 缩略图取 large medium/square_medium 是居中方裁 按比例卡片须用未裁的那档
internal fun PixivAppIllust.toWork(): Work? {
    val illustId = illustId
    val thumb = imageUrls.large.ifBlank { imageUrls.medium }.ifBlank { imageUrls.squareMedium }
    if (illustId <= 0 || thumb.isBlank()) return null
    return Work(
        id = illustId,
        authorId = user.userId,
        authorName = user.name,
        authorAvatarUrl = user.profileImageUrls.medium.ifBlank { null },
        categoryCd = -1,
        categoryName = "",
        title = title,
        thumbnailUrl = thumb,
        thumbWidth = width,
        thumbHeight = height,
        imageCount = pageCount,
        r18 = xRestrict > 0,
        source = WorkSource.PIXIV,
    )
}

internal fun PixivIllustBody.toWorkStats(): WorkStats = WorkStats(
    views = viewCount,
    likes = likeCount,
    bookmarks = bookmarkCount,
    postedAt = uploadDate.ifBlank { createDate },
    width = width,
    height = height,
    pageCount = pageCount,
    authorAccount = userAccount,
)

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

/** 从新着流的 next_url 里解析下一页游标 max_illust_id；末页无 next_url，解析不出也按 null（首页）处理 */
internal fun pixivNewFeedCursor(nextUrl: String?): Long? {
    if (nextUrl.isNullOrBlank()) return null
    return nextUrl.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == "max_illust_id" }
        ?.substringAfter('=')
        ?.toLongOrNull()
}

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

/** 用户搜索条目 → 用户行；id 解析不出的丢掉 */
internal fun PixivUserPreview.toFollowUser(): FollowUser? {
    val id = user.userId
    if (id <= 0L) return null
    return FollowUser(
        userId = id,
        name = user.name,
        avatarUrl = user.profileImageUrls.medium.ifBlank { null },
        followed = user.isFollowed,
    )
}

internal fun PixivTrendTag.toTrendingTag(): SourceTrendingTag? {
    val thumb = illust.imageUrls.large.ifBlank { illust.imageUrls.medium }.ifBlank { illust.imageUrls.squareMedium }
    if (tag.isBlank() || thumb.isBlank()) return null
    return SourceTrendingTag(
        name = tag,
        translatedName = translatedName,
        thumbnailUrl = thumb,
        width = illust.width,
        height = illust.height,
    )
}
