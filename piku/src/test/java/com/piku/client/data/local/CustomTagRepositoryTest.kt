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
    fun allHashPrefixesStripped() {
        val repo = CustomTagRepository(InMemorySharedPreferences())

        // 站点作者分类标签写作 "##東方"（叠加的 #），前导 # 全部去掉后才是标签名；
        // 只剩 # 的输入视为空，不入库
        assertTrue(repo.addCustomTag("##東方"))
        assertEquals(listOf("東方"), repo.customTags.value)
        assertFalse(repo.addCustomTag("##"))
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
