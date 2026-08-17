package com.piku.client.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTagRepositoryTest {

    @Test
    fun addNormalizesAndTrims() {
        val repo = CustomTagRepository(InMemorySharedPreferences())

        assertTrue(repo.addCustomTag(" # 星野 "))
        assertEquals(listOf("星野"), repo.customTags.value)
    }

    @Test
    fun addDeduplicates() {
        val repo = CustomTagRepository(InMemorySharedPreferences())
        repo.addCustomTag("星野")

        assertFalse(repo.addCustomTag("星野"))
        assertEquals(listOf("星野"), repo.customTags.value)
    }

    @Test
    fun newestAddedFirst() {
        val repo = CustomTagRepository(InMemorySharedPreferences())

        repo.addCustomTag("a")
        repo.addCustomTag("b")
        repo.addCustomTag("c")

        assertEquals(listOf("c", "b", "a"), repo.customTags.value)
    }

    @Test
    fun blankTagIgnored() {
        val repo = CustomTagRepository(InMemorySharedPreferences())

        assertFalse(repo.addCustomTag("   "))
        assertTrue(repo.customTags.value.isEmpty())
    }

    @Test
    fun onlyFirstHashPrefixStripped() {
        val repo = CustomTagRepository(InMemorySharedPreferences())

        // 与原实现一致：只去掉一个 # 前缀，"##" 归一化为 "#"
        assertTrue(repo.addCustomTag("##"))
        assertEquals(listOf("#"), repo.customTags.value)
    }

    @Test
    fun removeDeletesTag() {
        val repo = CustomTagRepository(InMemorySharedPreferences())
        repo.addCustomTag("a")
        repo.addCustomTag("b")

        repo.removeCustomTag("a")
        repo.removeCustomTag("not-exist")

        assertEquals(listOf("b"), repo.customTags.value)
    }

    @Test
    fun persistsAcrossRecreation() {
        val prefs = InMemorySharedPreferences()
        val repo = CustomTagRepository(prefs)
        repo.addCustomTag("a")
        repo.addCustomTag("b")

        // 重建仓库（模拟重启）：标签应从 SP 恢复且保持顺序
        val reloaded = CustomTagRepository(prefs)
        assertEquals(listOf("b", "a"), reloaded.customTags.value)
    }

    @Test
    fun corruptedJsonYieldsEmptyList() {
        val prefs = InMemorySharedPreferences().apply {
            edit().putString("custom_tags", "not-json{{").apply()
        }

        val repo = CustomTagRepository(prefs)

        assertTrue(repo.customTags.value.isEmpty())
    }
}
