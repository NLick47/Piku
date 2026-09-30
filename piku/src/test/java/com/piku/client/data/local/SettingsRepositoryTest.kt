package com.piku.client.data.local

import com.piku.client.domain.model.FolderSort
import com.piku.client.domain.model.ReadingProgress
import com.piku.client.domain.model.ThemeMode
import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    private fun poipikuWork(workId: String) = WorkKey(WorkSource.POIPIKU, workId)

    private fun pixivWork(workId: String) = WorkKey(WorkSource.PIXIV, workId)

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

        assertEquals("no progress means 0 percent", 0, repo.getNovelProgress(poipikuWork("13367054")))

        repo.setNovelProgress(poipikuWork("13367054"), 42)
        assertEquals(42, repo.getNovelProgress(poipikuWork("13367054")))

        assertEquals("other works have no progress", 0, repo.getNovelProgress(poipikuWork("13368368")))

        val reloaded = SettingsRepository(prefs)
        assertEquals(42, reloaded.getNovelProgress(poipikuWork("13367054")))
    }

    @Test
    fun novelProgressClampsToPercentRange() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        repo.setNovelProgress(poipikuWork("1"), 500)
        assertEquals(100, repo.getNovelProgress(poipikuWork("1")))

        repo.setNovelProgress(poipikuWork("1"), -50)
        assertEquals(0, repo.getNovelProgress(poipikuWork("1")))
    }

    @Test
    fun sameIdOnDifferentSourcesHasSeparateProgress() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        repo.setImageProgress(poipikuWork("123"), 3)
        repo.setImageProgress(pixivWork("123"), 7)

        assertEquals(3, repo.getImageProgress(poipikuWork("123")))
        assertEquals(7, repo.getImageProgress(pixivWork("123")))

        val reloaded = SettingsRepository(prefs)
        assertEquals(3, reloaded.getImageProgress(poipikuWork("123")))
        assertEquals(7, reloaded.getImageProgress(pixivWork("123")))
    }

    @Test
    fun bareWorkIdKeysFromOlderVersionsReadAsPoipiku() {
        // 只有 poipiku 的年代键里没有源段：老用户的进度不能丢
        val prefs = InMemorySharedPreferences()
        prefs.edit().putInt("novel_progress_13367054", 42).apply()
        prefs.edit().putInt("image_progress_13367054", 6).apply()

        val progress = SettingsRepository(prefs).readingProgress.value

        assertEquals(
            ReadingProgress(novelPercent = 42, imagePage = 6),
            progress[poipikuWork("13367054")],
        )
    }

    @Test
    fun legacyKeysAreMigratedSoTheReaderStillResumes() {
        // 小说阅读器/看图器按 (源, id) 取值：老键不搬过去，升级后接着读会从头开始
        val prefs = InMemorySharedPreferences()
        prefs.edit().putInt("novel_progress_13367054", 42).apply()
        prefs.edit().putInt("image_progress_13367054", 6).apply()

        val repo = SettingsRepository(prefs)

        assertEquals("小说的进度要接着上次的地方", 42, repo.getNovelProgress(poipikuWork("13367054")))
        assertEquals("图集要接着上次那一页", 6, repo.getImageProgress(poipikuWork("13367054")))
        assertEquals(
            "老键要搬走，不能和新键并存",
            listOf("image_progress_POIPIKU_13367054", "novel_progress_POIPIKU_13367054"),
            prefs.all.keys.filter { it.startsWith("novel_progress_") || it.startsWith("image_progress_") }.sorted(),
        )
    }

    @Test
    fun newerKeyWinsWhenBothFormatsCoexist() {
        // 升级后写过一次（新键），老键还躺在库里：新键的值不能被老值顶掉
        val prefs = InMemorySharedPreferences()
        prefs.edit().putInt("novel_progress_13367054", 42).apply()
        prefs.edit().putInt("novel_progress_POIPIKU_13367054", 80).apply()

        val repo = SettingsRepository(prefs)

        assertEquals(80, repo.getNovelProgress(poipikuWork("13367054")))
        assertEquals(
            "同一作品只能留一把键",
            listOf("novel_progress_POIPIKU_13367054"),
            prefs.all.keys.filter { it.startsWith("novel_progress_") },
        )
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

        assertEquals(emptyMap<WorkKey, ReadingProgress>(), repo.readingProgress.value)

        // 同一部作品的两条 key（小说百分比 + 图集页码）合成一条进度
        repo.setNovelProgress(poipikuWork("11"), 42)
        repo.setImageProgress(poipikuWork("11"), 3)
        assertEquals(
            ReadingProgress(novelPercent = 42, imagePage = 3),
            repo.readingProgress.value[poipikuWork("11")],
        )

        repo.setImageProgress(poipikuWork("22"), 5)
        assertEquals(ReadingProgress(imagePage = 5), repo.readingProgress.value[poipikuWork("22")])

        // 重启后从 SP 恢复
        val reloaded = SettingsRepository(prefs)
        assertEquals(
            ReadingProgress(novelPercent = 42, imagePage = 3),
            reloaded.readingProgress.value[poipikuWork("11")],
        )
        assertEquals(ReadingProgress(imagePage = 5), reloaded.readingProgress.value[poipikuWork("22")])
    }

    @Test
    fun imageProgressIgnoresNonPositiveAndRepeatedPages() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        // 0 表示没有进度，不该在快照里冒出一个空条目
        repo.setImageProgress(poipikuWork("7"), 0)
        assertFalse(repo.readingProgress.value.containsKey(poipikuWork("7")))

        repo.setImageProgress(poipikuWork("7"), 4)
        assertEquals(4, repo.readingProgress.value[poipikuWork("7")]?.imagePage)

        // 非法页码不覆盖已有进度
        repo.setImageProgress(poipikuWork("7"), -3)
        assertEquals(4, repo.readingProgress.value[poipikuWork("7")]?.imagePage)
    }

    @Test
    fun readingProgressSkipsUnknownSourceKeys() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putInt("novel_progress_AO3_42", 5).apply()
        prefs.edit().putInt("image_progress_POIPIKU_9", 2).apply()

        val repo = SettingsRepository(prefs)

        assertNull("认不出的源整条跳过", repo.readingProgress.value[poipikuWork("42")])
        assertEquals(setOf(poipikuWork("9")), repo.readingProgress.value.keys)
    }
}
