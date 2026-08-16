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

    /** 详情页解析到真实缩略图后回填列表缓存，返回列表时立即生效 */
    fun rememberThumb(work: Work, url: String): String {
        val key = workKey(work)
        val thumb = thumbUrl(url)
        thumbCache[key] = thumb
        persistThumb(key, thumb)
        _thumbUpdated.tryEmit(work.copy(thumbnailUrl = thumb))
        return thumb
    }

    /** 已回填的真实缩略图（_360，稳定不过期），无则 null。历史/收藏记录优先用它 */
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

        /** 是否为网站占位图（登录墙/R-18/警告），真实图 URL 均为作品文件路径 */
        fun isPlaceholderImage(url: String): Boolean =
            url.contains("/img/publish_login") ||
                url.contains("/img/publish_pass") ||
                url.contains("/img/warning") ||
                url.contains("/img/R-18")

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