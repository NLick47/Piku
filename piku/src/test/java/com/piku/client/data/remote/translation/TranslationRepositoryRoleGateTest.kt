package com.piku.client.data.remote.translation

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.TranslationDao
import com.piku.client.data.local.TranslationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRepositoryRoleGateTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private fun model(
        id: String,
        vararg roles: String,
        apiKey: String? = "key",
        available: Boolean = true,
    ) = ModelEntry(
        id = id,
        label = id,
        baseUrl = "https://example.com/v1",
        model = "model-$id",
        available = available,
        apiKey = apiKey,
        roles = roles.toList(),
    )

    private fun repoOf(
        models: List<ModelEntry>,
        roles: Map<String, String> = emptyMap(),
    ): TranslationRepository {
        val settings = SettingsRepository(InMemorySharedPreferences())
        settings.saveCatalogCache(
            json.encodeToString(
                ModelCatalogDto(
                    version = 1,
                    defaults = CatalogDefaults(roles = roles),
                    models = models,
                ),
            ),
            url = "test://catalog",
            version = 1,
        )
        val catalog = ModelCatalogRepository(settings, OkHttpClient(), json)
        assertEquals("目录注入失败，后续断言无意义", models.size, catalog.models.value.size)
        return TranslationRepository(
            FakeTranslationDao,
            settings,
            catalog,
            TranslationEngineFactory(NoopChatApi, settings),
        )
    }

    @Test
    fun novelOnlyCatalogOpensTheNovelGateAndNeverTheTextGate() {
        val repo = repoOf(listOf(model("novel-a", Role.NOVEL)))
        assertTrue(repo.hasNovelModel())
        assertFalse("顶栏/短字段不得借正文通道的模型", repo.hasTextModel())
        assertFalse(repo.hasImageModel())
    }

    @Test
    fun textOnlyCatalogOpensOnlyTheTextGate() {
        val repo = repoOf(listOf(model("text-a", Role.TEXT)))
        assertTrue(repo.hasTextModel())
        assertFalse(repo.hasNovelModel())
        assertFalse(repo.hasImageModel())
    }

    @Test
    fun imageOnlyCatalogOpensOnlyTheImageGate() {
        val repo = repoOf(listOf(model("img-a", Role.IMAGE)))
        assertTrue(repo.hasImageModel())
        assertFalse(repo.hasTextModel())
        assertFalse(repo.hasNovelModel())
    }

    @Test
    fun downOrKeylessEntriesOpenNoGate() {
        val repo = repoOf(
            listOf(
                model("novel-down", Role.NOVEL, available = false),
                model("text-nokey", Role.TEXT, apiKey = null),
            ),
        )
        assertFalse(repo.hasTextModel())
        assertFalse(repo.hasNovelModel())
        assertFalse(repo.hasImageModel())
    }

    @Test
    fun defaultsRolesDeclarationWinsWithinItsOwnRole() {
        // 目录声明 novel 默认指向 novel-b：正文通道必须走它而不是首个 novel 条目
        val repo = repoOf(
            models = listOf(model("novel-a", Role.NOVEL), model("novel-b", Role.NOVEL)),
            roles = mapOf(Role.NOVEL to "novel-b"),
        )
        assertEquals("novel-b", repo.effectiveNovelEntry()?.id)
        assertFalse("正文默认不给短字段借用", repo.hasTextModel())
    }

    @Test
    fun availabilitySnapshotReportsEachRoleIndependently() = runTest {
        val repo = repoOf(listOf(model("novel-a", Role.NOVEL), model("img-a", Role.IMAGE)))
        assertEquals(
            RoleModelAvailability(text = false, novel = true, image = true),
            repo.roleModelAvailability.first(),
        )
    }

    @Test
    fun pipelineKeepsItsCrossRoleFallbackWhileTheTextGateStaysRolePure() {
        // 管线保留任一带 key 模型的兜底，但入口闸门不跟着打开
        val repo = repoOf(listOf(model("novel-a", Role.NOVEL)))
        assertEquals("novel-a", repo.effectiveTextEntry()?.id)
        assertTrue("管线预检仍视为能翻译", repo.hasKey())
        assertFalse("入口闸门不跟着兜底打开", repo.hasTextModel())
    }
}

private object FakeTranslationDao : TranslationDao {
    override suspend fun get(srcHash: String, targetLang: String, engineId: String): String? = null

    override suspend fun getAll(
        srcHashes: List<String>,
        targetLang: String,
        engineId: String,
    ): List<TranslationDao.CacheHit> = emptyList()

    override suspend fun upsertAll(entities: List<TranslationEntity>) = Unit

    override suspend fun count(): Int = 0

    override suspend fun deleteOldest(count: Int) = Unit
}

private object NoopChatApi : LlmChatApi {
    override suspend fun chat(
        url: String,
        authorization: String,
        body: JsonObject,
    ): ChatResponse = error("闸门测试不应发出翻译请求")
}
