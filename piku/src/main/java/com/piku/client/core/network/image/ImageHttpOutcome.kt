package com.piku.client.core.network.image

import java.net.SocketException
import javax.net.ssl.SSLException

/** 一次 HTTP 应答对这次取图的含义：只有 [ROUTE_TROUBLE] 才证明线路不通 */
internal enum class ImageHttpOutcome {
    SUCCESS,
    OBJECT_REJECTED,
    ROUTE_TROUBLE,
    REDIRECT,
}

internal fun classifyImageStatus(code: Int): ImageHttpOutcome = when {
    code in 200..299 -> ImageHttpOutcome.SUCCESS
    code in 300..399 -> ImageHttpOutcome.REDIRECT
    code == 408 || code == 429 || code >= 500 -> ImageHttpOutcome.ROUTE_TROUBLE
    else -> ImageHttpOutcome.OBJECT_REJECTED
}

/**
 * 失败链里有「握手被重置」的痕迹：TCP 通了但明文 SNI 被看破（防火墙注入 RST 的典型样子）。
 * 这种失败恰恰证明带点 SNI 的原生通道对症；connect 超时/拒绝是 IP 层不通，换 SNI 没用。
 */
internal fun isSniBlockedLike(cause: Throwable?): Boolean =
    generateSequence(cause) { it.cause }.any {
        (it is SocketException && it.message?.contains("reset", ignoreCase = true) == true) ||
            it is SSLException
    }
