package com.piku.client.source.ao3.data.source

import com.piku.client.core.source.BackupWork
import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.repository.Ao3Repository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3ContentBackupTest {

    private class FakeAo3Api(private val html: String) : Ao3Api {
        val fullWorkIds = mutableListOf<Long>()
        val plainWorkIds = mutableListOf<Long>()

        override suspend fun getWork(id: Long, adult: Boolean): ResponseBody {
            plainWorkIds += id
            return html.toResponseBody("text/html".toMediaType())
        }

        override suspend fun getWorkFull(
            id: Long,
            fullWork: Boolean,
            adult: Boolean,
        ): ResponseBody {
            fullWorkIds += id
            return html.toResponseBody("text/html".toMediaType())
        }

        override suspend fun searchWorks(
            query: String?,
            scope: Map<String, String>,
            sortColumn: String,
            sortDirection: String,
            page: Int,
        ): ResponseBody = error("备份不该走列表接口")

        override suspend fun searchTags(
            name: String,
            type: String,
            status: String,
            sortColumn: String,
            sortDirection: String,
        ): ResponseBody = error("备份不该走标签接口")

        override suspend fun getTagWorks(tag: String, page: Int): ResponseBody =
            error("备份不该走标签作品接口")

        override suspend fun getUserWorks(user: String, page: Int): ResponseBody =
            error("备份不该走用户作品接口")
    }

    /** 全文页的两章：章节容器 + userstuff.module[role=article]，与站点结构一致 */
    private val fullWorkHtml = """
        <h2 class="title heading">两章的作品</h2>
        <a rel="author" href="/users/writer/pseuds/writer">writer</a>
        <div class="chapter" id="chapter-1">
          <h3 class="title">Chapter 1</h3>
          <div class="userstuff module" role="article">
            <h3 class="landmark heading">Chapter Text</h3>
            <p>第一章正文。</p>
          </div>
        </div>
        <div class="chapter" id="chapter-2">
          <h3 class="title">Chapter 2</h3>
          <div class="userstuff module" role="article">
            <p>第二章正文。</p>
          </div>
        </div>
    """.trimIndent()

    @Test
    fun backupUploadsWholeTextFromFullWorkPage() = runBlocking {
        val api = FakeAo3Api(fullWorkHtml)
        val content = Ao3ContentBackup(Ao3Repository(api))
            .content(BackupWork(workId = "n42", authorId = 0L, imageCount = 0))

        assertEquals("第一章正文。\n\n第二章正文。", content?.novelText)
        assertTrue(content!!.images.isEmpty())
        // 键里的 n 前缀要解回站内 id，且正文走全文页
        assertEquals(listOf(42L), api.fullWorkIds)
        assertTrue("首章页只有元信息与第一章", api.plainWorkIds.isEmpty())
    }

    /** AO3 的小说键一律带 n 前缀；没带说明根本不该走这条备份路 */
    @Test
    fun backupKeyMustCarryNovelPrefix() {
        assertEquals(42L, ao3WorkIdFromBackupKey("n42"))
        assertNull(ao3WorkIdFromBackupKey("42"))
    }
}
