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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivAppActionResponse
import com.piku.client.data.remote.pixiv.PixivAppApi
import com.piku.client.data.remote.pixiv.PixivAppIllust
import com.piku.client.data.remote.pixiv.PixivAppIllustDetailResponse
import com.piku.client.data.remote.pixiv.PixivAppIllustFullResponse
import com.piku.client.data.remote.pixiv.PixivAppIllustTag
import com.piku.client.data.remote.pixiv.PixivAppImageUrls
import com.piku.client.data.remote.pixiv.PixivAppProfileImages
import com.piku.client.data.remote.pixiv.PixivAppUser
import com.piku.client.data.remote.pixiv.PixivAutoTag
import com.piku.client.data.remote.pixiv.PixivAutoWordsResponse
import com.piku.client.data.remote.pixiv.PixivIllustResponse
import com.piku.client.data.remote.pixiv.PixivIllustsResponse
import com.piku.client.data.remote.pixiv.PixivNovelAjaxResponse
import com.piku.client.data.remote.pixiv.PixivNovelDetailResponse
import com.piku.client.data.remote.pixiv.PixivNovelsResponse
import com.piku.client.data.remote.pixiv.PixivPagesResponse
import com.piku.client.data.remote.pixiv.PixivRankingResponse
import com.piku.client.data.remote.pixiv.PixivRecommendResponse
import com.piku.client.data.remote.pixiv.PixivSearchBody
import com.piku.client.data.remote.pixiv.PixivSearchItem
import com.piku.client.data.remote.pixiv.PixivSearchResponse
import com.piku.client.data.remote.pixiv.PixivSearchResult
import com.piku.client.data.remote.pixiv.PixivTrendTagsResponse
import com.piku.client.data.remote.pixiv.PixivUserDetailResponse
import com.piku.client.data.remote.pixiv.PixivUserPreview
import com.piku.client.data.remote.pixiv.PixivUserPreviewsResponse
import com.piku.client.data.repository.PixivRepository
import com.piku.client.data.repository.pixivProportionalThumb
import com.piku.client.data.repository.pixivTagThumb
import com.piku.client.data.repository.tagThumbnails
import com.piku.client.data.repository.toFollowUser
import com.piku.client.data.repository.toTrendingTag
import com.piku.client.data.repository.toWork
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.source.FILTER_TOGGLE_ON
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PixivSearchSourceTest {

    private class FakeApi : PixivApi {
        data class WebSearchCall(
            val word: String,
            val page: Int,
            val sMode: String,
            val order: String,
        )

        val webSearchCalls = mutableListOf<WebSearchCall>()
        var webSearchResponse = PixivSearchResponse()

        override suspend fun searchArtworks(
            word: String,
            wordQuery: String,
            page: Int,
            sMode: String,
            order: String,
            mode: String,
            type: String,
        ): PixivSearchResponse {
            webSearchCalls.add(WebSearchCall(word, page, sMode, order))
            return webSearchResponse
        }

        override suspend fun ranking(mode: String, content: String, page: Int, format: String): PixivRankingResponse =
            PixivRankingResponse()

        override suspend fun illustPages(illustId: Long): PixivPagesResponse = PixivPagesResponse()

        override suspend fun illustDetail(illustId: Long): PixivIllustResponse = PixivIllustResponse()

        override suspend fun novelMeta(novelId: Long): PixivNovelAjaxResponse = PixivNovelAjaxResponse()

        override suspend fun recommend(illustId: Long, limit: Int): PixivRecommendResponse =
            PixivRecommendResponse()
    }

    private class FakeAppApi : PixivAppApi {
        data class SearchCall(
            val word: String,
            val searchTarget: String?,
            val sort: String?,
            val duration: String?,
            val searchAiType: Int?,
            val filter: String,
            val offset: Int?,
        )

        val searchCalls = mutableListOf<SearchCall>()
        val novelSearchCalls = mutableListOf<SearchCall>()
        val userCalls = mutableListOf<Pair<String, Int?>>()
        val followCalls = mutableListOf<Pair<Long, Boolean>>()
        var searchResponse = PixivIllustsResponse()
        var userResponse = PixivUserPreviewsResponse()

        /** 非空时 searchIllust/searchNovel 抛该异常（模拟会话失效 401） */
        var searchError: HttpException? = null
        var novelSearchError: HttpException? = null

        val autocompleteCalls = mutableListOf<String>()
        var autocompleteResponse = PixivAutoWordsResponse()

        val popularPreviewCalls = mutableListOf<String>()
        var popularPreviewResponse = PixivIllustsResponse()

        /** 非空时 searchPopularPreview 抛该异常（模拟配图链路失效） */
        var popularPreviewError: HttpException? = null

        override suspend fun recommended(
            clientTime: String,
            clientHash: String,
            contentType: String,
            filter: String,
            includeRankingIllusts: Boolean,
            offset: Int?,
        ): PixivIllustsResponse = PixivIllustsResponse()

        override suspend fun followFeed(
            clientTime: String,
            clientHash: String,
            restrict: String,
            filter: String,
            offset: Int?,
        ): PixivIllustsResponse = PixivIllustsResponse()

        override suspend fun illustNew(
            clientTime: String,
            clientHash: String,
            contentType: String,
            filter: String,
            maxIllustId: Long?,
        ): PixivIllustsResponse = PixivIllustsResponse()

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
        ): PixivIllustsResponse {
            searchCalls.add(
                SearchCall(word, searchTarget, sort, duration, searchAiType, filter, offset),
            )
            searchError?.let { throw it }
            return searchResponse
        }

        override suspend fun searchUser(
            clientTime: String,
            clientHash: String,
            word: String,
            filter: String,
            offset: Int?,
        ): PixivUserPreviewsResponse {
            userCalls.add(word to offset)
            return userResponse
        }

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
        ): PixivAutoWordsResponse {
            autocompleteCalls.add(word)
            return autocompleteResponse
        }

        override suspend fun searchPopularPreview(
            clientTime: String,
            clientHash: String,
            word: String,
            searchTarget: String,
            includeTranslatedTagResults: Boolean,
            mergePlainKeywordResults: Boolean,
            filter: String,
        ): PixivIllustsResponse {
            popularPreviewCalls.add(word)
            popularPreviewError?.let { throw it }
            return popularPreviewResponse
        }

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

        override suspend fun novelRecommended(
            clientTime: String,
            clientHash: String,
            filter: String,
            includeRankingLabel: Boolean,
            offset: Int?,
        ): PixivNovelsResponse = PixivNovelsResponse()

        override suspend fun novelFollow(
            clientTime: String,
            clientHash: String,
            restrict: String,
            offset: Int?,
        ): PixivNovelsResponse = PixivNovelsResponse()

        override suspend fun novelNew(
            clientTime: String,
            clientHash: String,
            filter: String,
            maxNovelId: Long?,
        ): PixivNovelsResponse = PixivNovelsResponse()

        override suspend fun novelDetail(
            clientTime: String,
            clientHash: String,
            novelId: Long,
        ): PixivNovelDetailResponse = PixivNovelDetailResponse()

        override suspend fun searchNovel(
            clientTime: String,
            clientHash: String,
            word: String,
            sort: String?,
            searchAiType: Int?,
            filter: String,
            offset: Int?,
        ): PixivNovelsResponse {
            novelSearchCalls.add(
                SearchCall(word, null, sort, null, searchAiType, filter, offset),
            )
            novelSearchError?.let { throw it }
            return PixivNovelsResponse()
        }

        override suspend fun userNovels(
            clientTime: String,
            clientHash: String,
            userId: Long,
            filter: String,
            offset: Int?,
        ): PixivNovelsResponse = PixivNovelsResponse()

        override suspend fun novelWebview(
            clientTime: String,
            clientHash: String,
            novelId: Long,
            viewerVersion: String,
        ): ResponseBody = "".toResponseBody("text/html".toMediaTypeOrNull())


        override suspend fun illustState(
            clientTime: String,
            clientHash: String,
            illustId: Long,
        ): PixivAppIllustDetailResponse = PixivAppIllustDetailResponse()

        override suspend fun illustDetail(
            clientTime: String,
            clientHash: String,
            illustId: Long,
            filter: String,
        ): PixivAppIllustFullResponse = PixivAppIllustFullResponse()

        override suspend fun relatedIllusts(
            clientTime: String,
            clientHash: String,
            illustId: Long,
            filter: String,
        ): PixivIllustsResponse = PixivIllustsResponse()

        override suspend fun followAdd(
            clientTime: String,
            clientHash: String,
            userId: Long,
            restrict: String,
        ): PixivAppActionResponse {
            followCalls.add(userId to true)
            return PixivAppActionResponse()
        }

        override suspend fun followDelete(
            clientTime: String,
            clientHash: String,
            userId: Long,
        ): PixivAppActionResponse {
            followCalls.add(userId to false)
            return PixivAppActionResponse()
        }

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
    }

    private fun repository(
        appApi: FakeAppApi,
        webApi: FakeApi = FakeApi(),
        loggedIn: Boolean = false,
    ): PixivRepository = PixivRepository(
        api = webApi,
        appApi = appApi,
        endpoints = PixivAuthEndpoints(),
        runtime = PixivAuthRuntime(dispatcher = Dispatchers.Unconfined, now = { FIXED_NOW }),
        pixivAuth = if (loggedIn) loggedInPixivAuth() else loggedOutPixivAuth(),
        imageRoute = testImageRoute(),
        quietFollowStore = QuietFollowStore(InMemorySharedPreferences()),
    )

    /** 无测速样本的线路控制器：worthFullImageInline 稳定给 false（速度优先档） */
    private fun testImageRoute(): ImageRouteController = ImageRouteController(
        settings = SettingsRepository(InMemorySharedPreferences()),
        prefs = InMemorySharedPreferences(),
    )

    // 未登录会话：详情与搜索走网页端链路
    private fun loggedOutPixivAuth(): PixivAuthRepository = PixivAuthRepository(
        api = FakePixivAuthApi(),
        store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson),
        endpoints = PixivAuthEndpoints(),
        runtime = PixivAuthRuntime(dispatcher = Dispatchers.Unconfined, now = { FIXED_NOW }),
    )

    // 已登录会话：搜索走 app-api
    private fun loggedInPixivAuth(): PixivAuthRepository {
        val store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson)
        store.save(PixivToken(accessToken = "at", refreshToken = "rt", expiresAt = Long.MAX_VALUE))
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
        appApi: FakeAppApi,
        webApi: FakeApi = FakeApi(),
        loggedIn: Boolean = false,
    ): PixivSearchSource =
        PixivSearchSource(repository(appApi, webApi, loggedIn))

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }

    // ---------------- 声明契约：筛选组与 app-api 枚举一一对应 ----------------

    @Test
    fun filterGroupsMatchApiEnums() {
        val groups = PixivSearchSource.FILTER_GROUPS
        assertEquals(listOf("kind", "sort", "target", "duration"), groups.map { it.id })

        // 类型组在小说档要隐藏期间/对象：接口不支持，藏起来比置灰诚实
        val impl = source(FakeAppApi())
        assertEquals(
            emptySet<String>(),
            impl.hiddenFilterGroups(mapOf(SourceSearch.FILTER_KIND to SourceSearch.KIND_ALL)),
        )
        assertEquals(
            setOf(PixivSearchSource.GROUP_DURATION, PixivSearchSource.GROUP_TARGET),
            impl.hiddenFilterGroups(mapOf(SourceSearch.FILTER_KIND to SourceSearch.KIND_NOVEL)),
        )

        val sort = groups.first { it.id == PixivSearchSource.GROUP_SORT }
        assertEquals(
            listOf("date_desc", "date_asc", "popular_desc"),
            sort.options.map { it.id },
        )
        assertEquals("date_desc", sort.options.single { it.default }.id)

        val target = groups.first { it.id == PixivSearchSource.GROUP_TARGET }
        assertEquals(
            listOf("partial_match_for_tags", "exact_match_for_tags", "title_and_caption"),
            target.options.map { it.id },
        )

        val duration = groups.first { it.id == PixivSearchSource.GROUP_DURATION }
        assertEquals(
            listOf("", "within_last_day", "within_last_week", "within_last_month"),
            duration.options.map { it.id },
        )

        // 每组有且只有一个默认项，空串默认项即"不带参数"
        groups.forEach { group ->
            assertEquals(1, group.options.count { it.default })
        }
        assertEquals(listOf("hide_ai"), PixivSearchSource.FILTER_TOGGLES.map { it.id })
    }

    @Test
    fun pixivSearchDeclaresProportionalCardsAndUserSearch() {
        val impl = source(FakeAppApi())
        assertTrue(impl.proportional)
        assertTrue(impl.supportsUsers)
    }

    @Test
    fun tagModeHidesKindAndTargetGroups() {
        val impl = source(FakeAppApi())

        assertEquals(
            setOf(SourceSearch.FILTER_KIND, PixivSearchSource.GROUP_TARGET),
            impl.hiddenTagFilterGroups(),
        )
    }

    // ---------------- 参数映射：filters → app-api 查询参数 ----------------

    @Test
    fun defaultFiltersOmitOptionalParams() = runTest {
        val appApi = FakeAppApi()
        val impl = source(appApi, loggedIn = true)
        val defaults = mapOf(
            PixivSearchSource.GROUP_SORT to PixivSearchSource.SORT_NEW,
            PixivSearchSource.GROUP_TARGET to PixivSearchSource.TARGET_PARTIAL,
            PixivSearchSource.GROUP_DURATION to PixivSearchSource.DURATION_ALL,
        )

        impl.searchWorks("東方", defaults, page = 0)

        val call = appApi.searchCalls.single()
        assertEquals("東方", call.word)
        assertEquals(PixivSearchSource.TARGET_PARTIAL, call.searchTarget)
        assertEquals(PixivSearchSource.SORT_NEW, call.sort)
        // 空串期间映射为不带参数；AI 开关不选时不带 search_ai_type
        assertNull(call.duration)
        assertNull(call.searchAiType)
        assertEquals(0, call.offset)
    }

    @Test
    fun filtersMapToApiParamsAndOffset() = runTest {
        val appApi = FakeAppApi()
        val impl = source(appApi, loggedIn = true)
        val filters = mapOf(
            PixivSearchSource.GROUP_SORT to PixivSearchSource.SORT_POPULAR,
            PixivSearchSource.GROUP_TARGET to PixivSearchSource.TARGET_TITLE,
            PixivSearchSource.GROUP_DURATION to PixivSearchSource.DURATION_WEEK,
            PixivSearchSource.TOGGLE_HIDE_AI to FILTER_TOGGLE_ON,
        )

        impl.searchWorks("巫女", filters, page = 2)

        val call = appApi.searchCalls.single()
        assertEquals(PixivSearchSource.SORT_POPULAR, call.sort)
        assertEquals(PixivSearchSource.TARGET_TITLE, call.searchTarget)
        assertEquals(PixivSearchSource.DURATION_WEEK, call.duration)
        assertEquals(1, call.searchAiType)
        assertEquals(60, call.offset)
    }

    @Test
    fun tagWorksForceExactMatch() = runTest {
        val appApi = FakeAppApi()
        val impl = source(appApi, loggedIn = true)

        impl.searchTagWorks(
            "東方Project",
            mapOf(
                PixivSearchSource.GROUP_TARGET to PixivSearchSource.TARGET_PARTIAL,
                PixivSearchSource.GROUP_SORT to PixivSearchSource.SORT_NEW,
            ),
            page = 0,
        )

        assertEquals(PixivSearchSource.TARGET_EXACT, appApi.searchCalls.single().searchTarget)
    }


    @Test
    fun anonymousSearchRoutesToWebEndpoint() = runTest {
        val appApi = FakeAppApi()
        val webApi = FakeApi().apply {
            webSearchResponse = PixivSearchResponse(
                body = PixivSearchBody(
                    illustManga = PixivSearchResult(data = listOf(searchItem(id = "1")), lastPage = 10),
                ),
            )
        }

        val page = source(appApi, webApi).searchWorks("東方", emptyMap(), 0).getOrThrow()

        assertEquals(0, appApi.searchCalls.size)
        val call = webApi.webSearchCalls.single()
        assertEquals("東方", call.word)
        assertEquals(1, call.page)
        assertEquals("s_tag", call.sMode)
        assertEquals("date_d", call.order)
        assertEquals(listOf(1L), page.items.map { it.id })
    }

    @Test
    fun anonymousTagSearchUsesExactTagMode() = runTest {
        val webApi = FakeApi()

        source(FakeAppApi(), webApi).searchTagWorks("東方Project", emptyMap(), 0)

        assertEquals("s_tag_exact", webApi.webSearchCalls.single().sMode)
    }

    @Test
    fun anonymousSearchMapsSortAndPopularDegradesToNew() = runTest {
        val webApi = FakeApi()
        val impl = source(FakeAppApi(), webApi)

        impl.searchWorks("x", mapOf(PixivSearchSource.GROUP_SORT to PixivSearchSource.SORT_OLD), 0)
        assertEquals("date_asc", webApi.webSearchCalls.first().order)

        impl.searchWorks("x", mapOf(PixivSearchSource.GROUP_SORT to PixivSearchSource.SORT_POPULAR), 0)
        assertEquals("date_d", webApi.webSearchCalls.last().order)
    }

    @Test
    fun webSearchBeyondLastPageReturnsEmpty() = runTest {
        val webApi = FakeApi().apply {
            webSearchResponse = PixivSearchResponse(
                body = PixivSearchBody(illustManga = PixivSearchResult(data = listOf(searchItem()), lastPage = 10)),
            )
        }
        val impl = source(FakeAppApi(), webApi)

        val last = impl.searchWorks("x", emptyMap(), 9).getOrThrow()
        assertEquals(listOf(1L), last.items.map { it.id })

        val clamped = impl.searchWorks("x", emptyMap(), 10).getOrThrow()
        assertTrue(clamped.items.isEmpty())
    }

    /** 会话失效（401）回落网页端匿名搜索，不拿错误墙挡用户 */
    @Test
    fun expiredSessionFallsBackToWebSearch() = runTest {
        val appApi = FakeAppApi().apply {
            searchError = HttpException(Response.error<Any>(401, "".toResponseBody(null)))
        }
        val webApi = FakeApi().apply {
            webSearchResponse = PixivSearchResponse(
                body = PixivSearchBody(illustManga = PixivSearchResult(data = listOf(searchItem(id = "9")))),
            )
        }

        val page = source(appApi, webApi, loggedIn = true).searchWorks("x", emptyMap(), 0).getOrThrow()

        assertEquals(1, appApi.searchCalls.size)
        assertEquals(listOf(9L), page.items.map { it.id })
    }

    /** 小说检索没有网页端匿名链路：401 归类 LoginRequired，外壳据此弹登录引导而非解析失败 */
    @Test
    fun novelSearchSessionExpiredMapsToLoginRequired() = runTest {
        val appApi = FakeAppApi().apply {
            novelSearchError = HttpException(Response.error<Any>(401, "".toResponseBody(null)))
        }

        val error = source(appApi, loggedIn = true)
            .searchNovels("お嬢", emptyMap(), 0)
            .exceptionOrNull()

        assertTrue(error is AppError.LoginRequired)
    }

    /** 高清档映射：方裁缩略图升级未裁切 master1200，比例/R18/AI 随条目走 */
    @Test
    fun webSearchItemMapsToWorkCard() {
        val work = searchItem(id = "7").toWork(hdThumb = true)!!

        assertEquals(7L, work.id)
        assertEquals(11L, work.authorId)
        assertEquals("t7", work.title)
        assertEquals(1900, work.thumbWidth)
        assertEquals(1080, work.thumbHeight)
        assertEquals(2, work.imageCount)
        assertTrue(work.r18)
        assertTrue(work.ai)
        assertEquals(
            "https://i.pximg.net/img-master/img/2026/10/06/03/18/15/7_p0_master1200.jpg",
            work.thumbnailUrl,
        )
    }

    /** 速度档映射：img-master 项改写成详情同款 540 裁切（垫底/转场可命中），custom-thumb 项原样 */
    @Test
    fun webSearchItemSpeedTierByShape() {
        val imgMaster = searchItem(id = "7")
        val speedWork = imgMaster.toWork(hdThumb = false)!!
        assertEquals(
            "https://i.pximg.net/c/540x540_70/img-master/img/2026/10/06/03/18/15/7_p0_master1200.jpg",
            speedWork.thumbnailUrl,
        )

        val customThumb = searchItem(
            id = "8",
            url = "https://i.pximg.net/c/250x250_80_a2/custom-thumb/img/2026/10/06/03/39/34/8_p0_custom1200.jpg",
        )
        assertEquals(customThumb.url, customThumb.toWork(hdThumb = false)!!.thumbnailUrl)
    }

    @Test
    fun webThumbUpgradeKeepsUnknownShapes() {
        // 无 /c/ 裁剪前缀：原样
        assertEquals(
            "https://i.pximg.net/img-master/img/a/1_p0_master1200.jpg",
            pixivProportionalThumb("https://i.pximg.net/img-master/img/a/1_p0_master1200.jpg"),
        )
        // 有裁剪前缀但后缀不认识：原样（宁可图方不可图裂）
        assertEquals(
            "https://i.pximg.net/c/250x250_80_a2/img-master/img/a/1_p0.jpg",
            pixivProportionalThumb("https://i.pximg.net/c/250x250_80_a2/img-master/img/a/1_p0.jpg"),
        )
        // custom1200（新作品 custom-thumb 形态）同样升级
        assertEquals(
            "https://i.pximg.net/custom-thumb/img/a/2_p0_master1200.jpg",
            pixivProportionalThumb("https://i.pximg.net/c/250x250_80_a2/custom-thumb/img/a/2_p0_custom1200.jpg"),
        )
    }

    /** 小说档走专用端点：不传 target/duration（接口没有这两个参数），排序与 AI 过滤照常 */
    @Test
    fun novelSearchRoutesToNovelEndpoint() = runTest {
        val appApi = FakeAppApi()
        val impl = source(appApi)
        val filters = mapOf(
            SourceSearch.FILTER_KIND to SourceSearch.KIND_NOVEL,
            PixivSearchSource.GROUP_SORT to PixivSearchSource.SORT_NEW,
            PixivSearchSource.GROUP_TARGET to PixivSearchSource.TARGET_TITLE,
            PixivSearchSource.GROUP_DURATION to PixivSearchSource.DURATION_WEEK,
            PixivSearchSource.TOGGLE_HIDE_AI to FILTER_TOGGLE_ON,
        )

        impl.searchNovels("お嬢", filters, page = 1)

        val call = appApi.novelSearchCalls.single()
        assertEquals("お嬢", call.word)
        assertEquals(PixivSearchSource.SORT_NEW, call.sort)
        assertNull(call.searchTarget)
        assertNull(call.duration)
        assertEquals(1, call.searchAiType)
        assertEquals(30, call.offset)
        assertEquals(0, appApi.searchCalls.size)
    }

    // ---------------- R-18 不再本地过滤（下发由账号侧表示设置在服务端管控） ----------------

    @Test
    fun r18ResultsPassThroughUntouched() = runTest {
        val r18 = PixivAppIllust(
            id = "1",
            title = "r18",
            imageUrls = PixivAppImageUrls(large = "https://i.pximg.net/1.jpg"),
            xRestrict = 1,
        )
        val normal = r18.copy(id = "2", xRestrict = 0)

        val appApi = FakeAppApi().apply { searchResponse = PixivIllustsResponse(illusts = listOf(r18, normal)) }

        val items = source(appApi, loggedIn = true).searchWorks("x", emptyMap(), 0).getOrThrow().items
        assertEquals(listOf(1L, 2L), items.map { it.id })
        assertTrue(items.first().r18)
    }

    // ---------------- 用户搜索与关注路由 ----------------

    @Test
    fun searchUsersOffsetsAndMaps() = runTest {
        val appApi = FakeAppApi().apply {
            userResponse = PixivUserPreviewsResponse(
                userPreviews = listOf(
                    previewUser(id = "11", followed = true),
                    previewUser(id = "22", followed = false),
                ),
            )
        }

        val users = source(appApi).searchUsers("hoge", page = 1).getOrThrow()

        assertEquals("hoge" to 30, appApi.userCalls.single())
        assertEquals(listOf(11L, 22L), users.map { it.userId })
        assertTrue(users.first().followed)
        assertFalse(users.last().followed)
    }

    @Test
    fun toggleFollowRoutesToFollowApi() = runTest {
        val appApi = FakeAppApi()
        val impl = source(appApi)

        impl.toggleFollow(userId = 42, follow = true)
        impl.toggleFollow(userId = 42, follow = false)

        assertEquals(listOf(42L to true, 42L to false), appApi.followCalls)
    }

    @Test
    fun userPageOpensInAppProfile() {
        val impl = source(FakeAppApi())

        assertEquals(SourceAuthorOpen.NativeProfile, impl.userPage(FollowUser(77, "n", null)))
    }

    @Test
    fun autocompleteWireFormat() {
        val json = """
            {"tags":[{"name":"東方Project","translated_name":"东方Project"},{"name":"巫女"}],"search_span_limit":10}
        """.trimIndent()

        val response = PikuJson.decodeFromString<PixivAutoWordsResponse>(json)

        assertEquals(2, response.tags.size)
        assertEquals("東方Project", response.tags[0].name)
        assertEquals("东方Project", response.tags[0].translatedName)
        assertNull(response.tags[1].translatedName)
    }

    // ---------------- 标签建议网格：联想名单 + 人气预览代表图 ----------------

    @Test
    fun suggestTagsPairsNamesWithPopularPreviewThumbs() = runTest {
        val appApi = FakeAppApi().apply {
            autocompleteResponse = PixivAutoWordsResponse(
                tags = listOf(
                    PixivAutoTag(name = "東方Project", translatedName = "东方Project"),
                    PixivAutoTag(name = "東方"),
                ),
            )
            popularPreviewResponse = PixivIllustsResponse(
                illusts = listOf(
                    illust(id = "1", tags = listOf("東方", "pixivタグ")),
                    illust(id = "2", tags = listOf("東方Project")),
                ),
            )
        }

        val suggestions = source(appApi, loggedIn = true).suggestTags("東方").getOrThrow()

        assertEquals(listOf("東方"), appApi.autocompleteCalls)
        assertEquals(listOf("東方"), appApi.popularPreviewCalls)
        assertEquals(listOf("東方Project", "東方"), suggestions.map { it.name })
        assertEquals("东方Project", suggestions[0].translatedName)
        assertEquals(square540("2"), suggestions[0].thumbnailUrl)
        assertEquals(square540("1"), suggestions[1].thumbnailUrl)
    }

    /** 人气预览失效不拖垮名单：建议照常返回，卡片回落占位图 */
    @Test
    fun suggestTagsKeepsNamesWhenPopularPreviewFails() = runTest {
        val appApi = FakeAppApi().apply {
            autocompleteResponse = PixivAutoWordsResponse(tags = listOf(PixivAutoTag(name = "東方")))
            popularPreviewError = HttpException(Response.error<Any>(404, "".toResponseBody(null)))
        }

        val suggestions = source(appApi, loggedIn = true).suggestTags("東方").getOrThrow()

        assertEquals(listOf("東方"), suggestions.map { it.name })
        assertNull(suggestions.single().thumbnailUrl)
    }

    /** 输入联想不带图：只发联想请求，不碰人气预览，条目永远无缩略图 */
    @Test
    fun suggestDoesNotFetchPopularPreview() = runTest {
        val appApi = FakeAppApi().apply {
            autocompleteResponse = PixivAutoWordsResponse(tags = listOf(PixivAutoTag(name = "東方")))
            popularPreviewResponse = PixivIllustsResponse(
                illusts = listOf(illust(id = "1", tags = listOf("東方"))),
            )
        }

        val suggestions = source(appApi, loggedIn = true).suggest("東方").getOrThrow()

        assertEquals(listOf("東方"), suggestions.map { it.name })
        assertNull(suggestions.single().thumbnailUrl)
        assertTrue(appApi.popularPreviewCalls.isEmpty())
    }

    /** 代表图按人气序先到先得；tags 全空/无图的作品不参与 */
    @Test
    fun tagThumbnailsFirstSeenWinsInPopularOrder() {
        val map = tagThumbnails(
            listOf(
                illust(id = "1", tags = listOf("東方")),
                illust(id = "2", tags = listOf("東方", "東方Project")),
                illust(id = "3", tags = listOf("東方Project"), squareMedium = "", medium = ""),
            ),
        )

        assertEquals(square540("1"), map["東方"])
        assertEquals(square540("2"), map["東方Project"])
    }

    /** 档位映射：170 方裁升 540 方裁（同文件）；形态不认识退 medium；medium 也空才退原样 */
    @Test
    fun tagThumbTierUpgradesAndFallsBack() {
        assertEquals(square540("1"), pixivTagThumb(square170("1"), master540("1")))
        assertEquals(
            "m.jpg",
            pixivTagThumb("https://i.pximg.net/c/250x250_80_a2/img-master/img/a/1_p0_square1200.jpg", "m.jpg"),
        )
        assertEquals("s.jpg", pixivTagThumb("s.jpg", ""))
    }

    /** wire 形态：popular-preview 响应 illusts[].tags 建映射，端到端钉住字段名与 540 改写 */
    @Test
    fun popularPreviewWireFormat() {
        val json = """
            {"illusts":[{"id":"9527","title":"t",
              "image_urls":{"square_medium":"${square170("9527")}","medium":"m.jpg","large":"l.jpg"},
              "tags":[{"name":"東方Project","translated_name":"东方Project"},{"name":"東方"}]}]}
        """.trimIndent()

        val illusts = PikuJson.decodeFromString<PixivIllustsResponse>(json).illusts

        assertEquals(listOf("東方Project", "東方"), illusts.single().tags.map { it.name })
        val map = tagThumbnails(illusts)
        assertEquals(square540("9527"), map["東方Project"])
        assertEquals(square540("9527"), map["東方"])
    }

    @Test
    fun trendingTagsWireFormat() {
        // illust 为完整作品对象，width/height 来自实测响应（Pixiv-Shaft 内嵌样本同构）
        val json = """
            {"trend_tags":[{"tag":"幻想入","translated_name":"幻想入乡","illust":{"id":"9527","image_urls":{"square_medium":"s.jpg","medium":"m.jpg","large":"l.jpg"},"width":995,"height":1543}}]}
        """.trimIndent()

        val response = PikuJson.decodeFromString<PixivTrendTagsResponse>(json)

        assertEquals(1, response.trendTags.size)
        val tag = response.trendTags[0].toTrendingTag()!!
        assertEquals("幻想入", tag.name)
        assertEquals(995, tag.width)
        assertEquals(1543, tag.height)
        // 瀑布流按原比例排，缩略图必须取未方裁的 large 档
        assertEquals("l.jpg", tag.thumbnailUrl)
    }

    @Test
    fun trendingTagWithoutThumbIsDropped() {
        val tag = PikuJson.decodeFromString<PixivTrendTagsResponse>(
            """{"trend_tags":[{"tag":"notag","illust":{"id":"1"}}]}""",
        ).trendTags.single().toTrendingTag()

        assertNull(tag)
    }

    @Test
    fun trendingTagWithoutSizeFallsBackToSquare() {
        val tag = PikuJson.decodeFromString<PixivTrendTagsResponse>(
            """{"trend_tags":[{"tag":"notag","illust":{"id":"1","image_urls":{"large":"l.jpg"}}}]}""",
        ).trendTags.single().toTrendingTag()!!

        assertEquals("l.jpg", tag.thumbnailUrl)
        assertEquals(0, tag.width)
        assertEquals(0, tag.height)
    }

    @Test
    fun userPreviewsWireFormat() {
        val json = """
            {"user_previews":[
                {"user":{"id":"11","name":"user11","profile_image_urls":{"medium":"a.jpg"},"is_followed":true},"illusts":[]},
                {"user":{"id":"not-a-number"},"illusts":[]}
            ]}
        """.trimIndent()

        val response = PikuJson.decodeFromString<PixivUserPreviewsResponse>(json)

        assertEquals(2, response.userPreviews.size)
        // id 解析不出的条目不成行
        assertEquals(listOf(11L), response.userPreviews.mapNotNull { it.toFollowUser() }.map { it.userId })
    }

    private fun previewUser(id: String, followed: Boolean) =
        PixivUserPreview(
            user = PixivAppUser(
                id = id,
                name = "user$id",
                profileImageUrls = PixivAppProfileImages(medium = "a$id.jpg"),
                isFollowed = followed,
            ),
        )

    private fun searchItem(
        id: String = "1",
        url: String = "https://i.pximg.net/c/250x250_80_a2/img-master/img/2026/10/06/03/18/15/${id}_p0_square1200.jpg",
    ) = PixivSearchItem(
        id = id,
        title = "t$id",
        url = url,
        userId = "11",
        userName = "user$id",
        pageCount = 2,
        xRestrict = 1,
        aiType = 2,
        width = 1900,
        height = 1080,
    )

    private fun square170(id: String) =
        "https://i.pximg.net/c/170x170_90_a2/img-master/img/a/${id}_p0_square1200.jpg"

    private fun square540(id: String) =
        "https://i.pximg.net/c/540x540_70/img-master/img/a/${id}_p0_square1200.jpg"

    private fun master540(id: String) =
        "https://i.pximg.net/c/540x540_70/img-master/img/a/${id}_p0_master1200.jpg"

    private fun illust(
        id: String,
        tags: List<String>,
        squareMedium: String? = null,
        medium: String? = null,
    ) = PixivAppIllust(
        id = id,
        imageUrls = PixivAppImageUrls(
            squareMedium = squareMedium ?: square170(id),
            medium = medium ?: master540(id),
        ),
        tags = tags.map { PixivAppIllustTag(name = it) },
    )
}
