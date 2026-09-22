package com.piku.client.data.local

import com.piku.client.domain.model.FolderSort
import com.piku.client.domain.model.ReadingProgress
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

    @Test
    fun folderSortPersistsAndFallsBackOnUnknownValue() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        assertEquals(FolderSort.ADDED, repo.folderSort.value)

        repo.setFolderSort(FolderSort.AUTHOR)
        assertEquals(FolderSort.AUTHOR, repo.folderSort.value)
        assertEquals(FolderSort.AUTHOR, SettingsRepository(prefs).folderSort.value)

        // 存了不认识的名字（降级安装等）：退回默认而不是崩在枚举解析上
        prefs.edit().putString("folder_sort", "NOT_A_SORT").apply()
        assertEquals(FolderSort.ADDED, SettingsRepository(prefs).folderSort.value)
    }

    @Test
    fun readingProgressMergesBothKindsAndPublishes() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        assertEquals(emptyMap<Long, ReadingProgress>(), repo.readingProgress.value)

        // 同一部作品的两条 key（小说百分比 + 图集页码）合成一条进度
        repo.setNovelProgress(11L, 42)
        repo.setImageProgress(11L, 3)
        assertEquals(ReadingProgress(novelPercent = 42, imagePage = 3), repo.readingProgress.value[11L])

        repo.setImageProgress(22L, 5)
        assertEquals(ReadingProgress(imagePage = 5), repo.readingProgress.value[22L])

        // 重启后从 SP 恢复
        val reloaded = SettingsRepository(prefs)
        assertEquals(ReadingProgress(novelPercent = 42, imagePage = 3), reloaded.readingProgress.value[11L])
        assertEquals(ReadingProgress(imagePage = 5), reloaded.readingProgress.value[22L])
    }

    @Test
    fun imageProgressIgnoresNonPositiveAndRepeatedPages() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        // 0 表示没有进度，不该在快照里冒出一个空条目
        repo.setImageProgress(7L, 0)
        assertFalse(repo.readingProgress.value.containsKey(7L))

        repo.setImageProgress(7L, 4)
        assertEquals(4, repo.readingProgress.value[7L]?.imagePage)

        // 非法页码不覆盖已有进度
        repo.setImageProgress(7L, -3)
        assertEquals(4, repo.readingProgress.value[7L]?.imagePage)
    }

    @Test
    fun readingProgressSkipsKeysThatAreNotWorkIds() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putInt("novel_progress_not_a_number", 5).apply()
        prefs.edit().putInt("image_progress_9", 2).apply()

        val repo = SettingsRepository(prefs)

        assertEquals(setOf(9L), repo.readingProgress.value.keys)
    }
}
