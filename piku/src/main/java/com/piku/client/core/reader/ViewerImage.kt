package com.piku.client.core.reader

/** 查看器单页图片：缩略图常驻打底，原图就绪后替换；hdUrl 是更高清档，给了才出 HD 按钮 */
data class ViewerImage(
    val thumbnailUrl: String,
    val fullUrl: String?,
    val hdUrl: String? = null,
)
