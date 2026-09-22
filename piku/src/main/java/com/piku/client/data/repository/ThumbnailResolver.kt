package com.piku.client.data.repository

import android.content.SharedPreferences
import android.os.SystemClock
import com.piku.client.domain.model.Work
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailResolver @Inject constructor(
    private val prefs: SharedPreferences,
) {

    private val thumbCache = ConcurrentHashMap<String, String>()
    private val appendLock = Mutex()
    private val lastAppendAt = AtomicLong(0L)

    /** 缩略图回填完成事件，供列表页局部更新对应卡片（详情页打开后返回列表立即生效） */
    private val _thumbUpdated = MutableSharedFlow<Work>(extraBufferCapacity = 16)
    val thumbUpdated: SharedFlow<Work> = _thumbUpdated.asSharedFlow()

    init {
        prefs.getStringSet(KEY_THUMBS, emptySet())?.forEach { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0) thumbCache[entry.substring(0, idx)] = entry.substring(idx + 1)
        }
    }

    /**
     * 详情页解析到真实缩略图后回填列表缓存，返回列表时立即生效。
     *
     * 调用方只在列表当前缩略图是占位图/空图时调用（判定见 [backfillThumbnailUrl]）：
     * 列表已能显示真实图的卡片不该被替换，否则卡片换图并重新解码。
     */
    fun rememberThumb(work: Work, url: String): String {
        val key = workKey(work)
        val thumb = thumbUrl(url)
        thumbCache[key] = thumb
        persistThumb(key, thumb)
        _thumbUpdated.tryEmit(work.copy(thumbnailUrl = thumb))
        return thumb
    }

    /**
     * 已回填的真实缩略图（_360，稳定不过期），无则 null。
     *
     * 只对"列表里看不到内容"的作品有值（登录墙/关注墙/密码/R-18/警告/文字作品）：这些
     * 作品的历史/收藏记录用它补上真实图；列表本来就有真实缩略图的作品直接用详情页首图。
     */
    fun thumbFor(work: Work): String? = thumbCache[workKey(work)]

    /**
     * 全局限速：距上次 append 不足 APPEND_INTERVAL_MS 则等待，防服务端限流。原子更新，可并发调用。
     *
     * [force] = true 时（用户主动输入密码解锁等显式操作）不受限速等待，立即放行，
     * 但仍记录本次调用时间，保证后续后台请求的限速计数不丢。
     */
    suspend fun throttleAppend(force: Boolean = false) {
        if (force) {
            lastAppendAt.set(SystemClock.elapsedRealtime())
            return
        }
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val next = lastAppendAt.get() + APPEND_INTERVAL_MS
            if (now >= next) {
                if (lastAppendAt.compareAndSet(next - APPEND_INTERVAL_MS, now)) return
            } else {
                delay(next - now)
            }
        }
    }

    private fun workKey(work: Work): String = "${work.authorId}/${work.id}"

    private fun thumbUrl(url: String): String =
        url.replace("_640.jpg", "_360.jpg")

    private fun persistThumb(key: String, url: String) {
        prefs.edit().putStringSet(
            KEY_THUMBS,
            thumbCache.map { "${it.key}=${it.value}" }.toSet(),
        ).apply()
    }

    companion object {
        const val APPEND_INTERVAL_MS = 12_000L
        const val RESULT_LOGIN_REQUIRED = -3
        private const val KEY_THUMBS = "work_thumb_urls"

        /** 是否为网站占位图（登录墙/关注墙/密码/R-18/警告/文字作品 logo），真实图 URL 均为作品文件路径 */
        fun isPlaceholderImage(url: String): Boolean =
            url.contains("/img/publish_follower") ||
                url.contains("/img/publish_login") ||
                url.contains("/img/publish_pass") ||
                url.contains("/img/warning") ||
                url.contains("/img/R-18") ||
                url.contains("/assets/img/poipiku_icon")

        /**
         * 列表卡片当前的缩略图是否"看不到内容"：空串或占位图（登录墙/关注墙/密码/R-18/警告）。
         *
         * 只有这种卡片该被详情页解析到的真实图替换。真实缩略图一律保留：append 返回的是
         * 第 2 张起的追加图，替换后卡片换成作品的另一张图；URL 一变 Coil 还要重新解码，
         * 返回列表时先灰白再出图（多图作品必现，单图作品无从触发）。
         *
         * 生产端（[backfillThumbnailUrl]）与消费端（列表回填）共用这一条判定：详情页可能是
         * 深链/正文链接进来的，那时它并不知道列表缩略图是什么，只有列表自己判断才作数。
         */
        fun needsThumbnailBackfill(currentThumbnailUrl: String): Boolean =
            currentThumbnailUrl.isBlank() || isPlaceholderImage(currentThumbnailUrl)

        /**
         * 详情页解析到真实图后，列表缩略图该回填成哪个 URL；null = 不回填。
         *
         * [imageUrls] 按作品内顺序给出候选（详情页主图在前、追加图在后）。只有列表当前是
         * 占位图/空图才回填（判定见 [needsThumbnailBackfill]）：这类作品在列表里本来就看不到
         * 内容，点开详情页拿到真实图后才值得替换。
         *
         * 取第一张真实图——回填的必须是作品首图，不能随手拿一张追加图（深链/正文链接进来时
         * 列表缩略图未知，走的正是这条路）。
         */
        fun backfillThumbnailUrl(currentThumbnailUrl: String, imageUrls: List<String>): String? {
            if (!needsThumbnailBackfill(currentThumbnailUrl)) return null
            return imageUrls.firstOrNull { !isPlaceholderImage(it) }
        }

        /**
         * 合并详情页主图与追加图为完整图片列表：
         * - 过滤占位图（sign in/R-18 等），只保留真实图
         * - 去重且保持顺序（主图在前）
         * - 若过滤后为空（未登录/不可见），回退原主图列表，保证图区不空白
         */
        fun mergeWorkImages(detailImages: List<String>, appendImages: List<String>): List<String> {
            val real = (detailImages + appendImages)
                .filterNot { isPlaceholderImage(it) }
                .distinct()
            return if (real.isEmpty()) detailImages.distinct() else real
        }
    }
}