package com.piku.client.data.source

import com.piku.client.data.remote.AppendFileResponse
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.ShowIllustDetailResponse
import com.piku.client.domain.source.BackupWork
import com.piku.client.domain.source.SourceLogin
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoipikuContentBackupTest {

    private val calls = mutableListOf<Pair<String, Int?>>()

    private fun api(
        appendHtml: String = APPEND_HTML,
        fullImages: Map<Int, String> = mapOf(-1 to MAIN_FULL, 0 to APPEND_FULL),
        fetchCostMs: Long = 0,
        detailHtml: String = DETAIL_HTML,
    ): PoipikuApi = Proxy.newProxyInstance(
        PoipikuApi::class.java.classLoader,
        arrayOf(PoipikuApi::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getWorkDetail" -> {
                calls += method.name to null
                if (fetchCostMs > 0) Thread.sleep(fetchCostMs)
                detailHtml.toResponseBody("text/html".toMediaTypeOrNull())
            }

            "showAppendFile" -> {
                calls += method.name to null
                AppendFileResponse(result_num = 1, html = appendHtml)
            }

            "showIllustDetail" -> {
                val appendIndex = args?.get(2) as? Int ?: -1
                calls += method.name to appendIndex
                val url = fullImages[appendIndex]
                if (url == null) {
                    ShowIllustDetailResponse(error_code = -1)
                } else {
                    ShowIllustDetailResponse(
                        result = 1,
                        html = "<img class=\"DetailIllustItemImage\" src=\"$url\">",
                        error_code = 0,
                    )
                }
            }

            else -> throw UnsupportedOperationException(method.name)
        }
    } as PoipikuApi

    @Test
    fun multiImageWorkTakesFullImagesAndNovelText() = runTest {
        val backup = PoipikuContentBackup(api(), SourceLogin { true })

        val content = backup.content(BackupWork(workId = "123", authorId = 7L, imageCount = 3))

        assertEquals("主图原图 + 追加图原图", listOf(MAIN_FULL, APPEND_FULL), content?.images)
        assertEquals("正文只在 append 响应里", "标题正文\n第二行", content?.novelText)
        assertTrue("多图要问 append", calls.any { it.first == "showAppendFile" })
    }

    @Test
    fun singleImageWorkSkipsAppendAndCarriesNoNovelText() = runTest {
        val backup = PoipikuContentBackup(api(), SourceLogin { true })

        val content = backup.content(BackupWork(workId = "123", authorId = 7L, imageCount = 1))

        assertEquals(listOf(MAIN_FULL), content?.images)
        assertEquals("", content?.novelText)
        assertFalse("单图不问 append", calls.any { it.first == "showAppendFile" })
    }

    @Test
    fun missingSessionFallsBackToDetailAndAppendImages() = runTest {
        val backup = PoipikuContentBackup(api(), SourceLogin { false })

        val content = backup.content(BackupWork(workId = "123", authorId = 7L, imageCount = 3))

        assertEquals(
            "原图要会话：拿不到就退回详情页 + 追加图那一档",
            listOf(MAIN_THUMB, APPEND_THUMB),
            content?.images,
        )
    }

    @Test
    fun singleImageWorkFallsBackToDetailImageWhenOriginalIsMissing() = runTest {
        val backup = PoipikuContentBackup(api(fullImages = emptyMap()), SourceLogin { true })

        val content = backup.content(BackupWork(workId = "123", authorId = 7L, imageCount = 1))

        assertEquals("原图接口回错就退回详情页那档", listOf(MAIN_THUMB), content?.images)
    }

    @Test
    fun fetchGapIsCountedFromTheEndOfThePreviousFetch() = runTest {
        // 取一次要 100ms（真实耗时）：时钟要落在"取完"，落在"开始"就说明下一次的
        // 礼貌间隔会被这次的耗时吃掉，源站连着收请求
        val backup = PoipikuContentBackup(api(fetchCostMs = 100), SourceLogin { true })

        backup.content(BackupWork(workId = "123", authorId = 7L, imageCount = 1))
        val finishedAt = System.currentTimeMillis()

        assertTrue(
            "礼貌间隔从取完算起，lastFetchedAt=${backup.lastFetchedAt} finishedAt=$finishedAt",
            backup.lastFetchedAt >= finishedAt - 5,
        )
    }

    @Test
    fun textWorkWithOneImageStillGetsItsNovelText() = runTest {
        // 文字作品在列表里的 imageCount 也是 1（关注流解析是 append 数 + 1），详情页没有真图：
        // 正文只存在于 append 响应里，不能因为"单图"就跳过 append —— 否则正文永远备份不出去，
        // 每次同步还会白抓一遍
        val backup = PoipikuContentBackup(api(detailHtml = TEXT_WORK_HTML), SourceLogin { true })

        val content = backup.content(BackupWork(workId = "123", authorId = 7L, imageCount = 1))

        assertTrue("要问 append 才拿得到正文", calls.any { it.first == "showAppendFile" })
        assertEquals("标题正文\n第二行", content?.novelText)
    }

    @Test
    fun nonNumericWorkIdIsSkippedWithoutRequests() = runTest {
        val backup = PoipikuContentBackup(api(), SourceLogin { true })

        assertNull(backup.content(BackupWork(workId = "n123", authorId = 7L, imageCount = 1)))
        assertTrue("取不到的作品不该发请求", calls.isEmpty())
    }

    private companion object {
        const val MAIN_THUMB = "https://cdn.poipiku.com/7/main_640.jpg"
        const val APPEND_THUMB = "https://cdn.poipiku.com/7/append_640.jpg"
        const val MAIN_FULL = "https://cdn.poipiku.com/7/main.png"
        const val APPEND_FULL = "https://cdn.poipiku.com/7/append.png"

        // 文字作品：IllustItem 带 Text 标记、有 NovelSection、没有真图（主图是占位）
        val TEXT_WORK_HTML = """
            <div class="IllustItem  Text">
            <h1 id="IllustItemDesc_1" class="IllustItemDesc">标题</h1>
            <img class="IllustItemThumbImg" src="https://cdn.poipiku.com/img/publish_pass.png_640.jpg" />
            <div class="NovelSection"><span class="NovelTitle">标题</span>正文<br />第二行</div>
            </div>
        """.trimIndent()

        val DETAIL_HTML = """
            <div class="IllustItem  Upload">
            <h1 id="IllustItemDesc_1" class="IllustItemDesc">标题</h1>
            <img class="IllustItemThumbImg" src="$MAIN_THUMB" />
            </div>
        """.trimIndent()

        val APPEND_HTML = """
            <a class="IllustItemThumb"><img class="IllustItemThumbImg" src="$APPEND_THUMB"/></a>
            <div class="NovelSection"><span class="NovelTitle">标题</span>正文<br />第二行</div>
            <a href="javascript:void(0)" onclick="showIllustDetail(7, 123, 0)">追加</a>
        """.trimIndent()
    }
}
