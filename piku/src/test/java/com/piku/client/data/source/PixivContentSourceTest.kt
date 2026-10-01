package com.piku.client.data.source

import com.piku.client.data.auth.PixivAuthEndpoints
import com.piku.client.data.auth.PixivAuthRuntime
import com.piku.client.data.auth.pixivClientHash
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivAppActionResponse
import com.piku.client.data.remote.pixiv.PixivAppApi
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.remote.pixiv.PixivAppIllust
import com.piku.client.data.remote.pixiv.PixivAppIllustDetailResponse
import com.piku.client.data.remote.pixiv.PixivAppImageUrls
import com.piku.client.data.remote.pixiv.PixivAutoWordsResponse
import com.piku.client.data.remote.pixiv.PixivContentType
import com.piku.client.data.remote.pixiv.PixivIllustsResponse
import com.piku.client.data.remote.pixiv.PixivRankingItem
import com.piku.client.data.remote.pixiv.PixivTrendTagsResponse
import com.piku.client.data.remote.pixiv.PixivUserPreviewsResponse
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.data.remote.pixiv.PixivIllustResponse
import com.piku.client.data.remote.pixiv.PixivPage
import com.piku.client.data.remote.pixiv.PixivPageUrls
import com.piku.client.data.remote.pixiv.PixivPagesResponse
import com.piku.client.data.remote.pixiv.PixivRankingResponse
import com.piku.client.data.remote.pixiv.PixivRecommendResponse
import com.piku.client.data.repository.PixivRepository
import com.piku.client.data.repository.pixivNewFeedCursor
import com.piku.client.data.repository.pixivTotalPages
import com.piku.client.data.repository.toWork
import com.piku.client.domain.source.SourceFacetStyle
import com.piku.client.domain.source.SourceAuthorOpen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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

        override suspend fun ranking(mode: String, content: String, page: Int, format: String): PixivRankingResponse {
            calls.add(Triple(mode, content, page))
            failure?.let { throw it }
            return response
        }

        override suspend fun illustPages(illustId: Long): PixivPagesResponse = pagesResponse

        var detailResponse = PixivIllustResponse()
        override suspend fun illustDetail(illustId: Long): PixivIllustResponse = detailResponse

        var recommendResponse = PixivRecommendResponse()
        override suspend fun recommend(illustId: Long, limit: Int): PixivRecommendResponse = recommendResponse
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

        override suspend fun trendingTags(
            clientTime: String,
            clientHash: String,
            filter: String,
        ): PixivTrendTagsResponse = PixivTrendTagsResponse()
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

    private fun repository(api: FakeApi, appApi: FakeAppApi = FakeAppApi()): PixivRepository =
        PixivRepository(
            api = api,
            appApi = appApi,
            endpoints = PixivAuthEndpoints(),
            runtime = PixivAuthRuntime(dispatcher = Dispatchers.Unconfined, now = { FIXED_NOW }),
        )

    private fun source(api: FakeApi, appApi: FakeAppApi = FakeAppApi()): PixivContentSource =
        PixivContentSource(repository(api, appApi))

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
            detailResponse = com.piku.client.data.remote.pixiv.PixivIllustResponse(
                body = com.piku.client.data.remote.pixiv.PixivIllustBody(
                    description = "第一行&amp;A<br />第二行&lt;B&gt;&#39;s",
                    tags = com.piku.client.data.remote.pixiv.PixivIllustTags(
                        tags = listOf(
                            com.piku.client.data.remote.pixiv.PixivTag("漫画"),
                            com.piku.client.data.remote.pixiv.PixivTag(""),
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
        // 默认公开关注；private（悄悄关注）另有入口，这里不该发
        assertTrue(app.followCalls.all { it.second == PixivAppConfig.RESTRICT_PUBLIC })
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

    /** 作者区出站到 pixiv 用户页：与详情页作者行的去向一致 */
    @Test
    fun authorPageOpensPixivUserInBrowser() {
        val open = source(FakeApi()).authorPage(work(7))

        assertEquals(SourceAuthorOpen.External("https://www.pixiv.net/users/7"), open)
    }

    /** 声明形态：四条流 = 三个登录门 + 榜单名次流；榜单带周期/内容两组维度，新着另有内容档 */
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
        assertTrue("周期菜单项都要带更新节奏提示", period.options.all { it.hintRes != null })

        val content = PixivContentSource.FACETS.first { it.id == PixivContentSource.GROUP_CONTENT }
        assertEquals(SourceFacetStyle.Dropdown, content.style)
        assertEquals(PixivContentSource.FEED_RANKING, content.feedId)
        assertEquals(listOf("all", "illust", "manga"), content.options.map { it.id })

        val latestContent = PixivContentSource.FACETS.first { it.id == PixivContentSource.GROUP_LATEST_CONTENT }
        assertEquals(SourceFacetStyle.Dropdown, latestContent.style)
        assertEquals(PixivContentSource.FEED_LATEST, latestContent.feedId)
        assertEquals(listOf("illust", "manga"), latestContent.options.map { it.id })
        assertEquals("illust", latestContent.options.first { it.selectedByDefault }.id)
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
