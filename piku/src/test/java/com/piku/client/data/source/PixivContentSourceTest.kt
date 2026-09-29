package com.piku.client.data.source

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivContentType
import com.piku.client.data.remote.pixiv.PixivRankingItem
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.data.remote.pixiv.PixivIllustResponse
import com.piku.client.data.remote.pixiv.PixivPage
import com.piku.client.data.remote.pixiv.PixivPageUrls
import com.piku.client.data.remote.pixiv.PixivPagesResponse
import com.piku.client.data.remote.pixiv.PixivRankingResponse
import com.piku.client.data.repository.PixivRepository
import com.piku.client.data.repository.pixivTotalPages
import com.piku.client.data.repository.toWork
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

    private fun source(api: FakeApi): PixivContentSource =
        PixivContentSource(PixivRepository(api), SettingsRepository(InMemorySharedPreferences()))

    /** 0 起页在取页时翻译成接口的 1 起页 */
    @Test
    fun pageTranslatesZeroBasedPageToOneBasedApi() = runTest {
        val api = FakeApi()
        source(api).page("daily", "all", 2)

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

        val page = source(api).page("daily", "all", 0).getOrThrow()

        val work = page.items.single()
        assertEquals(7L, work.id)
        assertEquals(70L, work.authorId)
        assertEquals("author7", work.authorName)
        assertEquals("https://i.pximg.net/7.jpg", work.thumbnailUrl)
        assertEquals(3, work.imageCount)
        assertTrue(work.r18.not())
        assertEquals(-1, work.categoryCd)
    }

    /** R-18 在源里过滤，跟随成人内容开关；开关关着时 sexual>0 的条目不得出现 */
    @Test
    fun r18EntriesHiddenWhenAdultContentDisabled() = runTest {
        val prefs = InMemorySharedPreferences()
        val settings = SettingsRepository(prefs)
        val api = FakeApi().apply {
            response = PixivRankingResponse(
                contents = listOf(item(1), item(2, sexual = 1), item(3, sexual = 2)),
            )
        }

        val hidden = PixivContentSource(PixivRepository(api), settings)
            .page("daily", "all", 0).getOrThrow()
        assertEquals(listOf(1L), hidden.items.map { it.id })

        settings.setShowAdultContent(true)
        val shown = PixivContentSource(PixivRepository(api), settings)
            .page("daily", "all", 0).getOrThrow()
        assertEquals(listOf(1L, 2L, 3L), shown.items.map { it.id })
    }

    /** 看图页走详情取页接口，取 regular（master1200）；缺 regular 才落 original */
    @Test
    fun workPagesTakeRegularUrlsFromPagesEndpoint() = runTest {
        val api = FakeApi().apply {
            pagesResponse = PixivPagesResponse(
                body = listOf(
                    PixivPage(
                        width = 1200,
                        height = 900,
                        urls = PixivPageUrls(
                            regular = "https://i.pximg.net/img-master/a_p0_master1200.jpg",
                            original = "https://i.pximg.net/img-original/a_p0.png",
                        ),
                    ),
                    PixivPage(urls = PixivPageUrls(regular = "", original = "https://i.pximg.net/img-original/b_p1.png")),
                ),
            )
        }
        val src = source(api)

        val pages = src.workPages(work(7)).getOrThrow()

        assertEquals(
            listOf(
                "https://i.pximg.net/img-master/a_p0_master1200.jpg",
                "https://i.pximg.net/img-original/b_p1.png",
            ),
            pages.map { it.url },
        )
        // 尺寸随页返回：详情页图区按它定高
        assertEquals(1200, pages[0].width)
        assertEquals(900, pages[0].height)
        // 原图随页返回：查看器按 poipiku 同款「缩略打底 + 原图覆盖」
        assertEquals("https://i.pximg.net/img-original/a_p0.png", pages[0].fullUrl)
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

        val beyond = src.page("daily", "all", 10).getOrThrow()
        assertTrue(beyond.items.isEmpty())

        val first = src.page("daily", "all", 0)
        assertTrue(first.isFailure)
    }

    @Test
    fun totalPagesDerivedFromRankTotal() {
        assertEquals(11, com.piku.client.data.repository.pixivTotalPages(502))
        assertEquals(1, com.piku.client.data.repository.pixivTotalPages(50))
        assertNull(com.piku.client.data.repository.pixivTotalPages(0))
    }
}
