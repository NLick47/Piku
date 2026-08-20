package com.piku.client.data.local

import com.piku.client.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun defaultsOnEmptyPrefs() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        assertFalse(repo.showAdultContent.value)
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.value)
        assertEquals(0, repo.historyRetentionDays.value)
    }

    @Test
    fun setterUpdatesFlowAndPersists() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        repo.setShowAdultContent(true)
        repo.setThemeMode(ThemeMode.DARK)
        repo.setHistoryRetentionDays(30)

        assertTrue(repo.showAdultContent.value)
        assertEquals(ThemeMode.DARK, repo.themeMode.value)
        assertEquals(30, repo.historyRetentionDays.value)

        // 重建仓库（模拟重启）：值应从 SP 恢复
        val reloaded = SettingsRepository(prefs)
        assertTrue(reloaded.showAdultContent.value)
        assertEquals(ThemeMode.DARK, reloaded.themeMode.value)
        assertEquals(30, reloaded.historyRetentionDays.value)
    }

    @Test
    fun invalidThemeNameFallsBackToSystem() {
        val prefs = InMemorySharedPreferences().apply {
            edit().putString("theme_mode", "NOT_A_THEME").apply()
        }

        val repo = SettingsRepository(prefs)

        assertEquals(ThemeMode.SYSTEM, repo.themeMode.value)
    }

    @Test
    fun overwriteLatestWins() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        repo.setThemeMode(ThemeMode.LIGHT)
        repo.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repo.themeMode.value)
    }

    @Test
    fun novelProgressIsPerWorkAndPersists() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        assertEquals("no progress means 0 percent", 0, repo.getNovelProgress(13367054L))

        repo.setNovelProgress(13367054L, 42)
        assertEquals(42, repo.getNovelProgress(13367054L))

        assertEquals("other works have no progress", 0, repo.getNovelProgress(13368368L))

        val reloaded = SettingsRepository(prefs)
        assertEquals(42, reloaded.getNovelProgress(13367054L))
    }

    @Test
    fun novelProgressClampsToPercentRange() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        repo.setNovelProgress(1L, 500)
        assertEquals(100, repo.getNovelProgress(1L))

        repo.setNovelProgress(1L, -50)
        assertEquals(0, repo.getNovelProgress(1L))
    }
}
