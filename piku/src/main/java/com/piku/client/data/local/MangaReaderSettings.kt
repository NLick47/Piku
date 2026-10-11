package com.piku.client.data.local

/**
 * 漫画阅读器的显示设置。整包读写，与 [NovelReaderSettings] 同构。
 * 阅读形态与翻页方向是"读这本时想要什么"的偏好，跨作品共用一份。
 */
data class MangaReaderSettings(
    /** 竖向连续滚动（长图与条漫）；false = 一屏一页横向翻页 */
    val scrollMode: Boolean = false,
    /** 右→左翻页（日漫），同时左右点按热区互换 */
    val rightToLeft: Boolean = true,
    /** 长条图铺满宽度边看边滚，而不是缩进整页；仅影响翻页模式 */
    val fillWidth: Boolean = false,
    /** 滚动模式下页与页之间留缝 */
    val pageGap: Boolean = true,
    /** 阅读时的屏幕亮度（0~1）；[BRIGHTNESS_SYSTEM] 表示跟随系统 */
    val brightness: Float = BRIGHTNESS_SYSTEM,
    val keepScreenOn: Boolean = true,
    /** 音量键翻页 */
    val volumeFlip: Boolean = false,
) {
    companion object {
        const val BRIGHTNESS_SYSTEM = -1f
    }
}
