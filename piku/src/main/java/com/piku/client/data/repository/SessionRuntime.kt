package com.piku.client.data.repository

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 会话状态的运行环境：所有会话变更都跑在 [dispatcher] 这条单线程线上，时间从 [now] 取。
 * 抽出来是为了能注入——JVM 单测里 elapsedRealtime 恒为 0，不换时钟就无法触发第二次重登，
 * 「瞬态失败不淘汰、确定性失败三次淘汰」这条链路根本测不到。
 */
class SessionRuntime(
    val dispatcher: CoroutineDispatcher,
    val now: () -> Long,
)
