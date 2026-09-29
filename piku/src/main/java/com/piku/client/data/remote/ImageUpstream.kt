package com.piku.client.data.remote

enum class ImageUpstream(
    val host: String,
    val relayPrefix: String,
    /**
     * 直连探测用的图。
     * poipiku 用图床自己的小图标；pixiv 特意用 **master1200 大图**——小缩略图在这条线上
     * 也秒回，只有大图才代表"直连够不够用"，探测结论必须反映真正出问题的那个场景。
     */
    val probePath: String,
    /** 探测要不要真读 body：pixiv 的结论取决于大图传不传得完，只看响应头会永远判"可用" */
    val probeWithBody: Boolean = false,
) {
    POIPIKU("cdn.poipoiku.com", "", "/assets/img/poipiku_icon_512x512_2.png"),
    PIXIV(
        "i.pximg.net",
        "/pximg",
        "/img-master/img/2026/09/27/00/43/35/150148598_p0_master1200.jpg",
        probeWithBody = true,
    ),
    ;

    /** 直连探测地址（中转探测由 [relayPath] 换前缀） */
    val probeUrl: String get() = "https://$host$probePath"

    /** 中继改写后的路径：`/a/b.png` → `/pximg/a/b.png` */
    fun relayPath(encodedPath: String): String = relayPrefix + encodedPath

    companion object {
        fun of(host: String): ImageUpstream? = entries.firstOrNull { it.host == host }

        /** 是不是受管上游；不是（含中继自己）就不改写 */
        fun isUpstreamHost(host: String): Boolean = of(host) != null
    }
}
