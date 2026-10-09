package com.piku.client.data.local

/**
 * 小说阅读器的显示设置。整包读写：新增一档设置只需加字段，不必再给每个宿主铺一条 StateFlow。
 * [themeId] 是 UI 层配色枚举的 id（[com.piku.client.ui.theme.NovelReaderTheme]），
 * 这里只当数字存取，避免数据层依赖 Compose。
 */
data class NovelReaderSettings(
    val fontSize: Float = SettingsRepository.NOVEL_FONT_DEFAULT,
    val lineHeight: Float = SettingsRepository.NOVEL_LINE_HEIGHT_DEFAULT,
    val themeId: Int = SettingsRepository.NOVEL_THEME_DEFAULT,
    /** 正文用衬线体（宋体/明朝体），默认跟随系统无衬线 */
    val serif: Boolean = false,
    /** 阅读时的屏幕亮度（0~1）；[BRIGHTNESS_SYSTEM] 表示跟随系统 */
    val brightness: Float = BRIGHTNESS_SYSTEM,
    val keepScreenOn: Boolean = false,
) {
    companion object {
        const val BRIGHTNESS_SYSTEM = -1f
    }
}
