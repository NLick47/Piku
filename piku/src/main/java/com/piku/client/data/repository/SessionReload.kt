package com.piku.client.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 观察会话版本：**调用时**就记下当前值，之后只在版本真的变了时执行 [block]。
 *
 * 不能用 `drop(1)`：StateFlow 会合并快速变化，订阅协程真正开跑时拿到的可能是
 * 调用之后才发生的版本，`drop(1)` 会把"调用时点之后的那次变化"当成初始值丢掉，
 * 页面就停在旧数据上而且不会自愈。
 */
fun CoroutineScope.reloadOnSessionChange(
    version: StateFlow<Long>,
    block: suspend () -> Unit,
): Job {
    val seenAtSubscribe = version.value
    return launch {
        var seen = seenAtSubscribe
        version.collect { current ->
            if (current == seen) return@collect
            seen = current
            block()
        }
    }
}
