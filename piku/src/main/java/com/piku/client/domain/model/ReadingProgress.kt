package com.piku.client.domain.model

/**
 * 一部作品的阅读进度。小说按百分比、图集按页码分别记，两者互不干扰
 * （同一部作品只会有其中一种）。
 *
 * 0 表示没有进度。图集页码 1 起：只读了第 1 页不算"读到过"，
 * 因为打开详情页点第一张图是常规操作，不该被当成读过。
 */
data class ReadingProgress(
    val novelPercent: Int = 0,
    val imagePage: Int = 0,
) {
    val hasNovel: Boolean get() = novelPercent > 0
    val hasImage: Boolean get() = imagePage > 0

    val isEmpty: Boolean get() = !hasNovel && !hasImage
}
