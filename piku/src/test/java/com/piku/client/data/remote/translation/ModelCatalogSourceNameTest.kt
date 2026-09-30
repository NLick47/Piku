package com.piku.client.data.remote.translation

import com.piku.client.data.local.CatalogSource
import com.piku.client.data.local.CatalogSourceCodec
import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 拉取成功后源名称改用目录声明的 sourceId（比从 URL 猜出来的清楚），
 * 但用户手动改过名的不动——名字是用户的，不能被远程目录覆盖。
 */
class ModelCatalogSourceNameTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
    private val url = "https://example.com/catalog/models.enc.json"

    private fun catalogBody(sourceId: String) =
        """{"version":9,"sourceId":"$sourceId","models":[""" +
            """{"id":"m1","label":"M1","baseUrl":"https://api.example/v1","model":"m"}]}"""

    private fun repository(prefs: InMemorySharedPreferences) = ModelCatalogRepository(
        settingsRepository = SettingsRepository(prefs),
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("")
                    .body(catalogBody("mom09-fork").toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build(),
        json = json,
    )

    /** 预置一个已保存的源并激活它 */
    private fun prefsWithSourceName(name: String): InMemorySharedPreferences {
        val prefs = InMemorySharedPreferences()
        SettingsRepository(prefs).apply {
            saveCatalogSource(CatalogSource(id = "s1", name = name, url = url))
            setCatalogRemoteUrl(url)
        }
        return prefs
    }

    @Test
    fun `declared source id becomes the source name`() = runTest {
        val prefs = prefsWithSourceName(CatalogSourceCodec.autoName(url))

        assertEquals(CatalogRefreshResult.UPDATED, repository(prefs).refresh())

        assertEquals("mom09-fork", SettingsRepository(prefs).catalogSources.value.single().name)
    }

    @Test
    fun `hand renamed source keeps its name`() = runTest {
        val prefs = prefsWithSourceName("我的小模型源")

        assertEquals(CatalogRefreshResult.UPDATED, repository(prefs).refresh())

        assertEquals("我的小模型源", SettingsRepository(prefs).catalogSources.value.single().name)
    }

    @Test
    fun `already current catalog still names the source by its declared id`() = runTest {
        // 存为新源时若地址与当前源同组、版本又相同（真机踩过）：走的是「已是最新」分支，
        // 这条分支同样要按声明的 id 命名，否则名字还是从 URL 猜出来的那个
        val prefs = InMemorySharedPreferences()
        SettingsRepository(prefs).apply {
            saveCatalogSource(CatalogSource(id = "s1", name = CatalogSourceCodec.autoName(url), url = url))
            setCatalogRemoteUrl(url)
            saveCatalogCache(catalogBody("mom09-fork"), url, 9)
        }

        assertEquals(CatalogRefreshResult.UP_TO_DATE, repository(prefs).refresh())

        assertEquals("mom09-fork", SettingsRepository(prefs).catalogSources.value.single().name)
    }
}
