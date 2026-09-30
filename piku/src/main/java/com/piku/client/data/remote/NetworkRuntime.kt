package com.piku.client.data.remote

class NetworkRuntime(
    val now: () -> Long = System::currentTimeMillis,
    /**
     * 单调钟（毫秒）：专供耗时测量与间隔比较，NTP 同步/手动改时不会让它跳变。
     * 数值只有相对意义，不能当时间戳展示——展示一律用 [now]。
     */
    val monotonicNow: () -> Long = { System.nanoTime() / 1_000_000 },
    val sleeper: (Long) -> Unit = { millis ->
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    },
)
