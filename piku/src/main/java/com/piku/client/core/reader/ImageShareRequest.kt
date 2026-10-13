package com.piku.client.core.reader

import android.net.Uri

/** 一次性的图片分享请求：详情壳发出，UI 消费后即清空 */
data class ImageShareRequest(
    val uri: Uri,
    val targetPackage: String?,
    val shareText: String,
)
