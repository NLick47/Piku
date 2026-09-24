package com.piku.client.data.remote

object MySettingPageParser {

    /** 头像：`... PreviewImg" src="https://cdn.poipiku.com/{uid}/profile_xxx.jpeg"` */
    private val PREVIEW_IMG = Regex("""PreviewImg" src="([^"]+)""")

    /**
     * 「已带尺寸后缀」判定，命中就原样用，否则补 [SMALL_SUFFIX]。
     *
     * 只认站点实际在用的尺寸，不能写成 `_\d+\.(ext)`：设置页给的是带时间戳的
     * `profile_20260816063418.jpeg`，那样会被误判成"已带尺寸"而原样使用，
     * 结果头像下的是 289935 字节的原图而不是 6525 字节的 120px 图。
     * 列表页 22 个头像 URL 全是 `<原图>_120.jpg` 形态，说明这个变体普遍存在；
     * 不存在的变体 CDN 回 403（不是 404），所以判定要保守
     */
    private val SIZE_SUFFIX = Regex("""_(120|360|640)\.(jpg|jpeg|png|webp)$""")

    fun parseAvatarUrl(html: String): String? =
        PREVIEW_IMG.find(html)?.groupValues?.get(1)?.let { url ->
            if (SIZE_SUFFIX.containsMatchIn(url)) url else url + SMALL_SUFFIX
        }

    private const val SMALL_SUFFIX = "_120.jpg"
}
