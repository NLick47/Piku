package com.piku.client.data.source

import android.util.Log
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.WorkDetailParser
import com.piku.client.data.repository.ThumbnailResolver
import com.piku.client.domain.model.WorkDetail
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.BackupWork
import com.piku.client.domain.source.ContentBackup
import com.piku.client.domain.source.SourceContentBackup
import com.piku.client.domain.source.SourceAuthRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PoipikuContentBackup @Inject constructor(
    private val poipikuApi: PoipikuApi,
    private val authRegistry: SourceAuthRegistry,
) : SourceContentBackup {

    override val source = WorkSource.POIPIKU

    /**
     * 上一次取内容的**结束**时刻：礼貌间隔从这里算起。从开始算等于把这次请求的耗时
     * 也当成间隔，源站看来就是连着来。internal 给单测核时钟落点。
     */
    internal var lastFetchedAt = 0L
        private set
    private val gapRandom = Random(System.nanoTime())

    override suspend fun content(work: BackupWork): ContentBackup? {
        val workId = work.workId.toLongOrNull() ?: return null
        pace()
        return try {
            val html = poipikuApi.getWorkDetail(work.authorId, workId).string()
            val detail = WorkDetailParser.parse(html)
            if (detail.passwordProtected && detail.imageUrls.isEmpty() && detail.novelText.isBlank()) {
                return null
            }
            fetchContent(work.authorId, workId, work.imageCount, detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "content failed for ${work.authorId}/$workId", e)
            null
        } finally {
            lastFetchedAt = System.currentTimeMillis()
        }
    }

    private suspend fun fetchContent(
        authorId: Long,
        workId: Long,
        imageCount: Int,
        detail: WorkDetail,
    ): ContentBackup =
        // 真正只有一张图的作品才走这条快路：详情页一张真图都没有说明是文字作品
        // （列表给它的 imageCount 也是 1）或门后作品，正文只在 append 响应里，必须去问
        if (imageCount <= 1 && detail.imageUrls.isNotEmpty()) {
            ContentBackup(images = fetchMainFullImage(authorId, workId).ifEmpty { detail.imageUrls })
        } else {
            delay(APPEND_FILE_GAP_MS)
            val appendResp = poipikuApi.showAppendFile(authorId, workId, "", 0, -1)
            val appendUrls = if (appendResp.result_num > 0) {
                WorkDetailParser.extractImageUrls(appendResp.html)
            } else {
                emptyList()
            }
            val fullUrls = fetchFullImageUrls(authorId, workId, appendResp.html)
            ContentBackup(
                // 原图取不到（未登录/受限）时退回详情页与追加图合并后的那一档
                images = fullUrls.ifEmpty {
                    ThumbnailResolver.mergeWorkImages(detail.imageUrls, appendUrls)
                },
                novelText = WorkDetailParser.extractNovelText(appendResp.html),
            )
        }

    private suspend fun fetchMainFullImage(authorId: Long, workId: Long): List<String> {
        delay(ILLUST_DETAIL_GAP_MS)
        val resp = runCatching { poipikuApi.showIllustDetail(authorId, workId, -1, "") }.getOrNull()
        if (resp == null || resp.error_code != 0) {
            Log.d(TAG, "fetchMainFullImage failed: error_code=${resp?.error_code} work=$authorId/$workId")
            return emptyList()
        }
        return WorkDetailParser.extractFullImageUrls(resp.html)
    }

    private suspend fun fetchFullImageUrls(
        authorId: Long,
        workId: Long,
        appendHtml: String,
    ): List<String> {
        if (!authRegistry.isLoggedIn(WorkSource.POIPIKU)) return emptyList()

        val fullUrls = mutableListOf<String>()
        fullUrls.addAll(fetchMainFullImage(authorId, workId))

        val ads = runCatching { WorkDetailParser.extractAppendAds(appendHtml) }.getOrDefault(emptyList())
        for (ad in ads) {
            delay(ILLUST_DETAIL_GAP_MS)
            val resp = runCatching { poipikuApi.showIllustDetail(authorId, workId, ad, "") }.getOrNull()
            if (resp != null && resp.error_code == 0) {
                WorkDetailParser.extractFullImageUrls(resp.html).firstOrNull()?.let { fullUrls.add(it) }
            }
        }
        return fullUrls
    }

    /** 距上次取完不足 1.2~3.0 秒就等够；本进程第一次取不等 */
    private suspend fun pace() {
        val wait = lastFetchedAt + FETCH_GAP_MIN_MS + gapRandom.nextLong(FETCH_GAP_JITTER_MS) -
            System.currentTimeMillis()
        if (wait > 0) delay(wait)
    }

    private companion object {
        const val TAG = "PoipikuBackup"

        /** 抓取 poipiku 详情页之间的固定间隔基数 */
        const val FETCH_GAP_MIN_MS = 1_200L

        /** 叠加的随机抖动上限，与基数合计 1.2~3.0 秒 */
        const val FETCH_GAP_JITTER_MS = 1_800L

        /** 同域连续请求（详情页 → showAppendFile）之间的最小间隔 */
        const val APPEND_FILE_GAP_MS = 800L

        /** showIllustDetail 请求之间的最小间隔 */
        const val ILLUST_DETAIL_GAP_MS = 800L
    }
}
