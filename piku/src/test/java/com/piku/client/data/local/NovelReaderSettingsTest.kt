package com.piku.client.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelReaderSettingsTest {

    @Test
    fun defaultsFollowTheReaderDefaults() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        val settings = repo.novelReaderSettings.value
        assertEquals(SettingsRepository.NOVEL_FONT_DEFAULT, settings.fontSize, 1e-4f)
        assertEquals(SettingsRepository.NOVEL_LINE_HEIGHT_DEFAULT, settings.lineHeight, 1e-4f)
        assertEquals(SettingsRepository.NOVEL_THEME_DEFAULT, settings.themeId)
        assertFalse(settings.serif)
        assertEquals(NovelReaderSettings.BRIGHTNESS_SYSTEM, settings.brightness, 1e-4f)
        assertFalse(settings.keepScreenOn)
    }

    @Test
    fun settingsPersistAcrossRestart() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        repo.setNovelReaderSettings(
            NovelReaderSettings(
                fontSize = 20f,
                lineHeight = 2.1f,
                themeId = 2,
                serif = true,
                brightness = 0.4f,
                keepScreenOn = true,
            ),
        )

        val reloaded = SettingsRepository(prefs).novelReaderSettings.value
        assertEquals(20f, reloaded.fontSize, 1e-4f)
        assertEquals(2.1f, reloaded.lineHeight, 1e-4f)
        assertEquals(2, reloaded.themeId)
        assertTrue(reloaded.serif)
        assertEquals(0.4f, reloaded.brightness, 1e-4f)
        assertTrue(reloaded.keepScreenOn)
    }

    /** 老 key 的残值不再被读取：档位只看主题键 */
    @Test
    fun legacyLightBooleanIsIgnored() {
        val prefs = InMemorySharedPreferences().apply {
            edit().putBoolean("novel_reader_light", false).apply()
        }

        assertEquals(
            SettingsRepository.NOVEL_THEME_DEFAULT,
            SettingsRepository(prefs).novelReaderSettings.value.themeId,
        )
    }

    @Test
    fun outOfRangeValuesAreClampedOnWrite() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        repo.setNovelReaderSettings(
            NovelReaderSettings(
                fontSize = 99f,
                lineHeight = 9f,
                brightness = 0.001f,
            ),
        )
        val high = repo.novelReaderSettings.value
        assertEquals(SettingsRepository.NOVEL_FONT_MAX, high.fontSize, 1e-4f)
        assertEquals(SettingsRepository.NOVEL_LINE_HEIGHT_MAX, high.lineHeight, 1e-4f)
        assertEquals(SettingsRepository.NOVEL_BRIGHTNESS_MIN, high.brightness, 1e-4f)

        repo.setNovelReaderSettings(
            NovelReaderSettings(
                fontSize = 1f,
                lineHeight = 0.2f,
                brightness = -8f,
            ),
        )
        val low = repo.novelReaderSettings.value
        assertEquals(SettingsRepository.NOVEL_FONT_MIN, low.fontSize, 1e-4f)
        assertEquals(SettingsRepository.NOVEL_LINE_HEIGHT_MIN, low.lineHeight, 1e-4f)
        assertEquals(
            "负亮度是跟随系统这个状态值，不能被夹到 0",
            NovelReaderSettings.BRIGHTNESS_SYSTEM,
            low.brightness,
            1e-4f,
        )
    }

    /** 滑块拖动只更新内存：一路 apply 会把整段拖动变成几十次落盘 */
    @Test
    fun previewUpdatesMemoryWithoutTouchingDisk() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)
        repo.setNovelReaderSettings(NovelReaderSettings(fontSize = 16f, themeId = 0))

        repo.setNovelReaderSettings(NovelReaderSettings(fontSize = 19f, themeId = 1), persist = false)

        assertEquals(19f, repo.novelReaderSettings.value.fontSize, 1e-4f)
        assertEquals(1, repo.novelReaderSettings.value.themeId)
        val reloaded = SettingsRepository(prefs).novelReaderSettings.value
        assertEquals("预览不该写盘", SettingsRepository.NOVEL_FONT_DEFAULT, reloaded.fontSize, 1e-4f)
        assertEquals(SettingsRepository.NOVEL_THEME_DEFAULT, reloaded.themeId)
    }

    /** 预览之后再松手落盘，落的是最后一次预览的值 */
    @Test
    fun commitAfterPreviewPersistsThePreviewedValue() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        repo.setNovelReaderSettings(NovelReaderSettings(lineHeight = 2.0f), persist = false)
        repo.setNovelReaderSettings(NovelReaderSettings(lineHeight = 2.0f))

        assertEquals(2.0f, SettingsRepository(prefs).novelReaderSettings.value.lineHeight, 1e-4f)
    }
}
