package com.piku.client.data.remote

import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTuningTest {

    @Test
    fun routeBudgetStaysWithinImageCallTimeout() {
        // 候选数 × 连接超时是"一次请求在换 IP 上最多花多久"的上界，
        // 越过图片 client 的总超时，换 IP 会在试完之前被砍掉
        val budget = NetworkTuning.MAX_ROUTES * NetworkTuning.CONNECT_TIMEOUT_MS

        assertTrue(
            "候选预算 ${budget}ms 已越过图片总超时 ${NetworkTuning.IMAGE_CALL_TIMEOUT_MS}ms",
            budget <= NetworkTuning.IMAGE_CALL_TIMEOUT_MS,
        )
    }
}
