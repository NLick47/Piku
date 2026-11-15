package com.piku.client.core.network.image

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
