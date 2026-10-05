package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.remote.apiCall
import com.piku.client.data.auth.PixivAuthEndpoints
import com.piku.client.data.auth.PixivAuthRepository
import com.piku.client.data.auth.PixivAuthRuntime
import com.piku.client.data.remote.ImageRouteController
import com.piku.client.data.remote.ImageUpstream
import com.piku.client.data.local.QuietFollowStore
import com.piku.client.data.source.PixivSearchSource
import com.piku.client.domain.model.AppError
import com.piku.client.data.remote.pixiv.PixivAppActionResponse
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivApiConfig
import com.piku.client.data.remote.pixiv.PixivAppApi
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.remote.pixiv.PixivAppIllust
import com.piku.client.data.remote.pixiv.PixivAppIllustFull
import com.piku.client.data.remote.pixiv.PixivIllustBody
import com.piku.client.data.remote.pixiv.PixivWorkCard
import com.piku.client.data.remote.pixiv.PixivPageUrls
import com.piku.client.data.remote.pixiv.PixivRankingItem
import com.piku.client.data.remote.pixiv.PixivSearchItem
import com.piku.client.data.remote.pixiv.PixivUserPreview
import com.piku.client.data.remote.pixiv.PixivUserDetailResponse
import com.piku.client.data.remote.pixiv.PixivTrendTag
import com.piku.client.domain.model.AuthorProfile
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.FollowUserPage
import com.piku.client.data.remote.PikuJson
import com.piku.client.data.remote.pixiv.PixivNovel
import com.piku.client.data.remote.pixiv.PixivWebviewNovel
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.WorkStats
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceSuggestion
import com.piku.client.domain.source.SourceTrendingTag
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.source.SourceWorkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivRepository @Inject constructor(
    private val api: PixivApi,
    // 登录后个性化数据在 app-api 域 令牌只对该域生效
    private val appApi: PixivAppApi,
    private val endpoints: PixivAuthEndpoints,
    private val runtime: PixivAuthRuntime,
    // 会话判定决定详情走哪条链路
    private val pixivAuth: PixivAuthRepository,
    // 卡片取哪档缩略图由图片线路实测速率决定（速度优先，够快才升未裁切档）
    private val imageRoute: ImageRouteController,
    private val quietFollowStore: QuietFollowStore,
) {

    /**
     * 看图页列表。有会话走 app-api 全量详情（登录限定作品网页端 404，只有这条路拿得到）；
     * 未登录走网页端详情取页接口（每页 URL 由 pixiv 给，不再从缩略图猜）。
     * regular 是 master1200 看图档；original 扩展名不定，仅作缺 regular 时的兜底。
     */
    suspend fun workPages(illustId: Long): Result<List<SourceWorkPage>> = apiCall {
        if (pixivAuth.hasSession()) {
            appIllustDetail(illustId).toSourceWorkPages().filter { it.url.isNotBlank() }
        } else {
            webIllustPages(illustId)
        }
    }

    private suspend fun webIllustPages(illustId: Long): List<SourceWorkPage> {
        val response = try {
            api.illustPages(illustId)
        } catch (_: HttpException) {
            // 404 与「不存在/已删除」同形，借详情接口分清
            throw classifyWebWall(illustId)
        }
        if (response.error) {
            // R-18 等登录墙：HTTP 200 但 error=true，重试无意义
            throw classifyWebWall(illustId)
        }
        return response.body
            .map { page -> page.urls.toSourceWorkPage(page.width, page.height) }
            .filter { it.url.isNotBlank() }
    }

    /**
     * 网页端分页被墙时判明性质：详情接口还认得这件作品 = 仅登录可见（引导登录），
     * 详情同样拿不到 = 真不存在或已删除。探测本身失败时按不存在处理，与旧表现一致。
     */
    private suspend fun classifyWebWall(illustId: Long): AppError =
        webWallError(runCatching { api.illustDetail(illustId) }.map { !it.error }.getOrDefault(false))

    /** app-api 全量详情：会话失效给引导登录，受限作品与 404 给终态 */
    private suspend fun appIllustDetail(illustId: Long): PixivAppIllustFull {
        val signature = endpoints.clientSignature(runtime.now())
        val response = try {
            appApi.illustDetail(
                clientTime = signature.time,
                clientHash = signature.hash,
                illustId = illustId,
            )
        } catch (e: HttpException) {
            throw when (e.code()) {
                401 -> AppError.LoginRequired
                404 -> AppError.NotFound
                else -> e
            }
        }
        if (response.error != null || !response.illust.visible) throw AppError.NotFound
        return response.illust
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
    suspend fun recommendedFeed(
        offset: Int,
        contentType: String = PixivAppConfig.TYPE_ILLUST,
    ): Result<List<Work>> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.recommended(
            clientTime = signature.time,
            clientHash = signature.hash,
            contentType = contentType,
            offset = offset,
        )
        response.illusts.mapNotNull { it.toWork() }
    }

    /**
     * 搜作品。有会话走 app-api（登录限定/R-18 只有这条路拿得到，参数全量生效）；
     * 未登录或会话失效（自动重登恢复前的窗口期）回落网页端匿名搜索——详情页登录墙
     * 同款分链路，别让未登录的搜索卡在错误墙上。
     *
     * 缩略图档位按图片线路实测速率定：速度优先取方裁小图（与主页同速），
     * 线路够快（预算内落得了地）才升未裁切档。详情页看图器的升档判断同一套。
     */
    suspend fun searchWorks(
        word: String,
        searchTarget: String?,
        sort: String?,
        duration: String?,
        hideAi: Boolean,
        page: Int,
    ): Result<SourcePage> {
        val hdThumb = imageRoute.worthFullImageInline(ImageUpstream.PIXIV)
        if (pixivAuth.hasSession()) {
            val appResult = apiCall {
                val signature = endpoints.clientSignature(runtime.now())
                val response = try {
                    appApi.searchIllust(
                        clientTime = signature.time,
                        clientHash = signature.hash,
                        word = word,
                        searchTarget = searchTarget,
                        sort = sort,
                        duration = duration,
                        searchAiType = if (hideAi) 0 else null,
                        offset = page * PixivAppConfig.PAGE_SIZE,
                    )
                } catch (e: HttpException) {
                    throw when (e.code()) {
                        401 -> AppError.LoginRequired
                        else -> e
                    }
                }
                SourcePage(items = response.illusts.mapNotNull { it.toWork(hdThumb) })
            }
            val error = appResult.exceptionOrNull()
            if (error == null || error !is AppError.LoginRequired) return appResult
        }
        return webSearchWorks(word, searchTarget, sort, page, hdThumb)
    }

    /**
     * 网页端匿名搜索。期间与 AI 过滤不支持（匿名请求被服务端忽略），人気順也不生效，
     * 一律映射回新着；target/sort 由 app-api 枚举翻译成网页端参数，插件契约不变。
     * 匿名翻页钳在 lastPage，越界请求返回的数据与末页相同，这里直接判空让 UI 落到尽头。
     */
    private suspend fun webSearchWorks(
        word: String,
        searchTarget: String?,
        sort: String?,
        page: Int,
        hdThumb: Boolean,
    ): Result<SourcePage> = apiCall {
        val response = api.searchArtworks(
            word = word,
            page = page + 1,
            sMode = when (searchTarget) {
                PixivSearchSource.TARGET_EXACT -> WEB_S_MODE_TAG_EXACT
                PixivSearchSource.TARGET_TITLE -> WEB_S_MODE_TC
                else -> WEB_S_MODE_TAG
            },
            order = if (sort == PixivSearchSource.SORT_OLD) WEB_ORDER_OLD else WEB_ORDER_NEW,
        )
        // 网页端信封 error=true 时 body 为空壳：不查标志会把失败静默渲染成"无结果"
        if (response.error) throw AppError.Unknown
        val result = response.body.illustManga
        val inRange = result.lastPage <= 0 || page + 1 <= result.lastPage
        SourcePage(
            items = if (inRange) result.data.mapNotNull { it.toWork(hdThumb) } else emptyList(),
            totalPages = result.lastPage.takeIf { it > 0 },
        )
    }

    /** 搜小说：与搜作品同构，参数只有排序与 AI 过滤（对象/期间组外壳在小说档不展示） */
    suspend fun searchNovels(
        word: String,
        sort: String?,
        hideAi: Boolean,
        offset: Int,
    ): Result<SourcePage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        // 小说检索没有网页端匿名链路，401 归类成 LoginRequired 让外壳弹登录引导
        val response = try {
            appApi.searchNovel(
                clientTime = signature.time,
                clientHash = signature.hash,
                word = word,
                sort = sort,
                searchAiType = if (hideAi) 0 else null,
                offset = offset,
            )
        } catch (e: HttpException) {
            throw when (e.code()) {
                401 -> AppError.LoginRequired
                else -> e
            }
        }
        SourcePage(items = response.novels.mapNotNull { it.toWork() })
    }

    suspend fun searchUsers(word: String, offset: Int): Result<List<FollowUser>> = apiCall {        val signature = endpoints.clientSignature(runtime.now())
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
    suspend fun userFollowing(
        userId: Long,
        offset: Int,
        restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
    ): Result<FollowUserPage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.userFollowing(
            clientTime = signature.time,
            clientHash = signature.hash,
            userId = userId,
            offset = offset.takeIf { it > 0 },
            restrict = restrict,
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
     * 详情补充文本与统计。有会话走 app-api（登录限定/R-18 作品网页端匿名拿不到文本；
     * app-api 不带点赞数，likes 置空让 UI 不渲染该格）。未登录走网页端：
     * 简介是 HTML 片段：<br /> 换算行、其余标签剥掉（详情壳按纯文本展示）。
     * 计数与元信息同一个接口就带出来了，不再多打一次请求。
     */
    suspend fun workText(illustId: Long): Result<SourceWorkText?> = apiCall {
        if (pixivAuth.hasSession()) {
            val illust = appIllustDetail(illustId)
            SourceWorkText(
                description = cleanPixivDescription(illust.caption),
                tags = illust.tags.map { it.tag }.filter { it.isNotBlank() },
                stats = WorkStats(
                    views = illust.totalView,
                    likes = null,
                    bookmarks = illust.totalBookmarks,
                    postedAt = illust.createDate,
                    width = illust.width,
                    height = illust.height,
                    pageCount = illust.pageCount,
                    authorAccount = illust.user.account,
                ),
            )
        } else {
            val response = api.illustDetail(illustId)
            if (response.error) return@apiCall null
            SourceWorkText(
                description = cleanPixivDescription(response.body.description),
                tags = response.body.tags.tags.map { it.tag }.filter { it.isNotBlank() },
                stats = response.body.toWorkStats(),
            )
        }
    }

    /** 详情页底部的相关作品；取不到就当没有，详情页照常展示 */
    suspend fun recommend(illustId: Long): Result<List<Work>> = apiCall {
        val response = api.recommend(illustId)
        if (response.error) return@apiCall emptyList()
        response.body.illusts.mapNotNull { it.toWork() }
    }

    suspend fun novelRecommended(offset: Int): Result<List<Work>> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.novelRecommended(
            clientTime = signature.time,
            clientHash = signature.hash,
            offset = offset,
        )
        response.novels.mapNotNull { it.toWork() }
    }

    suspend fun novelFollowFeed(offset: Int): Result<List<Work>> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.novelFollow(
            clientTime = signature.time,
            clientHash = signature.hash,
            offset = offset,
        )
        response.novels.mapNotNull { it.toWork() }
    }

    suspend fun novelNew(cursor: Long?): Result<SourcePage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.novelNew(
            clientTime = signature.time,
            clientHash = signature.hash,
            maxNovelId = cursor,
        )
        SourcePage(
            items = response.novels.mapNotNull { it.toWork() },
            nextCursor = pixivNovelCursor(response.nextUrl)?.toString(),
        )
    }

    suspend fun novelText(novelId: Long): Result<SourceWorkText?> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.novelDetail(
            clientTime = signature.time,
            clientHash = signature.hash,
            novelId = novelId,
        )
        val novel = response.novel
        if (!novel.visible) return@apiCall null
        SourceWorkText(
            description = cleanPixivDescription(novel.caption),
            tags = novel.tags.map { it.name }.filter { it.isNotBlank() },
            stats = novel.toWorkStats(),
        )
    }

    /**
     * 小说正文。官方的 /v1/novel/text 已下线（2026-10 真机实测 404），只有 webview 这条：
     * 回的是 HTML，正文藏在页面里 `novel: {...}` 这个对象里，取出来再按 JSON 解析。
     */
    suspend fun novelBody(novelId: Long): Result<String> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val html = withContext(Dispatchers.IO) {
            appApi.novelWebview(
                clientTime = signature.time,
                clientHash = signature.hash,
                novelId = novelId,
            ).string()
        }
        val json = WEBVIEW_NOVEL_JSON.find(html)?.groupValues[1] ?: throw AppError.NotFound
        cleanPixivNovelText(PikuJson.decodeFromString<PixivWebviewNovel>(json).text)
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

    /**
     * 关注/取消关注作者。关注带可见性（默认公开），取关不需要；
     * restrict=private 即悄悄关注，悄悄/公开互转直接换 restrict 重发 add，不用先取关。
     */
    suspend fun followUser(
        userId: Long,
        follow: Boolean,
        restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
    ): Result<Unit> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = if (follow) {
            appApi.followAdd(
                clientTime = signature.time,
                clientHash = signature.hash,
                userId = userId,
                restrict = restrict,
            )
        } else {
            appApi.followDelete(
                clientTime = signature.time,
                clientHash = signature.hash,
                userId = userId,
            )
        }
        requireNoActionError(response)
    }.onSuccess {
        // 悄悄关注态只有本 App 自己记账（接口分不出公开/私密）：动作成功即落账，
        // 关注列表页拉到私密/公开两路名单时再校准网页端那边的转档
        when {
            !follow -> quietFollowStore.unmark(userId)
            restrict == PixivAppConfig.RESTRICT_PRIVATE -> quietFollowStore.mark(userId)
            else -> quietFollowStore.unmark(userId)
        }
    }

    /**
     * 加入/取消云端收藏。[restrict] 传 private 即非公开收藏；
     * 带标签的精细管理交给 pixiv 本家。取消收藏不受 restrict 影响。
     */
    suspend fun bookmarkIllust(
        illustId: Long,
        add: Boolean,
        restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
    ): Result<Unit> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = if (add) {
            appApi.bookmarkAdd(
                clientTime = signature.time,
                clientHash = signature.hash,
                illustId = illustId,
                restrict = restrict,
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

    // ---------------- 画师主页（app-api；都要登录） ----------------

    suspend fun authorProfile(userId: Long): Result<AuthorProfile> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.userDetail(
            clientTime = signature.time,
            clientHash = signature.hash,
            userId = userId,
        )
        response.toAuthorProfile(userId)
    }

    /** 画师作品：type 传 illust / manga，两池各自 offset 翻页 */
    suspend fun authorIllusts(userId: Long, type: String, offset: Int): Result<SourcePage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.userIllusts(
            clientTime = signature.time,
            clientHash = signature.hash,
            userId = userId,
            type = type,
            offset = offset.takeIf { it > 0 },
        )
        SourcePage(
            items = response.illusts.mapNotNull { it.toWork() },
            // 令牌只表「还有下一页」；到底与否以接口的 next_url 为准——
            // 条目会被 mapNotNull 丢掉（无图/无 id 的占位），按数量猜会提前判定到底
            nextCursor = response.nextUrl?.takeIf { it.isNotBlank() },
        )
    }

    /** 画师的小说：offset 翻页，下一页看 next_url，与 authorIllusts 同型 */
    suspend fun authorNovels(userId: Long, offset: Int): Result<SourcePage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.userNovels(
            clientTime = signature.time,
            clientHash = signature.hash,
            userId = userId,
            offset = offset.takeIf { it > 0 },
        )
        SourcePage(
            items = response.novels.mapNotNull { it.toWork() },
            nextCursor = response.nextUrl?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * 画师的收藏；翻页靠响应里的 max_bookmark_id 游标，不是 offset。
     * [restrict] 传 private 才能看非公开池，但只有 user_id 是登录者本人时服务端才认——
     * 看别人的主页恒走 public。
     */
    suspend fun authorBookmarks(
        userId: Long,
        cursor: Long?,
        restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
    ): Result<SourcePage> = apiCall {
        val signature = endpoints.clientSignature(runtime.now())
        val response = appApi.userBookmarks(
            clientTime = signature.time,
            clientHash = signature.hash,
            userId = userId,
            restrict = restrict,
            maxBookmarkId = cursor,
        )
        SourcePage(
            items = response.illusts.mapNotNull { it.toWork() },
            nextCursor = pixivBookmarkCursor(response.nextUrl)?.toString(),
        )
    }
}

/** 作品的登录态快照，供详情页回显按钮状态 */
data class PixivIllustState(
    val isBookmarked: Boolean,
    val isFollowed: Boolean,
)

private const val TAG = "PikuDiag"

private const val WEB_S_MODE_TAG = "s_tag"
private const val WEB_S_MODE_TAG_EXACT = "s_tag_exact"
private const val WEB_S_MODE_TC = "s_tc"
private const val WEB_ORDER_NEW = "date_d"
private const val WEB_ORDER_OLD = "date_asc"

private val WEB_CROPPED_THUMB_SUFFIX = Regex("_(square|custom)1200\\.jpg$")

private val WEB_SQUARE_THUMB_SUFFIX = Regex("_square1200\\.jpg$")

/**
 * 速度档缩略图：网页接口给的是 250 方裁小图。img-master 路径改写成详情首图同款
 * `c/540x540_70/` + master1200 文件（实测 200）——更清晰一档，且与详情首图同名同文件，
 * 详情页低清垫底（fileKey 比对）与共享转场得以命中；custom-thumb 路径实测不吃 540 前缀
 * （404），保持接口原样。HD 档的升级见 [pixivProportionalThumb]。
 */
internal fun pixivSpeedTierThumb(url: String): String {
    if (!url.startsWith("https://i.pximg.net/c/")) return url
    if (!url.contains("/img-master/") || !WEB_SQUARE_THUMB_SUFFIX.containsMatchIn(url)) return url
    val path = url.removePrefix("https://i.pximg.net/c/").substringAfter('/')
    return "https://i.pximg.net/c/540x540_70/" + WEB_SQUARE_THUMB_SUFFIX.replace(path, "_master1200.jpg")
}

internal fun pixivProportionalThumb(url: String): String {
    if (!url.startsWith("https://i.pximg.net/c/")) return url
    if (!WEB_CROPPED_THUMB_SUFFIX.containsMatchIn(url)) return url
    val path = url.removePrefix("https://i.pximg.net/c/").substringAfter('/')
    return "https://i.pximg.net/" + WEB_CROPPED_THUMB_SUFFIX.replace(path, "_master1200.jpg")
}

internal fun PixivSearchItem.toWork(hdThumb: Boolean): Work? {
    val illustId = id.toLongOrNull() ?: return null
    if (illustId <= 0 || url.isBlank()) return null
    return Work(
        id = illustId,
        authorId = userId.toLongOrNull() ?: 0,
        authorName = userName,
        authorAvatarUrl = profileImageUrl.ifBlank { null },
        categoryCd = -1,
        categoryName = "",
        title = title,
        // 速度优先按线路取小图（见 searchWorks 的档位决策），够快才换未裁切 master1200
        thumbnailUrl = if (hdThumb) pixivProportionalThumb(url) else pixivSpeedTierThumb(url),
        thumbWidth = width,
        thumbHeight = height,
        imageCount = pageCount,
        r18 = xRestrict > 0,
        ai = aiType == 2,
        source = WorkSource.PIXIV,
    )
}

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

internal fun PixivAppIllust.toWork(hdThumb: Boolean = true): Work? {
    val illustId = illustId
    val thumb = if (hdThumb) {
        imageUrls.large.ifBlank { imageUrls.medium }.ifBlank { imageUrls.squareMedium }
    } else {
        imageUrls.medium.ifBlank { imageUrls.squareMedium }.ifBlank { imageUrls.large }
    }
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
        ai = illustAiType == 2,
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

/** 网页端被墙后的定性：详情接口认得作品 = 仅登录可见，否则按不存在 */
internal fun webWallError(detailVisible: Boolean): AppError =
    if (detailVisible) AppError.LoginRequired else AppError.NotFound

/**
 * app-api 分页映射：medium 540px 打底、large 1200px 清晰档，原图 meta 单给。
 * 多页在 meta_pages 逐页给；单页原图在 meta_single_page（此时 meta_pages 为空）。
 * app-api 不带逐页尺寸，只有作品本体尺寸（= 首页），其余页留 0 由 UI 按图自适应。
 */
internal fun PixivAppIllustFull.toSourceWorkPages(): List<SourceWorkPage> = when {
    metaPages.isNotEmpty() -> metaPages.mapIndexed { index, page ->
        SourceWorkPage(
            url = page.imageUrls.medium.ifBlank { page.imageUrls.large },
            fullUrl = page.imageUrls.large.ifBlank { page.originalImageUrl },
            originalUrl = page.originalImageUrl,
            width = if (index == 0) width else 0,
            height = if (index == 0) height else 0,
        )
    }
    else -> listOf(
        SourceWorkPage(
            url = imageUrls.medium.ifBlank { imageUrls.large },
            fullUrl = imageUrls.large.ifBlank { metaSinglePage.originalImageUrl },
            originalUrl = metaSinglePage.originalImageUrl,
            width = width,
            height = height,
        )
    )
}

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

/**
 * 画师主页资料。id 以接口回传的为准；回不来用请求参数兜底（名字/头像是页面必有项，
 * 但两者都可能为空，界面自己决定退到什么）。
 */
internal fun PixivUserDetailResponse.toAuthorProfile(requestedId: Long): AuthorProfile = AuthorProfile(
    userId = user.userId.takeIf { it > 0 } ?: requestedId,
    name = user.name,
    account = user.account,
    // 没设头像时 pixiv 回 /common/images/no_profile.png 这张占位图（在 s.pximg.net，不受管上游）。
    // 当成空，界面用自带的兜底图标，不替 pixiv 展示它的默认头像
    avatarUrl = user.profileImageUrls.medium
        .takeIf { it.isNotBlank() && !it.contains("/common/images/no_profile") },
    bannerUrl = profile.backgroundImageUrl?.takeIf { it.isNotBlank() },
    comment = cleanPixivDescription(user.comment),
    illustCount = profile.totalIllusts,
    mangaCount = profile.totalManga,
    novelCount = profile.totalNovels,
    bookmarkCount = profile.totalIllustBookmarksPublic,
    followCount = profile.totalFollowUsers,
    twitterUrl = profile.twitterUrl?.takeIf { it.isNotBlank() },
    webpage = profile.webpage?.takeIf { it.isNotBlank() },
    premium = profile.isPremium,
    followed = user.isFollowed,
)

/** 收藏列表的下一页游标：max_bookmark_id，与其它列表的 offset 不同型 */
internal fun pixivBookmarkCursor(nextUrl: String?): Long? {
    if (nextUrl.isNullOrBlank()) return null
    return nextUrl.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == "max_bookmark_id" }
        ?.substringAfter('=')
        ?.toLongOrNull()
}

internal fun PixivNovel.toWork(): Work? {
    val novelId = novelId
    val thumb = imageUrls.large.ifBlank { imageUrls.medium }.ifBlank { imageUrls.squareMedium }
    if (novelId <= 0 || thumb.isBlank()) return null
    return Work(
        id = novelId,
        authorId = user.userId,
        authorName = user.name,
        authorAvatarUrl = user.profileImageUrls.medium.ifBlank { null },
        categoryCd = -1,
        categoryName = "",
        title = title,
        thumbnailUrl = thumb,
        textLength = textLength,
        imageCount = 1,
        r18 = xRestrict > 0,
        source = WorkSource.PIXIV,
        kind = WorkKind.NOVEL,
    )
}

internal fun PixivNovel.toWorkStats(): WorkStats = WorkStats(
    views = totalView ?: 0,
    bookmarks = totalBookmarks ?: 0,
    postedAt = createDate,
    pageCount = 1,
    authorAccount = user.account,
)

/** 新着小说的下一页游标：max_novel_id */
internal fun pixivNovelCursor(nextUrl: String?): Long? {
    if (nextUrl.isNullOrBlank()) return null
    return nextUrl.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == "max_novel_id" }
        ?.substringAfter('=')
        ?.toLongOrNull()
}

/** webview 页面里内嵌的小说对象：正文在 text 字段，页面结构变了就解析不出来 */
private val WEBVIEW_NOVEL_JSON = Regex("""novel:\s+(\{.+?\}),\s+isOwnWork""", RegexOption.DOT_MATCHES_ALL)

private val NOVEL_CHAPTER = Regex("""\[chapter:([^\]]*)\]""")
private val NOVEL_JUMPURI = Regex("""\[\[jumpuri:([^>\]]*)>[^\]]*\]\]""")
private val NOVEL_RUBY = Regex("""\[\[rb:([^>\]]*)>([^\]]*)\]\]""")
private val NOVEL_IMAGE = Regex("""\[(?:pixivimage|uploadedimage):[^\]]*\]""")
private val NOVEL_JUMP = Regex("""\[jump:[^\]]*\]""")

/**
 * 正文的 pixiv 私有标记换成纯文本：注音挂括号、超链接留标题、图片与跳转标记丢弃
 * （阅读器不渲染内嵌图，留标记只会变成噪音）。[newpage] 转成空行保留段落感。
 */
internal fun cleanPixivNovelText(raw: String): String = raw
    .replace(NOVEL_RUBY) { m -> "${m.groupValues[1]}（${m.groupValues[2]}）" }
    .replace(NOVEL_JUMPURI) { m -> m.groupValues[1] }
    .replace(NOVEL_CHAPTER) { m -> "\n${m.groupValues[1]}\n" }
    .replace("[newpage]", "\n\n")
    .replace(NOVEL_IMAGE, "")
    .replace(NOVEL_JUMP, "")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

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
