package com.piku.client.data.source

import com.piku.client.data.auth.PixivAuthEndpoints
import com.piku.client.data.auth.PixivAuthApi
import com.piku.client.data.auth.PixivAuthRepository
import com.piku.client.data.auth.PixivAuthRuntime
import com.piku.client.data.auth.PixivAuthStore
import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.QuietFollowStore
import com.piku.client.data.local.SettingsRepository
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
import com.piku.client.data.remote.pixiv.PixivAppImageUrls
import com.piku.client.data.remote.pixiv.PixivAppProfileImages
import com.piku.client.data.remote.pixiv.PixivAppUser
import com.piku.client.data.remote.pixiv.PixivAutoWordsResponse
import com.piku.client.data.remote.pixiv.PixivIllustResponse
import com.piku.client.data.remote.pixiv.PixivIllustsResponse
import com.piku.client.data.remote.pixiv.PixivNovelDetailResponse
import com.piku.client.data.remote.pixiv.PixivNovelsResponse
import com.piku.client.data.remote.pixiv.PixivPagesResponse
import com.piku.client.data.remote.pixiv.PixivRankingResponse
import com.piku.client.data.remote.pixiv.PixivRecommendResponse
import com.piku.client.data.remote.pixiv.PixivTrendTagsResponse
import com.piku.client.data.remote.pixiv.PixivUserDetailResponse
import com.piku.client.data.remote.pixiv.PixivUserPreview
import com.piku.client.data.remote.pixiv.PixivUserPreviewsResponse
import com.piku.client.data.repository.PixivRepository
import com.piku.client.data.repository.toFollowUser
import com.piku.client.data.repository.toTrendingTag
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

class PixivSearchSourceTest {

    private class FakeApi : PixivApi {
        override suspend fun ranking(mode: String, content: String, page: Int, format: String): PixivRankingResponse =
            PixivRankingResponse()

        override suspend fun illustPages(illustId: Long): PixivPagesResponse = PixivPagesResponse()

        override suspend fun illustDetail(illustId: Long): PixivIllustResponse = PixivIllustResponse()

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
        ): PixivAutoWordsResponse = PixivAutoWordsResponse()

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

    private fun repository(appApi: FakeAppApi): PixivRepository = PixivRepository(
        api = FakeApi(),
        appApi = appApi,
        endpoints = PixivAuthEndpoints(),
        runtime = PixivAuthRuntime(dispatcher = Dispatchers.Unconfined, now = { FIXED_NOW }),
        pixivAuth = loggedOutPixivAuth(),
        quietFollowStore = QuietFollowStore(InMemorySharedPreferences()),
    )

    // 未登录会话：详情走网页端链路，与本测试改前的行为一致
    private fun loggedOutPixivAuth(): PixivAuthRepository = PixivAuthRepository(
        api = FakePixivAuthApi(),
        store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson),
        endpoints = PixivAuthEndpoints(),
        runtime = PixivAuthRuntime(dispatcher = Dispatchers.Unconfined, now = { FIXED_NOW }),
    )

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

    private fun source(appApi: FakeAppApi): PixivSearchSource =
        PixivSearchSource(repository(appApi))

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

    // ---------------- 参数映射：filters → app-api 查询参数 ----------------

    @Test
    fun defaultFiltersOmitOptionalParams() = runTest {
        val appApi = FakeAppApi()
        val impl = source(appApi)
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
        val impl = source(appApi)
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
        assertEquals(0, call.searchAiType)
        assertEquals(60, call.offset)
    }

    @Test
    fun tagWorksForceExactMatch() = runTest {
        val appApi = FakeAppApi()
        val impl = source(appApi)

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
        assertEquals(0, call.searchAiType)
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

        val items = source(appApi).searchWorks("x", emptyMap(), 0).getOrThrow().items
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
}
