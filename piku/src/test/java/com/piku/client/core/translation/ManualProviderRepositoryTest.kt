package com.piku.client.core.translation

import com.piku.client.core.storage.prefs.InMemorySharedPreferences
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualProviderRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    private fun repo(prefs: InMemorySharedPreferences = InMemorySharedPreferences()) =
        testManualProviderRepository(prefs, json)

    private fun provider(
        name: String = "本地混元",
        baseUrl: String = "http://192.168.1.10:8000",
        model: String = "hunyuan-mt",
        authMode: AuthMode = AuthMode.NONE,
        roles: List<String> = listOf(Role.NOVEL),
        baseUrls: List<String> = emptyList(),
    ) = ManualProvider(
        name = name,
        baseUrl = baseUrl,
        model = model,
        authMode = authMode,
        roles = roles,
        baseUrls = baseUrls,
    )

    @Test
    fun saveNormalizesBareHostAndKeepsKeySeparate() {
        val repository = repo()
        val saved = repository.upsert(provider(), newKey = "local-secret")

        assertEquals("http://192.168.1.10:8000/v1", saved.baseUrl)
        assertTrue(saved.id.startsWith("manual:"))
        assertTrue(saved.hasKey)

        val entry = repository.modelEntries().single()
        assertTrue(entry.isManual)
        assertTrue(entry.isUsable())
        assertNull(entry.apiKey)
    }

    @Test
    fun bearerProviderNeedsKeyAndExposesItInMergedEntry() {
        val repository = repo()
        val saved = repository.upsert(
            provider(baseUrl = "https://api.example.com/v1", authMode = AuthMode.BEARER),
            newKey = null,
        )

        assertFalse(saved.hasKey)
        assertFalse(repository.modelEntries().single().isUsable())

        repository.upsert(saved, newKey = "sk-test")
        val entry = repository.modelEntries().single()
        assertTrue(entry.isUsable())
        assertEquals("sk-test", entry.apiKey)
        assertEquals(AuthMode.BEARER, entry.authMode)
    }

    @Test
    fun nullKeyKeepsExistingAndBlankKeyClears() {
        val repository = repo()
        val saved = repository.upsert(
            provider(authMode = AuthMode.BEARER),
            newKey = "sk-keep",
        )

        val kept = repository.upsert(saved, newKey = null)
        assertTrue(kept.hasKey)
        assertEquals("sk-keep", repository.modelEntries().single().apiKey)

        val cleared = repository.upsert(kept, newKey = "")
        assertFalse(cleared.hasKey)
        assertFalse(repository.modelEntries().single().isUsable())
    }

    @Test
    fun deleteRemovesEntryAndSecret() {
        val prefs = InMemorySharedPreferences()
        val repository = repo(prefs)
        val saved = repository.upsert(provider(authMode = AuthMode.BEARER), newKey = "sk-delete")

        repository.delete(saved.id)

        assertTrue(repository.providers.value.isEmpty())
        assertNull(repository.apiKey(saved.id))
        assertTrue(repository.modelEntries().isEmpty())
    }

    @Test
    fun baseUrlsAreNormalizedAndUnknownRolesDropped() {
        val repository = repo()
        val saved = repository.upsert(
            provider(
                baseUrl = "https://primary.example/v1/chat/completions",
                baseUrls = listOf("https://backup.example", "https://backup.example/v1/"),
                roles = listOf(Role.TEXT, Role.IMAGE, "unknown"),
            ),
            newKey = null,
        )

        assertEquals("https://primary.example/v1", saved.baseUrl)
        assertEquals(listOf("https://backup.example/v1"), saved.baseUrls)
        assertEquals(listOf(Role.TEXT), saved.roles)
    }

    @Test
    fun promptOverridesAreNormalizedAndSurviveRecreation() {
        val prefs = InMemorySharedPreferences()
        val first = repo(prefs)
        val saved = first.upsert(
            provider().copy(
                promptOverrides = PromptOverrides(
                    single = mapOf("zh" to "  自定义规则  ", "fr" to "unknown"),
                    novel = mapOf("ja" to "   "),
                ),
            ),
            newKey = null,
        )

        assertEquals("自定义规则", saved.promptOverrides.single["zh"])
        assertFalse(saved.promptOverrides.single.containsKey("fr"))
        assertTrue(saved.promptOverrides.novel.isEmpty())

        val restored = repo(prefs).find(saved.id)
        assertEquals("自定义规则", restored?.promptOverrides?.single?.get("zh"))
        assertEquals("自定义规则", restored?.toModelEntry(null)?.prompts?.single?.get("zh"))
    }

    @Test
    fun savedProviderSurvivesRepositoryRecreation() {
        val prefs = InMemorySharedPreferences()
        val first = repo(prefs)
        val saved = first.upsert(provider(authMode = AuthMode.BEARER), newKey = "sk-persist")

        val second = repo(prefs)
        val reloaded = second.providers.value.single()

        assertEquals(saved.id, reloaded.id)
        assertEquals("sk-persist", second.apiKey(saved.id))
        assertTrue(reloaded.hasKey)
    }
}
