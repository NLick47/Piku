package com.piku.client.data.local

import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTagRepositoryTest {

    private fun newRepo(): Pair<CustomTagRepository, InMemorySharedPreferences> {
        val prefs = InMemorySharedPreferences()
        return CustomTagRepository(prefs) to prefs
    }

    @Test
    fun addNormalizesAndTrims() {
        val (repo, _) = newRepo()

        assertTrue(repo.addCustomTag(WorkSource.POIPIKU, " # 星野 "))
        assertEquals(listOf("星野"), repo.tags(WorkSource.POIPIKU).value)
    }

    @Test
    fun addDeduplicates() {
        val (repo, _) = newRepo()
        repo.addCustomTag(WorkSource.POIPIKU, "星野")

        assertFalse(repo.addCustomTag(WorkSource.POIPIKU, "星野"))
        assertEquals(listOf("星野"), repo.tags(WorkSource.POIPIKU).value)
    }

    @Test
    fun newestAddedFirst() {
        val (repo, _) = newRepo()

        repo.addCustomTag(WorkSource.POIPIKU, "a")
        repo.addCustomTag(WorkSource.POIPIKU, "b")
        repo.addCustomTag(WorkSource.POIPIKU, "c")

        assertEquals(listOf("c", "b", "a"), repo.tags(WorkSource.POIPIKU).value)
    }

    @Test
    fun blankTagIgnored() {
        val (repo, _) = newRepo()

        assertFalse(repo.addCustomTag(WorkSource.POIPIKU, "   "))
        assertTrue(repo.tags(WorkSource.POIPIKU).value.isEmpty())
    }

    @Test
    fun allHashPrefixesStripped() {
        val (repo, _) = newRepo()

        // 站点作者分类标签写作 "##東方"（叠加的 #），前导 # 全部去掉后才是标签名；
        // 只剩 # 的输入视为空，不入库
        assertTrue(repo.addCustomTag(WorkSource.POIPIKU, "##東方"))
        assertEquals(listOf("東方"), repo.tags(WorkSource.POIPIKU).value)
        assertFalse(repo.addCustomTag(WorkSource.POIPIKU, "##"))
    }

    @Test
    fun removeDeletesTag() {
        val (repo, _) = newRepo()
        repo.addCustomTag(WorkSource.POIPIKU, "a")
        repo.addCustomTag(WorkSource.POIPIKU, "b")

        repo.removeCustomTag(WorkSource.POIPIKU, "a")
        repo.removeCustomTag(WorkSource.POIPIKU, "not-exist")

        assertEquals(listOf("b"), repo.tags(WorkSource.POIPIKU).value)
    }

    @Test
    fun sourcesAreIsolated() {
        val (repo, _) = newRepo()

        // 同一个标签名在两个源是两条独立记录：第二次添加不算重复，删其中一条不动另一条
        assertTrue(repo.addCustomTag(WorkSource.POIPIKU, "東方"))
        assertTrue(repo.addCustomTag(WorkSource.PIXIV, "東方"))
        repo.removeCustomTag(WorkSource.PIXIV, "東方")

        assertEquals(listOf("東方"), repo.tags(WorkSource.POIPIKU).value)
        assertTrue(repo.tags(WorkSource.PIXIV).value.isEmpty())
    }

    @Test
    fun persistsAcrossRecreation() {
        val prefs = InMemorySharedPreferences()
        val repo = CustomTagRepository(prefs)
        repo.addCustomTag(WorkSource.POIPIKU, "a")
        repo.addCustomTag(WorkSource.POIPIKU, "b")
        repo.addCustomTag(WorkSource.PIXIV, "c")

        // 重建仓库（模拟重启）：各源的标签应从 SP 恢复且保持顺序
        val reloaded = CustomTagRepository(prefs)
        assertEquals(listOf("b", "a"), reloaded.tags(WorkSource.POIPIKU).value)
        assertEquals(listOf("c"), reloaded.tags(WorkSource.PIXIV).value)
    }

    @Test
    fun legacyGlobalListMigratesToPoipiku() {
        val prefs = InMemorySharedPreferences().apply {
            edit().putString("custom_tags", """["東方","初音ミク"]""").apply()
        }

        val repo = CustomTagRepository(prefs)

        assertEquals(listOf("東方", "初音ミク"), repo.tags(WorkSource.POIPIKU).value)
        assertTrue(repo.tags(WorkSource.PIXIV).value.isEmpty())
        // 旧键搬完即删，避免下次启动再迁一遍、把之后的增删覆盖成老内容
        assertNull(prefs.getString("custom_tags", null))
    }

    @Test
    fun corruptedLegacyJsonYieldsEmptyTags() {
        val prefs = InMemorySharedPreferences().apply {
            edit().putString("custom_tags", "not-json{{").apply()
        }

        val repo = CustomTagRepository(prefs)

        assertTrue(repo.tags(WorkSource.POIPIKU).value.isEmpty())
        assertTrue(repo.tags(WorkSource.PIXIV).value.isEmpty())
    }
}
