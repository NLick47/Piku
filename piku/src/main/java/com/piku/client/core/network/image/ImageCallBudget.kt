package com.piku.client.core.network.image

import com.piku.client.core.network.http.NetworkTuning

/**
 * 一条图片 call 的时间预算模型。
 *
 * 常规线路按 [NetworkTuning.MANAGED_ROUTE_BUDGET_MS] 主动收紧，比 client 的
 * `callTimeout` 更早让位给原生通道；原生通道用满 [NetworkTuning.IMAGE_CALL_TIMEOUT_MS]
 * 的余量，把地址池逐个试完。这里只放预算，不掺路由状态。
 */
internal object ImageCallBudget {

    /** 直连最坏耗时：DoHDns 会给两条路由，每条各吃一个连接超时 */
    val directWorstMs: Long = NetworkTuning.MAX_ROUTES * NetworkTuning.CONNECT_TIMEOUT_MS

    /** 中继只有一条候选 */
    val relayWorstMs: Long = NetworkTuning.CONNECT_TIMEOUT_MS

    /** 留给原生通道的余量：至少够一次有意义的尝试 */
    const val nativeReserveMs: Long = 4_500L

    /** 原生通道最多用满整条 call 的余量：9 个地址 × 3s，短预算等于只试前几个 */
    const val maxNativeBudgetMs: Long = NetworkTuning.IMAGE_CALL_TIMEOUT_MS - 1_000L

    /** 再少就不值得开一次 TLS 了 */
    const val minNativeBudgetMs: Long = 1_500L

    /**
     * 这条 call 还能给原生通道多少时间。
     *
     * 走原生时没有常规线路在花时间，可以给到 [maxNativeBudgetMs]；
     * 作为兜底时只剩常规线路吃剩下的部分，给多少用多少。
     */
    fun nativeBudgetMs(elapsedMs: Long): Int {
        val remaining = NetworkTuning.IMAGE_CALL_TIMEOUT_MS - elapsedMs - 1_000L
        // 剩下的时间连一次最省的尝试都不够：别再开连接，也别把"没试成"记成线路故障
        if (remaining < minNativeBudgetMs) return 0
        return remaining.coerceAtMost(maxNativeBudgetMs).toInt()
    }

    /** 没有原生可退时仍留一点余量：常规线路撞满 callTimeout 会让取消路径跳过线路状态更新 */
    private const val SAFETY_MARGIN_MS = 1_000L

    /**
     * 已经花了 [elapsedMs] 之后，再起一轮最坏 [nextWorstMs] 的尝试是否还安全。
     *
     * 有原生可退时留足 [nativeReserveMs]，没有也留 [SAFETY_MARGIN_MS]。
     */
    fun hasRoom(elapsedMs: Long, nextWorstMs: Long, keepNativeReserve: Boolean): Boolean {
        val reserve = if (keepNativeReserve) nativeReserveMs else SAFETY_MARGIN_MS
        return elapsedMs + nextWorstMs + reserve <= NetworkTuning.MANAGED_ROUTE_BUDGET_MS
    }
}
