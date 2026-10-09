package com.piku.client.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.piku.client.R

/**
 * 小说阅读器配色。正文底色独立于 app 主题，由用户在阅读器里选并持久化（[id] 落盘，顺序即色点顺序）。
 * 每个主题自带正文底/字、控件卡片、进度强调色；轨道与描边由 [fg] 派生，避免每档都手填一遍。
 */
enum class NovelReaderTheme(
    val id: Int,
    /** 亮底深字，决定状态栏图标取反方向 */
    val light: Boolean,
    val bg: Color,
    val fg: Color,
    /** 浮动控件卡片底：亮底主题用比正文更白的暖白，暗底主题必须比正文更亮才能浮起来 */
    val card: Color,
    val accent: Color,
    @get:StringRes val labelRes: Int,
) {
    Paper(0, true, Color(0xFFF3EEDA), Color(0xFF2E2A23), Color(0xF2FCF8EF), Color(0xFFB08A52), R.string.detail_novel_theme_paper),
    Plain(1, true, Color(0xFFF7F5F1), Color(0xFF2B2926), Color(0xF2FFFFFF), Color(0xFFB08A52), R.string.detail_novel_theme_plain),
    Green(2, true, Color(0xFFD3E7D4), Color(0xFF22301E), Color(0xF2F4FBF4), Color(0xFF4F7D57), R.string.detail_novel_theme_green),
    Ink(3, false, ViewerBackgroundDark, Color(0xFFD6D0C4), Color(0xF23A3834), ControlAccentDark, R.string.detail_novel_theme_ink),
    Black(4, false, Color(0xFF000000), Color(0xFFB5B1A9), Color(0xF2292724), ControlAccentDark, R.string.detail_novel_theme_black);

    /** 进度条轨道、控件描边 */
    val track: Color get() = fg.copy(alpha = if (light) 0.14f else 0.24f)
    val border: Color get() = if (light) SoftBorderLight else SoftBorderDark
    /** 正文链接色：沿用站点控件强调色 */
    val link: Color get() = if (light) ControlAccentLight else ControlAccentDark

    /** 一键日夜切换：亮底切到 [Ink]，暗底切回 [Paper]，五个色点仍由设置面板选 */
    fun toggleFamily(): NovelReaderTheme = if (light) Ink else Paper

    companion object {
        /** 落盘值越界（手改、回滚安装）时回落到纸黄，别让阅读器没有配色 */
        fun fromId(id: Int): NovelReaderTheme = entries.firstOrNull { it.id == id } ?: Paper
    }
}
