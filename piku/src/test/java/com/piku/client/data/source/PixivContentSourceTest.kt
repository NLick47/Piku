package com.piku.client.data.source

import com.piku.client.data.auth.PixivAuthEndpoints
import com.piku.client.data.auth.PixivAuthApi
import com.piku.client.data.auth.PixivAuthRepository
import com.piku.client.data.auth.PixivAuthRuntime
import com.piku.client.data.auth.PixivAuthStore
import com.piku.client.data.auth.PixivToken
import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.QuietFollowStore
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.ImageRouteController
import com.piku.client.data.remote.PikuJson
import com.piku.client.data.auth.pixivClientHash
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivAppActionResponse
import com.piku.client.data.remote.pixiv.PixivAppApi
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.remote.pixiv.PixivAppIllust
import com.piku.client.data.remote.pixiv.PixivAppIllustDetailResponse
import com.piku.client.data.remote.pixiv.PixivAppIllustFull
import com.piku.client.data.remote.pixiv.PixivAppIllustFullResponse
import com.piku.client.data.remote.pixiv.PixivAppImageUrls
import com.piku.client.data.remote.pixiv.PixivAppMetaPage
import com.piku.client.data.remote.pixiv.PixivAutoWordsResponse
import com.piku.client.data.remote.pixiv.PixivContentType
import com.piku.client.data.remote.pixiv.PixivIllustsResponse
import com.piku.client.data.remote.pixiv.PixivNovel
import com.piku.client.data.remote.pixiv.PixivNovelAjaxBody
import com.piku.client.data.remote.pixiv.PixivNovelAjaxResponse
import com.piku.client.data.remote.pixiv.PixivNovelAjaxTag
import com.piku.client.data.remote.pixiv.PixivNovelAjaxTags
import com.piku.client.data.remote.pixiv.PixivNovelDetailResponse
import com.piku.client.data.remote.pixiv.PixivNovelEmbeddedImage
import com.piku.client.data.remote.pixiv.PixivNovelsResponse
import com.piku.client.data.remote.pixiv.PixivRankingItem
import com.piku.client.data.remote.pixiv.PixivTrendTagsResponse
import com.piku.client.data.remote.pixiv.PixivUserDetailResponse
import com.piku.client.data.remote.pixiv.PixivUserPreviewsResponse
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.model.WorkSource
import com.piku.client.data.remote.pixiv.PixivIllustResponse
import com.piku.client.data.remote.pixiv.PixivIllustBody
import com.piku.client.data.remote.pixiv.PixivIllustTags
import com.piku.client.data.remote.pixiv.PixivTag
import com.piku.client.data.remote.pixiv.PixivPage
import com.piku.client.data.remote.pixiv.PixivPageUrls
import com.piku.client.data.remote.pixiv.PixivPagesResponse
import com.piku.client.data.remote.pixiv.PixivRankingResponse
import com.piku.client.data.remote.pixiv.PixivRecommendBody
import com.piku.client.data.remote.pixiv.PixivRecommendResponse
import com.piku.client.data.remote.pixiv.PixivSearchResponse
import com.piku.client.data.remote.pixiv.PixivWorkCard
import com.piku.client.data.repository.PixivRepository
import com.piku.client.data.repository.NovelBlock
import com.piku.client.data.repository.pixivNewFeedCursor
import com.piku.client.data.repository.pixivTotalPages
import com.piku.client.data.repository.splitNovelBlocks
import com.piku.client.data.repository.toWork
import com.piku.client.domain.source.SourceFacetStyle
import com.piku.client.domain.source.SourceFacetVisibleWhen
import com.piku.client.domain.source.isVisibleWith
import com.piku.client.domain.source.sanitizeFacetChoices
import com.piku.client.domain.source.defaultFacetChoices
import com.piku.client.domain.source.AuthorPageStyle
import com.piku.client.domain.source.SourceAuthorOpen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivContentSourceTest {

    private class FakeApi : PixivApi {
        val calls = mutableListOf<Triple<String, String, Int>>()
        var response = PixivRankingResponse()
        /** 置位后取页抛该异常（模拟服务端 404 等） */
        var failure: HttpException? = null
        var pagesResponse = PixivPagesResponse()
        var pagesCalls = 0

        override suspend fun ranking(mode: String, content: String, page: Int, format: String): PixivRankingResponse {
            calls.add(Triple(mode, content, page))
            failure?.let { throw it }
            return response
        }

        override suspend fun illustPages(illustId: Long): PixivPagesResponse {
            pagesCalls++
            return pagesResponse
        }

        var novelMetaResponse = PixivNovelAjaxResponse()
        override suspend fun novelMeta(novelId: Long): PixivNovelAjaxResponse = novelMetaResponse

        override suspend fun searchArtworks(
            word: String,
            wordQuery: String,
            page: Int,
            sMode: String,
            order: String,
            mode: String,
            type: String,
        ): PixivSearchResponse = PixivSearchResponse()

        var detailResponse = PixivIllustResponse()
        var detailCalls = 0
        override suspend fun illustDetail(illustId: Long): PixivIllustResponse {
            detailCalls++
            return detailResponse
        }

        var recommendResponse = PixivRecommendResponse()
        var recommendCalls = 0
        override suspend fun recommend(illustId: Long, limit: Int): PixivRecommendResponse {
            recommendCalls++
            return recommendResponse
        }

        override suspend fun recommendByIds(illustIds: List<String>): PixivRecommendResponse =
            PixivRecommendResponse()
    }

    private class FakeAppApi : PixivAppApi {
        val calls = mutableListOf<Int?>()
        /** 关注流调用：offset 与 restrict 成对记下，两者都是接口契约的一部分 */
        val followCalls = mutableListOf<Pair<Int?, String>>()
        /** 新着流调用：内容类型与游标成对记下，两者都是接口契约的一部分 */
        val newCalls = mutableListOf<Pair<String, Long?>>()
        val signatures = mutableListOf<Pair<String, String>>()
        var illusts = emptyList<PixivAppIllust>()
        var newNextUrl: String? = null

        override suspend fun recommended(
            clientTime: String,
            clientHash: String,
            contentType: String,
            filter: String,
            includeRankingIllusts: Boolean,
            offset: Int?,
        ): PixivIllustsResponse {
            calls.add(offset)
            signatures.add(clientTime to clientHash)
            return PixivIllustsResponse(illusts = illusts)
        }

        override suspend fun followFeed(
            clientTime: String,
            clientHash: String,
            restrict: String,
            filter: String,
            offset: Int?,
        ): PixivIllustsResponse {
            followCalls.add(offset to restrict)
            signatures.add(clientTime to clientHash)
            return PixivIllustsResponse(illusts = illusts)
        }

        override suspend fun illustNew(
            clientTime: String,
            clientHash: String,
            contentType: String,
            filter: String,
            maxIllustId: Long?,
        ): PixivIllustsResponse {
            newCalls.add(contentType to maxIllustId)
            signatures.add(clientTime to clientHash)
            return PixivIllustsResponse(illusts = illusts, nextUrl = newNextUrl)
        }

        override suspend fun illustState(
            clientTime: String,
            clientHash: String,
            illustId: Long,
        ): PixivAppIllustDetailResponse = PixivAppIllustDetailResponse()

        var detailResponse = PixivAppIllustFullResponse()
        var detailFailure: Exception? = null
        /** 调用次数与人为延迟：在途请求合并要靠延迟把两路调用真的叠在一起 */
        var detailCalls = 0
        var detailDelayMs = 0L

        override suspend fun illustDetail(
            clientTime: String,
            clientHash: String,
            illustId: Long,
            filter: String,
        ): PixivAppIllustFullResponse {
            detailCalls++
            if (detailDelayMs > 0) delay(detailDelayMs)
            detailFailure?.let { throw it }
            return detailResponse
        }

        var related = emptyList<PixivAppIllust>()
        var relatedCalls = 0

        override suspend fun relatedIllusts(
            clientTime: String,
            clientHash: String,
            illustId: Long,
            filter: String,
        ): PixivIllustsResponse {
            relatedCalls++
            return PixivIllustsResponse(illusts = related)
        }

        override suspend fun relatedIllustsNext(
            url: String,
            clientTime: String,
            clientHash: String,
        ): PixivIllustsResponse = PixivIllustsResponse()

        override suspend fun followAdd(
            clientTime: String,
            clientHash: String,
            userId: Long,
            restrict: String,
        ): PixivAppActionResponse = PixivAppActionResponse()

        override suspend fun followDelete(
            clientTime: String,
            clientHash: String,
            userId: Long,
        ): PixivAppActionResponse = PixivAppActionResponse()

        override suspend fun bookmarkAdd(
            clientTime: String,
            clientHash: String,
            illustId: Long,
            restrict: String,
        ): PixivAppActionResponse = PixivAppActionResponse()

        override suspend fun bookmarkDelete(
            clientTime: String,
            clientHash: String,
            illustId: Long,
        ): PixivAppActionResponse = PixivAppActionResponse()

        override suspend fun searchIllust(
            clientTime: String,
            clientHash: String,
            word: String,
            searchTarget: String?,
            sort: String?,
            duration: String?,
            searchAiType: Int?,
            filter: String,
            offset: Int?,
        ): PixivIllustsResponse = PixivIllustsResponse(illusts = illusts)

        override suspend fun searchUser(
            clientTime: String,
            clientHash: String,
            word: String,
            filter: String,
            offset: Int?,
        ): PixivUserPreviewsResponse = PixivUserPreviewsResponse()

        override suspend fun userFollowing(
            clientTime: String,
            clientHash: String,
            userId: Long,
            restrict: String,
            offset: Int?,
        ): PixivUserPreviewsResponse = PixivUserPreviewsResponse()

        override suspend fun autocomplete(
            clientTime: String,
            clientHash: String,
            word: String,
        ): PixivAutoWordsResponse = PixivAutoWordsResponse()

        override suspend fun searchPopularPreview(
            clientTime: String,
            clientHash: String,
            word: String,
            searchTarget: String,
            includeTranslatedTagResults: Boolean,
            mergePlainKeywordResults: Boolean,
            filter: String,
        ): PixivIllustsResponse = PixivIllustsResponse()

        override suspend fun trendingTags(
            clientTime: String,
            clientHash: String,
            filter: String,
        ): PixivTrendTagsResponse = PixivTrendTagsResponse()

        override suspend fun userDetail(
            clientTime: String,
            clientHash: String,
            userId: Long,
            filter: String,
        ): PixivUserDetailResponse = PixivUserDetailResponse()

        override suspend fun userIllusts(
            clientTime: String,
            clientHash: String,
            userId: Long,
            type: String,
            filter: String,
            offset: Int?,
        ): PixivIllustsResponse = PixivIllustsResponse()

        override suspend fun userBookmarks(
            clientTime: String,
            clientHash: String,
            userId: Long,
            restrict: String,
            maxBookmarkId: Long?,
            filter: String,
        ): PixivIllustsResponse = PixivIllustsResponse()

        var novels = emptyList<PixivNovel>()
        var novelNextUrl: String? = null
        var novelDetailResponse = PixivNovelDetailResponse()
        var novelWebviewHtml: String = ""
        /** 小说关注流调用：offset 与 restrict 成对记下，范围档对小说同样生效 */
        val novelFollowCalls = mutableListOf<Pair<Int?, String>>()

        override suspend fun novelRecommended(
            clientTime: String,
            clientHash: String,
            filter: String,
            includeRankingLabel: Boolean,
            offset: Int?,
        ): PixivNovelsResponse = PixivNovelsResponse(novels = novels)

        override suspend fun novelFollow(
            clientTime: String,
            clientHash: String,
            restrict: String,
            offset: Int?,
        ): PixivNovelsResponse {
            novelFollowCalls.add(offset to restrict)
            return PixivNovelsResponse(novels = novels)
        }

        override suspend fun novelNew(
            clientTime: String,
            clientHash: String,
            filter: String,
            maxNovelId: Long?,
        ): PixivNovelsResponse = PixivNovelsResponse(novels = novels, nextUrl = novelNextUrl)

        override suspend fun novelDetail(
            clientTime: String,
            clientHash: String,
            novelId: Long,
        ): PixivNovelDetailResponse = novelDetailResponse

        var searchedNovelWord: String? = null

        var authorNovelsCalls = mutableListOf<Pair<Long, Int?>>()

        override suspend fun userNovels(
            clientTime: String,
            clientHash: String,
            userId: Long,
            filter: String,
            offset: Int?,
        ): PixivNovelsResponse {
            authorNovelsCalls.add(userId to offset)
            return PixivNovelsResponse(novels = novels)
        }

        override suspend fun searchNovel(
            clientTime: String,
            clientHash: String,
            word: String,
            sort: String?,
            searchAiType: Int?,
            filter: String,
            offset: Int?,
        ): PixivNovelsResponse {
            searchedNovelWord = word
            return PixivNovelsResponse(novels = novels)
        }

        override suspend fun novelWebview(
            clientTime: String,
            clientHash: String,
            novelId: Long,
            viewerVersion: String,
        ): ResponseBody = novelWebviewHtml.toResponseBody("text/html".toMediaTypeOrNull())

    }

    private fun http404() = HttpException(Response.error<Any>(404, "".toResponseBody()))

    private fun item(id: Long, sexual: Int = 0) = PixivRankingItem(
        illustId = id,
        userId = id * 10,
        userName = "author$id",
        title = "title$id",
        url = "https://i.pximg.net/$id.jpg",
        illustPageCount = "3",
        contentType = PixivContentType(sexual = sexual),
    )

    private fun work(id: Long) = Work(
        id = id,
        authorId = id,
        authorName = "author$id",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "title$id",
        thumbnailUrl = "https://i.pximg.net/img-master/img/2026/09/26/00/05/02/${id}_p0_master1200.jpg",
        imageCount = 1,
        r18 = false,
        source = WorkSource.PIXIV,
    )

    private fun repository(
        api: FakeApi,
        appApi: FakeAppApi = FakeAppApi(),
        pixivAuth: PixivAuthRepository = loggedOutPixivAuth(),
    ): PixivRepository =
        PixivRepository(
            api = api,
            appApi = appApi,
            endpoints = PixivAuthEndpoints(),
            runtime = PixivAuthRuntime(dispatcher = Dispatchers.Unconfined, now = { FIXED_NOW }),
            pixivAuth = pixivAuth,
            imageRoute = ImageRouteController(
                settings = SettingsRepository(InMemorySharedPreferences()),
                prefs = InMemorySharedPreferences(),
            ),
            quietFollowStore = QuietFollowStore(InMemorySharedPreferences()),
        )

    // 未登录会话：详情走网页端链路，与本测试改前的行为一致
    private fun loggedOutPixivAuth(): PixivAuthRepository = PixivAuthRepository(
        api = FakePixivAuthApi(),
        store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson),
        endpoints = PixivAuthEndpoints(),
        runtime = PixivAuthRuntime(dispatcher = Dispatchers.Unconfined, now = { FIXED_NOW }),
    )

    // 登录态会话：详情先走 app-api，兜底与登录态隔离的断言都在这条路上
    private fun loggedInPixivAuth(): PixivAuthRepository {
        val store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson)
        store.save(
            PixivToken(
                accessToken = "access",
                refreshToken = "refresh",
                expiresAt = FIXED_NOW + 3_600_000L,
                userId = "1",
            ),
        )
        return PixivAuthRepository(
            api = FakePixivAuthApi(),
            store = store,
            endpoints = PixivAuthEndpoints(),
            runtime = PixivAuthRuntime(dispatcher = Dispatchers.Unconfined, now = { FIXED_NOW }),
        )
    }

    private class FakePixivAuthApi : PixivAuthApi {
        override suspend fun token(
            fields: Map<String, String>,
            clientTime: String,
            clientHash: String,
        ) = throw UnsupportedOperationException()
    }

    private class FakeCipher : CredentialCipher {
        override fun encrypt(plain: String): String = "ENC:$plain"
        override fun decrypt(cipherText: String): String = cipherText.removePrefix("ENC:")
    }

    private class InMemoryStorage : CredentialStorage {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }

    private fun source(
        api: FakeApi,
        appApi: FakeAppApi = FakeAppApi(),
        pixivAuth: PixivAuthRepository = loggedOutPixivAuth(),
    ): PixivContentSource = PixivContentSource(repository(api, appApi, pixivAuth))

    private fun loggedInSource(api: FakeApi, appApi: FakeAppApi): PixivContentSource =
        source(api, appApi, loggedInPixivAuth())

    /** 小说 webview 链路是登录态专属：构造带会话的仓库 */
    private fun loggedInRepository(api: FakeApi, appApi: FakeAppApi): PixivRepository =
        repository(api, appApi, loggedInPixivAuth())

    private fun appIllust(id: String, xRestrict: Int = 0) = PixivAppIllust(
        id = id,
        title = "title$id",
        imageUrls = PixivAppImageUrls(large = "https://i.pximg.net/$id.jpg"),
        pageCount = 2,
        width = 1200,
        height = 1800,
        xRestrict = xRestrict,
    )

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }

    /** 0 起页在取页时翻译成接口的 1 起页 */
    @Test
    fun pageTranslatesZeroBasedPageToOneBasedApi() = runTest {
        val api = FakeApi()
        source(api).page(PixivContentSource.FEED_RANKING, emptyMap(), 2)

        assertEquals(listOf(Triple("daily", "all", 3)), api.calls)
    }

    @Test
    fun pageMapsItemToWork() = runTest {
        val api = FakeApi().apply {
            response = PixivRankingResponse(
                contents = listOf(item(7)),
                rankTotal = 100,
            )
        }

        val page = source(api).page(PixivContentSource.FEED_RANKING, emptyMap(), 0).getOrThrow()

        val work = page.items.single()
        assertEquals(7L, work.id)
        assertEquals(70L, work.authorId)
        assertEquals("author7", work.authorName)
        assertEquals("https://i.pximg.net/7.jpg", work.thumbnailUrl)
        assertEquals(3, work.imageCount)
        assertTrue(work.r18.not())
        assertEquals(-1, work.categoryCd)
    }

    /** R-18/敏感的下发由 pixiv 账号侧表示设置在服务端管控：源里不过滤，sexual>0 的条目原样透出 */
    @Test
    fun r18EntriesPassThroughUntouched() = runTest {
        val api = FakeApi().apply {
            response = PixivRankingResponse(
                contents = listOf(item(1), item(2, sexual = 1), item(3, sexual = 2)),
            )
        }

        val page = source(api).page(PixivContentSource.FEED_RANKING, emptyMap(), 0).getOrThrow()

        assertEquals(listOf(1L, 2L, 3L), page.items.map { it.id })
    }

    /**
     * 看图页走详情取页接口，按三档取：
     * 打底 small(540) → 清晰 regular(master1200) → 原图只留给保存。
     * 一开详情页就拉 master1200 是 1 MB 起步（原图 3 MB），这条线上直连要几十秒。
     */
    @Test
    fun workPagesPickSmallForFirstPaintAndMasterForQuality() = runTest {
        val api = FakeApi().apply {
            pagesResponse = PixivPagesResponse(
                body = listOf(
                    PixivPage(
                        width = 1200,
                        height = 900,
                        urls = PixivPageUrls(
                            small = "https://i.pximg.net/c/540x540_70/img-master/a_p0_master1200.jpg",
                            regular = "https://i.pximg.net/img-master/a_p0_master1200.jpg",
                            original = "https://i.pximg.net/img-original/a_p0.png",
                        ),
                    ),
                    // 只给原图的页：打底也要退到它，页面不能空
                    PixivPage(urls = PixivPageUrls(original = "https://i.pximg.net/img-original/b_p1.png")),
                ),
            )
        }
        val src = source(api)

        val pages = src.workPages(work(7)).getOrThrow()

        assertEquals(
            listOf(
                "https://i.pximg.net/c/540x540_70/img-master/a_p0_master1200.jpg",
                "https://i.pximg.net/img-original/b_p1.png",
            ),
            pages.map { it.url },
        )
        // 尺寸随页返回：详情页图区按它定高
        assertEquals(1200, pages[0].width)
        assertEquals(900, pages[0].height)
        // 清晰档：查看器覆盖、图片翻译、分享都用它
        assertEquals("https://i.pximg.net/img-master/a_p0_master1200.jpg", pages[0].fullUrl)
        // 原图只给保存
        assertEquals("https://i.pximg.net/img-original/a_p0.png", pages[0].originalUrl)
    }

    /** R-18 等登录墙：HTTP 200 但 error=true → 失败（NotFound 语义，重试无意义） */
    @Test
    fun loginWallPagesMapToFailure() = runTest {
        val api = FakeApi().apply { pagesResponse = PixivPagesResponse(error = true) }

        val result = source(api).workPages(work(7))

        assertTrue(result.isFailure)
    }

    /** 详情补充文本：简介 HTML 清洗 + 标签提取；登录墙（error=true）时返回 null 不报错 */
    @Test
    fun workTextCleansDescriptionAndTakesTags() = runTest {
        val api = FakeApi().apply {
            detailResponse = PixivIllustResponse(
                body = PixivIllustBody(
                    description = "第一行&amp;A<br />第二行&lt;B&gt;&#39;s",
                    tags = PixivIllustTags(
                        tags = listOf(
                            PixivTag("漫画"),
                            PixivTag(""),
                        ),
                    ),
                ),
            )
        }

        val text = source(api).workDetailText(work(7)).getOrThrow()!!

        assertEquals("第一行&A\n第二行<B>'s", text.description)
        assertEquals(listOf("漫画"), text.tags)

        api.detailResponse = com.piku.client.data.remote.pixiv.PixivIllustResponse(error = true)
        assertEquals(null, source(api).workDetailText(work(7)).getOrThrow())
    }

    /**
     * 登录隔离：登录态取详情文本只走 app-api，任何失败都不许去摸网页端。
     * 取页与取文本共用同一发 app-api 请求，失败时整页出错误态，不需要匿名数据来顶替。
     */
    @Test
    fun loggedInWorkTextNeverTouchesWeb() = runTest {
        val failures = listOf<Pair<Exception, AppError>>(
            IOException("boom") to AppError.Network,
            HttpException(Response.error<Any>(520, "".toResponseBody())) to AppError.Http(520),
        )
        for ((failure, expected) in failures) {
            val api = FakeApi()
            val appApi = FakeAppApi().apply { detailFailure = failure }

            val result = loggedInSource(api, appApi).workDetailText(work(7))

            assertEquals(expected, result.exceptionOrNull())
            assertEquals(0, api.detailCalls)
        }
    }

    /** 401 是服务端给的结论：会话失效要引导登录，不能被匿名数据盖掉，网页端也不该被打 */
    @Test
    fun loginRequiredAppApiErrorSurfacesForLoginGuide() = runTest {
        val api = FakeApi()
        val appApi = FakeAppApi().apply {
            detailFailure = HttpException(Response.error<Any>(401, "".toResponseBody()))
        }

        val result = loggedInSource(api, appApi).workDetailText(work(7))

        assertTrue(result.exceptionOrNull() is AppError.LoginRequired)
        assertEquals(0, api.detailCalls)
    }

    /** 登录隔离：登录态的相关作品走 app-api 的 v2/illust/related，不碰网页端 recommend */
    @Test
    fun loggedInRelatedWorksUseAppApi() = runTest {
        val api = FakeApi()
        val appApi = FakeAppApi().apply { related = listOf(appIllust("11")) }

        val related = loggedInSource(api, appApi).relatedWorks(work(7)).getOrThrow()

        assertEquals(listOf(11L), related.map { it.id })
        assertEquals(1, appApi.relatedCalls)
        assertEquals(0, api.recommendCalls)
    }

    /** 未登录照旧走网页端（那边一次能多给），app-api 那条不该被调用 */
    @Test
    fun loggedOutRelatedWorksStayOnWeb() = runTest {
        val api = FakeApi().apply {
            recommendResponse = PixivRecommendResponse(
                body = PixivRecommendBody(
                    illusts = listOf(PixivWorkCard(id = "12", url = "https://i.pximg.net/12.jpg")),
                ),
            )
        }
        val appApi = FakeAppApi()

        val related = source(api, appApi).relatedWorks(work(7)).getOrThrow()

        assertEquals(listOf(12L), related.map { it.id })
        assertEquals(1, api.recommendCalls)
        assertEquals(0, appApi.relatedCalls)
    }

    /** 详情页把取页与取文本并行发出来：登录态下同一作品的 v1/illust/detail 只该打一发 */
    @Test
    fun parallelPageAndTextShareOneDetailRequest() = runTest {
        val appApi = FakeAppApi().apply { detailDelayMs = 20 }
        val source = loggedInSource(FakeApi(), appApi)

        coroutineScope {
            val pages = async { source.workPages(work(7)) }
            val text = async { source.workDetailText(work(7)) }
            pages.await()
            text.await()
        }

        assertEquals(1, appApi.detailCalls)
    }

    /** 合并只覆盖在途那一次：出了详情页再进来照旧重新取，不留缓存 */
    @Test
    fun sequentialDetailRequestsAreNotCached() = runTest {
        val appApi = FakeAppApi().apply { detailDelayMs = 20 }
        val source = loggedInSource(FakeApi(), appApi)

        source.workPages(work(7))
        source.workDetailText(work(7))

        assertEquals(2, appApi.detailCalls)
    }

    /** 实测翻过末页接口回 404：翻页中的 NotFound 就地判到底，首屏 404 照常失败 */
    @Test
    fun beyondRangePageBecomesEmptyNotError() = runTest {
        val api = FakeApi().apply { failure = http404() }
        val src = source(api)

        val beyond = src.page(PixivContentSource.FEED_RANKING, emptyMap(), 10).getOrThrow()
        assertTrue(beyond.items.isEmpty())

        val first = src.page(PixivContentSource.FEED_RANKING, emptyMap(), 0)
        assertTrue(first.isFailure)
    }

    /** 周期片选驱动接口 mode，内容类型下拉驱动 content */
    @Test
    fun periodFacetDrivesMode() = runTest {
        val api = FakeApi()

        source(api).page(
            PixivContentSource.FEED_RANKING,
            mapOf(PixivContentSource.GROUP_PERIOD to "weekly", PixivContentSource.GROUP_CONTENT to "manga"),
            0,
        )

        assertEquals(listOf(Triple("weekly", "manga", 1)), api.calls)
    }

    /** 独立榜（男生向等）固定单一日榜：mode 直接用选项值，周期不参与，content 恒传 all（实测 illust/manga 404） */
    @Test
    fun familyBoardFacetDrivesModeIgnoringPeriod() = runTest {
        val api = FakeApi()

        source(api).page(
            PixivContentSource.FEED_RANKING,
            mapOf(
                PixivContentSource.GROUP_PERIOD to "weekly",
                PixivContentSource.GROUP_CONTENT to "male",
            ),
            0,
        )

        assertEquals(listOf(Triple("male", "all", 1)), api.calls)
    }

    /** 推荐流走应用接口：0 起页换算成接口的 offset（单次上限 30） */
    @Test
    fun recommendedPageTranslatesToOffset() = runTest {
        val app = FakeAppApi()
        val src = source(FakeApi(), app)

        src.page(PixivContentSource.FEED_RECOMMEND, emptyMap(), 0)
        src.page(PixivContentSource.FEED_RECOMMEND, emptyMap(), 2)

        assertEquals(listOf(0, 2 * PixivAppConfig.PAGE_SIZE), app.calls)
    }

    /**
     * 客户端签名的两个头必须由同一个时间串算出。分成两次算（时钟各走一次）就对不上，
     * pixiv 只会回一句「客户端凭据不合法」，看不出是这里错了。
     */
    @Test
    fun recommendedSendsMatchedClientSignature() = runTest {
        val app = FakeAppApi()

        source(FakeApi(), app).page(PixivContentSource.FEED_RECOMMEND, emptyMap(), 0)

        val (time, hash) = app.signatures.single()
        assertEquals(pixivClientHash(time), hash)
    }

    /** 推荐卡带原作宽高（按比例排版用）；R-18 不再被源过滤 */
    @Test
    fun recommendedCarriesSizeAndKeepsR18() = runTest {
        val app = FakeAppApi().apply {
            illusts = listOf(appIllust("1"), appIllust("2", xRestrict = 1))
        }

        val page = source(FakeApi(), app).page(PixivContentSource.FEED_RECOMMEND, emptyMap(), 0).getOrThrow()

        assertEquals(listOf(1L, 2L), page.items.map { it.id })
        val work = page.items.first()
        assertEquals(1200, work.thumbWidth)
        assertEquals(1800, work.thumbHeight)
        assertEquals("https://i.pximg.net/1.jpg", work.thumbnailUrl)
    }

    /** 关注流与推荐同款翻页：0 起页换算成接口 offset */
    @Test
    fun followPageTranslatesToOffset() = runTest {
        val app = FakeAppApi()
        val src = source(FakeApi(), app)

        src.page(PixivContentSource.FEED_FOLLOW, emptyMap(), 0)
        src.page(PixivContentSource.FEED_FOLLOW, emptyMap(), 2)

        assertEquals(listOf(0, 2 * PixivAppConfig.PAGE_SIZE), app.followCalls.map { it.first })
        // 空档位收敛为默认：公开关注。private 只在范围档选了悄悄时才发
        assertTrue(app.followCalls.all { it.second == PixivAppConfig.RESTRICT_PUBLIC })
    }

    /** 关注流范围档驱动 restrict：悄悄档原样发出去，插画与小说两条链路同参数 */
    @Test
    fun followScopeFacetDrivesRestrict() = runTest {
        val app = FakeAppApi()
        val src = source(FakeApi(), app)

        src.page(
            PixivContentSource.FEED_FOLLOW,
            mapOf(PixivContentSource.GROUP_FOLLOW_SCOPE to PixivAppConfig.RESTRICT_PRIVATE),
            0,
        )
        src.page(
            PixivContentSource.FEED_FOLLOW,
            mapOf(
                PixivContentSource.GROUP_CONTENT to PixivContentSource.FACET_NOVEL,
                PixivContentSource.GROUP_FOLLOW_SCOPE to PixivAppConfig.RESTRICT_PRIVATE,
            ),
            0,
        )

        assertEquals(PixivAppConfig.RESTRICT_PRIVATE, app.followCalls.single().second)
        assertEquals(PixivAppConfig.RESTRICT_PRIVATE, app.novelFollowCalls.single().second)
    }

    /** 关注卡同样按比例排版；R-18 不再被源过滤 */
    @Test
    fun followCarriesSizeAndKeepsR18() = runTest {
        val app = FakeAppApi().apply {
            illusts = listOf(appIllust("1"), appIllust("2", xRestrict = 1))
        }

        val page = source(FakeApi(), app).page(PixivContentSource.FEED_FOLLOW, emptyMap(), 0).getOrThrow()

        assertEquals(listOf(1L, 2L), page.items.map { it.id })
        val work = page.items.first()
        assertEquals(1200, work.thumbWidth)
        assertEquals(1800, work.thumbHeight)
        assertEquals("https://i.pximg.net/1.jpg", work.thumbnailUrl)
    }

    /**
     * 新着流按游标翻页：首页不带游标；上一页 next_url 里的 max_illust_id 供下一页用；
     * 同页重取（预取被作废后的重拉）永远用同一游标，不漂移；刷新从页 0 推倒重来。
     */
    @Test
    fun latestPageWalksCursorFromNextUrl() = runTest {
        val app = FakeAppApi()
        val src = source(FakeApi(), app)

        // 首页响应就带 next_url：下一页游标随响应回来
        app.newNextUrl = "https://app-api.pixiv.net/v1/illust/new?filter=for_android&content_type=illust&max_illust_id=12345"
        src.page(PixivContentSource.FEED_LATEST, emptyMap(), 0)
        // 页 1 的响应再带回更后一段游标，页 2 才会换用它
        app.newNextUrl = "https://app-api.pixiv.net/v1/illust/new?filter=for_android&content_type=illust&max_illust_id=67890"
        src.page(PixivContentSource.FEED_LATEST, emptyMap(), 1)
        src.page(PixivContentSource.FEED_LATEST, emptyMap(), 2)
        src.page(PixivContentSource.FEED_LATEST, emptyMap(), 2)

        assertEquals(
            listOf<Long?>(null, 12345L, 67890L, 67890L),
            app.newCalls.map { it.second },
        )

        // 刷新：页 0 不带游标重来，链路覆盖旧游标
        src.page(PixivContentSource.FEED_LATEST, emptyMap(), 0)
        src.page(PixivContentSource.FEED_LATEST, emptyMap(), 1)
        assertEquals(
            listOf<Long?>(null, 67890L),
            app.newCalls.takeLast(2).map { it.second },
        )
    }

    /** 新着流内容档驱动 content_type，默认插画；换档即换流，各自从头翻 */
    @Test
    fun latestContentFacetDrivesContentType() = runTest {
        val app = FakeAppApi()
        val src = source(FakeApi(), app)

        src.page(PixivContentSource.FEED_LATEST, emptyMap(), 0)
        src.page(
            PixivContentSource.FEED_LATEST,
            mapOf(PixivContentSource.GROUP_LATEST_CONTENT to PixivContentSource.FACET_MANGA),
            0,
        )

        assertEquals(
            listOf(
                PixivContentSource.FACET_ILLUST to null,
                PixivContentSource.FACET_MANGA to null,
            ),
            app.newCalls,
        )
    }

    /** 新着卡同样按比例排版；R-18 不再被源过滤 */
    @Test
    fun latestCarriesSizeAndKeepsR18() = runTest {
        val app = FakeAppApi().apply {
            illusts = listOf(appIllust("1"), appIllust("2", xRestrict = 1))
        }

        val page = source(FakeApi(), app).page(PixivContentSource.FEED_LATEST, emptyMap(), 0).getOrThrow()

        assertEquals(listOf(1L, 2L), page.items.map { it.id })
        val work = page.items.first()
        assertEquals(1200, work.thumbWidth)
        assertEquals(1800, work.thumbHeight)
        assertEquals("https://i.pximg.net/1.jpg", work.thumbnailUrl)
    }

    /** 作者区进应用内的画师主页，且声明的是资料页形态——外壳据此挑页面 */
    @Test
    fun authorPageOpensInAppProfile() {
        val source = source(FakeApi())

        assertEquals(SourceAuthorOpen.NativeProfile, source.authorPage(work(7)))
        assertEquals(AuthorPageStyle.Profile, source.authorPageStyle)
    }

    /** 声明形态：四条流 = 三个登录门 + 榜单名次流；榜单带周期片选与榜单下拉，新着另有内容档 */
    @Test
    fun declarationsKeepOneRankingTabWithPeriodChips() {
        assertEquals(
            listOf(
                PixivContentSource.FEED_RECOMMEND,
                PixivContentSource.FEED_FOLLOW,
                PixivContentSource.FEED_RANKING,
                PixivContentSource.FEED_LATEST,
            ),
            PixivContentSource.FEEDS.map { it.id },
        )
        // 声明里不再有占位流：每条都已接通
        assertEquals(PixivContentSource.FEEDS.map { it.id }.toSet(), PixivContentSource.IMPLEMENTED_FEEDS)
        val recommend = PixivContentSource.FEEDS.first { it.id == PixivContentSource.FEED_RECOMMEND }
        assertTrue(recommend.requiresLogin)
        // 已接通：登录后就该有内容，不再是「即将上线」
        assertFalse(recommend.pendingAfterLogin)
        assertTrue(recommend.proportional)
        val follow = PixivContentSource.FEEDS.first { it.id == PixivContentSource.FEED_FOLLOW }
        assertTrue(follow.requiresLogin)
        assertFalse(follow.pendingAfterLogin)
        assertTrue(follow.proportional)
        // 关注流按投稿时间倒序：刷新要能算「新增 N 条」
        assertTrue(follow.chronological)
        val ranking = PixivContentSource.FEEDS.first { it.id == PixivContentSource.FEED_RANKING }
        assertTrue(ranking.ranked)
        assertFalse(ranking.requiresLogin)
        val latest = PixivContentSource.FEEDS.first { it.id == PixivContentSource.FEED_LATEST }
        assertTrue(latest.requiresLogin)
        assertFalse(latest.pendingAfterLogin)
        assertTrue(latest.proportional)
        // 新着按投稿时间序：刷新要能算「新增 N 条」
        assertTrue(latest.chronological)

        val period = PixivContentSource.FACETS.first { it.id == PixivContentSource.GROUP_PERIOD }
        assertEquals(SourceFacetStyle.Chips, period.style)
        assertEquals(PixivContentSource.FEED_RANKING, period.feedId)
        assertEquals(listOf("daily", "weekly", "monthly", "rookie"), period.options.map { it.id })
        assertEquals("daily", period.options.first { it.selectedByDefault }.id)
        // 周期 chips 只在综合族显示：独立榜只有单一日榜，没有周期可选
        assertEquals(
            SourceFacetVisibleWhen(PixivContentSource.GROUP_CONTENT, setOf("all", "illust", "manga")),
            period.visibleOnlyWhen,
        )
        assertTrue(period.isVisibleWith(mapOf(PixivContentSource.GROUP_CONTENT to PixivContentSource.FACET_ALL)))
        assertFalse(period.isVisibleWith(mapOf(PixivContentSource.GROUP_CONTENT to "male")))

        val content = PixivContentSource.FACETS.first { it.id == PixivContentSource.GROUP_CONTENT }
        assertEquals(SourceFacetStyle.Dropdown, content.style)
        assertEquals(PixivContentSource.FEED_RANKING, content.feedId)
        // 综合族三档 + 独立榜三档（选项 id 即 ranking.php 的 mode 值），AI 榜与 R-18 榜不进（总量 50 / 通道 403）；
        // 综合族与独立榜是两类榜单，菜单里用分隔线分段
        assertEquals(
            listOf("all", "illust", "manga", "male", "female", "original"),
            content.options.map { it.id },
        )
        assertEquals(listOf("manga"), content.options.filter { it.dividerAfter }.map { it.id })

        val latestContent = PixivContentSource.FACETS.first { it.id == PixivContentSource.GROUP_LATEST_CONTENT }
        assertEquals(SourceFacetStyle.Dropdown, latestContent.style)
        assertEquals(PixivContentSource.FEED_LATEST, latestContent.feedId)
        assertEquals(listOf("illust", "manga", "novel"), latestContent.options.map { it.id })
        assertEquals("illust", latestContent.options.first { it.selectedByDefault }.id)

        // 推荐流有 content_type 参数，插画/漫画/小说三档都发得出去
        val recommendContent = PixivContentSource.FACETS.first {
            it.id == PixivContentSource.GROUP_CONTENT && it.feedId == PixivContentSource.FEED_RECOMMEND
        }
        assertEquals(listOf("illust", "manga", "novel"), recommendContent.options.map { it.id })
        // 关注流接口不分插画/漫画：多给一档就是选了没反应
        val followContent = PixivContentSource.FACETS.first {
            it.id == PixivContentSource.GROUP_CONTENT && it.feedId == PixivContentSource.FEED_FOLLOW
        }
        assertEquals(listOf("illust", "novel"), followContent.options.map { it.id })

        // 关注流的范围档：id 直接就是 restrict 参数值，公开默认、悄悄选了才发；片选常显，tab 行只留内容档一个下拉
        val followScope = PixivContentSource.FACETS.first { it.id == PixivContentSource.GROUP_FOLLOW_SCOPE }
        assertEquals(SourceFacetStyle.Chips, followScope.style)
        assertEquals(PixivContentSource.FEED_FOLLOW, followScope.feedId)
        assertEquals(
            listOf(PixivAppConfig.RESTRICT_PUBLIC, PixivAppConfig.RESTRICT_PRIVATE),
            followScope.options.map { it.id },
        )
        assertEquals(PixivAppConfig.RESTRICT_PUBLIC, followScope.options.first { it.selectedByDefault }.id)
    }

    /** 小说档必须走小说接口并落成 NOVEL 作品：走错接口取到的是插画，kind 也不会是小说 */
    @Test
    fun novelFacetRoutesToNovelEndpoint() = runTest {
        val appApi = FakeAppApi()
        appApi.novels = listOf(
            PixivNovel(
                id = "77",
                title = "novel77",
                imageUrls = PixivAppImageUrls(large = "https://i.pximg.net/novel77.jpg"),
                textLength = 1200,
            ),
        )
        appApi.illusts = listOf(appIllust("88"))
        val page = source(FakeApi(), appApi)
            .page(PixivContentSource.FEED_RECOMMEND, mapOf(PixivContentSource.GROUP_CONTENT to PixivContentSource.FACET_NOVEL), 0)
            .getOrThrow()
        assertEquals(listOf(77L), page.items.map { it.id })
        assertEquals(listOf(WorkKind.NOVEL), page.items.map { it.kind })
        assertEquals(1200, page.items.first().textLength)
    }

    /**
     * 切流的档位隔离：换流后只能用新流自己的档位（VM 按流分别记，换流时取新流那份），
     * 万一有旧档位漏过来，收敛也只认这一流声明过的选项。
     */
    @Test
    fun facetChoicesStayInsideOneFeed() = runTest {
        val source = PixivContentSource(repository(FakeApi()))
        // 关注流选了小说：在关注流里合法，原样保留；范围档补上自己的默认（公开）
        val follow = mapOf(PixivContentSource.GROUP_CONTENT to PixivContentSource.FACET_NOVEL)
        assertEquals(
            follow + (PixivContentSource.GROUP_FOLLOW_SCOPE to PixivAppConfig.RESTRICT_PUBLIC),
            source.sanitizeFacetChoices(PixivContentSource.FEED_FOLLOW, follow),
        )
        // 榜单没有小说这一档：带着别流的档位过来要退回榜单自己的默认（周期片选照常在）
        assertEquals(
            mapOf(
                PixivContentSource.GROUP_PERIOD to PixivContentSource.PERIOD_DAILY,
                PixivContentSource.GROUP_CONTENT to PixivContentSource.FACET_ALL,
            ),
            source.sanitizeFacetChoices(PixivContentSource.FEED_RANKING, follow),
        )
        // 榜单的周期片选不属于推荐流，推荐流的默认档里不该出现
        assertEquals(
            mapOf(PixivContentSource.GROUP_CONTENT to PixivContentSource.FACET_ILLUST),
            source.defaultFacetChoices(PixivContentSource.FEED_RECOMMEND),
        )
        assertEquals(
            mapOf(
                PixivContentSource.GROUP_PERIOD to PixivContentSource.PERIOD_DAILY,
                PixivContentSource.GROUP_CONTENT to PixivContentSource.FACET_ALL,
            ),
            source.defaultFacetChoices(PixivContentSource.FEED_RANKING),
        )
        // 新流没有记忆时就是默认档：换流不带走上一个流的任何选择
        assertEquals(
            mapOf(PixivContentSource.GROUP_CONTENT to PixivContentSource.FACET_ILLUST),
            source.sanitizeFacetChoices(PixivContentSource.FEED_RECOMMEND, null),
        )
    }

    /** 正文来自 webview 页面里内嵌的 novel 对象：官方的 /v1/novel/text 已下线（真机 404） */
    @Test
    fun novelBodyExtractedFromWebviewHtml() = runTest {
        val appApi = FakeAppApi()
        appApi.novelWebviewHtml =
            """<html><body>novel: {"id":"77","title":"題","text":"[chapter:一][newpage]本文"}, isOwnWork: false,</body></html>"""
        assertEquals("一\n\n本文", loggedInRepository(FakeApi(), appApi).novelBody(77).getOrThrow())
    }

    /** 页面结构变了（取不到 novel 对象）必须报终态，不能当成空正文放行 */
    @Test
    fun novelBodyFailsWhenWebviewHasNoNovelObject() = runTest {
        val appApi = FakeAppApi()
        appApi.novelWebviewHtml = "<html><body>not found</body></html>"
        assertTrue(loggedInRepository(FakeApi(), appApi).novelBody(77).isFailure)
    }

    /**
     * 内嵌图 token 化：uploadedimage 查 payload 的 images 表（1200x1200 档优先）；
     * pixivimage 的 illusts 表线上恒为空数组，按作品 id 去重走看图链路（页码 1 起）
     */
    @Test
    fun novelBodyResolvesInlineImages() = runTest {
        val api = FakeApi()
        val appApi = FakeAppApi().apply {
            novelWebviewHtml = """
                <html><body>novel: {"id":"77","text":"段落一[pixivimage:88-2]段落二[uploadedimage:1]段落三[pixivimage:88]段落四",
                "illusts":[],
                "images":{"1":{"novelImageId":"1","urls":{"1200x1200":"https://i.pximg.net/novel/e1.jpg"}}}}, isOwnWork: false,</body></html>
            """.trimIndent()
            detailResponse = PixivAppIllustFullResponse(
                illust = PixivAppIllustFull(
                    metaPages = listOf(
                        PixivAppMetaPage(
                            imageUrls = PixivAppImageUrls(medium = "https://i.pximg.net/img-master/a_p0_master1200.jpg"),
                        ),
                        PixivAppMetaPage(
                            imageUrls = PixivAppImageUrls(medium = "https://i.pximg.net/img-master/a_p1_master1200.jpg"),
                        ),
                    ),
                ),
            )
        }

        val body = loggedInRepository(api, appApi).novelBody(77).getOrThrow()

        assertEquals(
            listOf(
                NovelBlock.Text("段落一"),
                NovelBlock.Image("https://i.pximg.net/img-master/a_p1_master1200.jpg"),
                NovelBlock.Text("段落二"),
                NovelBlock.Image("https://i.pximg.net/novel/e1.jpg"),
                NovelBlock.Text("段落三"),
                NovelBlock.Image("https://i.pximg.net/img-master/a_p0_master1200.jpg"),
                NovelBlock.Text("段落四"),
            ),
            splitNovelBlocks(body),
        )
        // 两个标记同一个作品 id：看图链路只打一次；uploadedimage 查表，网页端一次都不摸
        assertEquals(1, appApi.detailCalls)
        assertEquals(0, api.pagesCalls)
    }

    /** webview 载荷没带 images 表时向网页端补一发（匿名），补到就渲染 */
    @Test
    fun novelBodyFallsBackToWebForEmbeddedImages() = runTest {
        val api = FakeApi().apply {
            novelMetaResponse = PixivNovelAjaxResponse(
                body = PixivNovelAjaxBody(
                    textEmbeddedImages = mapOf(
                        "3" to PixivNovelEmbeddedImage(urls = mapOf("1200x1200" to "https://i.pximg.net/novel/w3.jpg")),
                    ),
                ),
            )
        }
        val appApi = FakeAppApi().apply {
            novelWebviewHtml = """<html><body>novel: {"id":"77","text":"前[uploadedimage:3]后"}, isOwnWork: false,</body></html>"""
        }

        val body = loggedInRepository(api, appApi).novelBody(77).getOrThrow()

        assertEquals(
            listOf(
                NovelBlock.Text("前"),
                NovelBlock.Image("https://i.pximg.net/novel/w3.jpg"),
                NovelBlock.Text("后"),
            ),
            splitNovelBlocks(body),
        )
    }

    /** 解析不出的内嵌图（载荷没有 / 作品不可见 / URL 表没有）清掉：正文干净，不带标记也不带死 token */
    @Test
    fun novelBodyDropsUnresolvableInlineImages() = runTest {
        val api = FakeApi()
        val appApi = FakeAppApi().apply {
            novelWebviewHtml = """
                <html><body>novel: {"id":"77","text":"A[pixivimage:88]B[pixivimage:99]C[uploadedimage:9]D",
                "illusts":{"99":{"visible":false,"availableMessage":"非公開","illust":{"images":{}}}}}, isOwnWork: false,</body></html>
            """.trimIndent()
        }

        val body = loggedInRepository(api, appApi).novelBody(77).getOrThrow()

        assertEquals("ABCD", body)
    }

    /** 未登录小说详情走网页端：简介清洗、标签、统计全从匿名载荷取，app-api 不该被打 */
    @Test
    fun novelTextLoggedOutUsesWeb() = runTest {
        val api = FakeApi().apply {
            novelMetaResponse = PixivNovelAjaxResponse(
                body = PixivNovelAjaxBody(
                    description = "第一行&amp;A<br />第二行",
                    tags = PixivNovelAjaxTags(tags = listOf(PixivNovelAjaxTag("東方"), PixivNovelAjaxTag(""))),
                    viewCount = 123,
                    likeCount = 4,
                    bookmarkCount = 55,
                    createDate = "2026-10-07T00:00:00+09:00",
                ),
            )
        }

        val text = repository(api, FakeAppApi()).novelText(77).getOrThrow()!!

        assertEquals("第一行&A\n第二行", text.description)
        assertEquals(listOf("東方"), text.tags)
        assertEquals(123, text.stats?.views)
        assertEquals(4, text.stats?.likes)
        assertEquals(55, text.stats?.bookmarks)
    }

    /** 未登录撞 R-18/登录限定小说：error=true → 登录墙，与插画同形 */
    @Test
    fun novelTextLoggedOutLoginWall() = runTest {
        val api = FakeApi().apply { novelMetaResponse = PixivNovelAjaxResponse(error = true) }

        val result = repository(api, FakeAppApi()).novelText(77)

        assertTrue(result.exceptionOrNull() is AppError.LoginRequired)
    }

    /** 未登录正文走网页端：content + textEmbeddedImages，pixivimage 无预解析走看图链路（页码 1 起） */
    @Test
    fun novelBodyLoggedOutUsesWeb() = runTest {
        val api = FakeApi().apply {
            pagesResponse = PixivPagesResponse(
                body = listOf(
                    PixivPage(urls = PixivPageUrls(regular = "https://i.pximg.net/img-master/a_p0_master1200.jpg")),
                    PixivPage(urls = PixivPageUrls(regular = "https://i.pximg.net/img-master/a_p1_master1200.jpg")),
                ),
            )
            novelMetaResponse = PixivNovelAjaxResponse(
                body = PixivNovelAjaxBody(
                    content = "段落一[pixivimage:88-2]段落二[uploadedimage:3]段落三",
                    textEmbeddedImages = mapOf(
                        "3" to PixivNovelEmbeddedImage(urls = mapOf("1200x1200" to "https://i.pximg.net/novel/e3.jpg")),
                    ),
                ),
            )
        }

        val body = repository(api, FakeAppApi()).novelBody(77).getOrThrow()

        assertEquals(
            listOf(
                NovelBlock.Text("段落一"),
                NovelBlock.Image("https://i.pximg.net/img-master/a_p1_master1200.jpg"),
                NovelBlock.Text("段落二"),
                NovelBlock.Image("https://i.pximg.net/novel/e3.jpg"),
                NovelBlock.Text("段落三"),
            ),
            splitNovelBlocks(body),
        )
    }

    /** 未登录正文撞登录墙：整体失败给登录引导，而不是空正文 */
    @Test
    fun novelBodyLoggedOutLoginWall() = runTest {
        val api = FakeApi().apply { novelMetaResponse = PixivNovelAjaxResponse(error = true) }

        val result = repository(api, FakeAppApi()).novelBody(77)

        assertTrue(result.exceptionOrNull() is AppError.LoginRequired)
    }

    /** 正文的 pixiv 私有标记按官方语义清洗：注音挂括号、超链接留标题、图片与跳转标记丢掉 */
    @Test
    fun novelTextMarkupCleanedToPlainText() {
        val raw = "[chapter:第一章][newpage]本文[[rb:漢字>かんじ]]と[[jumpuri:参考>https://example.com]]" +
            "[pixivimage:12345][jump:3]"
        assertEquals(
            "第一章\n\n本文漢字（かんじ）と参考",
            com.piku.client.data.repository.cleanPixivNovelText(raw),
        )
    }

    @Test
    fun totalPagesDerivedFromRankTotal() {
        assertEquals(11, com.piku.client.data.repository.pixivTotalPages(502))
        assertEquals(1, com.piku.client.data.repository.pixivTotalPages(50))
        assertNull(com.piku.client.data.repository.pixivTotalPages(0))
    }

    /** next_url 只为取游标服务：末页无 next_url、无游标参数或解析不出都按 null（首页）处理 */
    @Test
    fun newFeedCursorParsesMaxIllustIdFromNextUrl() {
        assertEquals(
            12345L,
            pixivNewFeedCursor(
                "https://app-api.pixiv.net/v1/illust/new?filter=for_android&content_type=illust&max_illust_id=12345",
            ),
        )
        assertNull(pixivNewFeedCursor("https://app-api.pixiv.net/v1/illust/new?filter=for_android&content_type=illust"))
        assertNull(pixivNewFeedCursor("https://app-api.pixiv.net/v1/illust/new?max_illust_id=not-a-number"))
        assertNull(pixivNewFeedCursor(null))
        assertNull(pixivNewFeedCursor(""))
    }
}
