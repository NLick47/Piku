package com.piku.client.data.remote

/**
 * 按**实测速率**决定走哪条路：够快就直连（中继省着点用），明显慢才用中继。
 *
 * 只统计 ≥ [MIN_SAMPLE_BYTES] 的取图：小缩略图几十毫秒就回来了，耗时几乎全是
 * 往返延迟，用它算带宽会把"慢"算成"快"。样本超过 [SAMPLE_WINDOW_MS] 就不算数——
 * 换了网络/位置之后，旧结论不该继续生效。
 */
internal class RouteSpeed(
    private val now: () -> Long,
    private val minBytes: Long = MIN_SAMPLE_BYTES,
    private val windowMs: Long = SAMPLE_WINDOW_MS,
    private val capacity: Int = MAX_SAMPLES,
) {

    data class Sample(val relay: Boolean, val bytes: Long, val elapsedMs: Long, val at: Long) {
        val bytesPerSec: Long get() = if (elapsedMs <= 0) 0 else bytes * 1000 / elapsedMs
    }

    private val samples = ArrayDeque<Sample>()

    /** 图片加载是并发的：record 在完成回调线程、rate 在取图决策线程，必须互斥 */
    @Synchronized
    fun record(relay: Boolean, bytes: Long, elapsedMs: Long) {
        if (bytes < minBytes || elapsedMs <= 0) return
        samples.addLast(Sample(relay, bytes, elapsedMs, now()))
        while (samples.size > capacity) samples.removeFirst()
    }

    /** 该线路最近样本的加权速率（字节/秒）；没样本返回 null */
    @Synchronized
    fun rate(relay: Boolean): Long? {
        val fresh = samples.filter { it.relay == relay && now() - it.at <= windowMs }
        if (fresh.isEmpty()) return null
        val bytes = fresh.sumOf { it.bytes }
        val ms = fresh.sumOf { it.elapsedMs }
        return if (ms <= 0) null else bytes * 1000 / ms
    }

    /** 这批字节按该线路当前速率估算的耗时；没样本返回 null */
    fun estimatedMs(bytes: Long, relay: Boolean): Long? {
        val rate = rate(relay) ?: return null
        return if (rate <= 0) null else bytes * 1000 / rate
    }

    /** 诊断用 */
    @Synchronized
    fun recent(): List<Sample> = samples.toList()

    companion object {
        const val MIN_SAMPLE_BYTES = 150L * 1024
        const val SAMPLE_WINDOW_MS = 30 * 60_000L
        const val MAX_SAMPLES = 8

        /** 达到这个速率就算"直连够用"，再走中继是浪费 */
        const val FAST_ENOUGH_BYTES_PER_SEC = 150L * 1024

        /** 清晰档（pixiv master1200）的典型体积：内联要不要直取按它估算 */
        const val FULL_IMAGE_BYTES_ESTIMATE = 1024L * 1024

        /** 内联图区升清晰档的预算：预计落地超过这个时间就不值当，先停在打底档 */
        const val FULL_IMAGE_BUDGET_MS = 2_000L
    }
}
