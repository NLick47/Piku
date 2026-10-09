package com.piku.client.ui.theme

import androidx.compose.ui.graphics.Color
import com.piku.client.data.local.NovelReaderSettings
import com.piku.client.data.local.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelReaderThemeTest {

    @Test
    fun everyThemeIsReachableById() {
        val ids = NovelReaderTheme.entries.map { it.id }
        assertEquals("档位 id 必须唯一，否则落盘值会指向两档", ids.size, ids.toSet().size)
        NovelReaderTheme.entries.forEach { entry ->
            assertEquals(entry, NovelReaderTheme.fromId(entry.id))
        }
    }

    /** 落盘值越界（手改过 prefs、装了老版本）时不能让阅读器没有配色 */
    @Test
    fun unknownIdFallsBackToPaper() {
        assertEquals(NovelReaderTheme.Paper, NovelReaderTheme.fromId(99))
        assertEquals(NovelReaderTheme.Paper, NovelReaderTheme.fromId(-1))
    }

    /** 数据层的默认档位与这里的枚举顺序是一份约定：改掉顺序会让默认底色换人 */
    @Test
    fun defaultThemeConstantPointsAtPaper() {
        assertEquals(NovelReaderTheme.Paper, NovelReaderTheme.fromId(SettingsRepository.NOVEL_THEME_DEFAULT))
        assertEquals(NovelReaderTheme.Paper, NovelReaderTheme.fromId(NovelReaderSettings().themeId))
    }

    /** 一键日夜切换：亮底进夜间、暗底回纸黄 */
    @Test
    fun toggleFamilyCrossesDayAndNight() {
        assertEquals(NovelReaderTheme.Ink, NovelReaderTheme.Paper.toggleFamily())
        assertEquals(NovelReaderTheme.Ink, NovelReaderTheme.Green.toggleFamily())
        assertEquals(NovelReaderTheme.Paper, NovelReaderTheme.Ink.toggleFamily())
        assertEquals(NovelReaderTheme.Paper, NovelReaderTheme.Black.toggleFamily())
    }

    /** 控件卡片必须比正文底更亮，否则浮层和正文糊成一片（暗底尤甚） */
    @Test
    fun controlCardStaysLighterThanThePage() {
        NovelReaderTheme.entries.forEach { theme ->
            val page = relativeLuminance(theme.bg)
            val card = relativeLuminance(composite(theme.card, theme.bg))
            assertTrue(
                "${theme.name} 的控件卡片压在正文底上浮不起来：card=$card page=$page",
                card > page,
            )
        }
    }

    /** 正文对比度守 WCAG AA（4.5:1）：正文底色可以随便换，字不能看不清 */
    @Test
    fun bodyTextKeepsReadableContrast() {
        NovelReaderTheme.entries.forEach { theme ->
            val fg = relativeLuminance(theme.fg)
            val bg = relativeLuminance(theme.bg)
            val ratio = (maxOf(fg, bg) + 0.05f) / (minOf(fg, bg) + 0.05f)
            assertTrue("${theme.name} 的正文对比度只有 $ratio", ratio >= 4.5f)
        }
    }

    private fun composite(fg: Color, bg: Color): Color {
        val a = fg.alpha
        return Color(
            red = fg.red * a + bg.red * (1f - a),
            green = fg.green * a + bg.green * (1f - a),
            blue = fg.blue * a + bg.blue * (1f - a),
        )
    }

    private fun relativeLuminance(color: Color): Float =
        0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)

    private fun linear(channel: Float): Float =
        if (channel <= 0.03928f) channel / 12.92f
        else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
}
