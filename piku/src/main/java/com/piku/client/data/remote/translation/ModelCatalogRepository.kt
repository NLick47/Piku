package com.piku.client.data.remote.translation

import android.util.Log
import com.piku.client.BuildConfig
import com.piku.client.data.local.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** 远程加密模型目录：启动时拉取解密替换内置默认，失败静默沿用内置列表。 */
@Singleton
class ModelCatalogRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @Named("translate") private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    private val _models = MutableStateFlow(ModelCatalog.DEFAULTS)
    val models: StateFlow<List<ModelEntry>> = _models.asStateFlow()

    private val _defaults = MutableStateFlow<CatalogDefaults?>(null)
    val catalogDefaults: StateFlow<CatalogDefaults?> = _defaults.asStateFlow()

    /** 拉取远程目录并整体替换当前列表；拉取前先 purge jsDelivr 边缘缓存。 */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val url = settingsRepository.catalogRemoteUrl.value.trim()
        if (url.isBlank()) return@withContext false
        val candidates = if (url == SettingsRepository.CATALOG_URL_DEFAULT) {
            purgeJsDelivrCache()
            listOf(url, SettingsRepository.CATALOG_URL_FALLBACK)
        } else {
            listOf(url)
        }
        for (candidate in candidates) {
            val body = fetch(candidate) ?: continue
            val dto = decode(body) ?: continue
            if (dto.models.isEmpty()) continue
            _models.value = dto.models
            _defaults.value = dto.defaults
            Log.d(TAG, "catalog refreshed from $candidate: ${dto.models.size} entries")
            return@withContext true
        }
        Log.d(TAG, "catalog refresh failed: all ${candidates.size} candidate(s) unreachable/undecodable")
        false
    }

    private fun fetch(url: String): String? = runCatching {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string().orEmpty().ifEmpty { error("empty body") }
        }
    }.onFailure { Log.d(TAG, "catalog fetch failed ($url): ${it.message}") }.getOrNull()

    /** 请求 jsDelivr purge 端点清边缘缓存，失败不影响后续拉取。 */
    private fun purgeJsDelivrCache() {
        val purgeUrl = SettingsRepository.CATALOG_URL_DEFAULT
            .replace("https://cdn.jsdelivr.net", "https://purge.jsdelivr.net")
        runCatching {
            okHttpClient.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url(purgeUrl).build())
                .execute().use { response ->
                    Log.d(TAG, "jsdelivr purge: HTTP ${response.code}")
                }
        }.onFailure { Log.d(TAG, "jsdelivr purge skipped: ${it.message}") }
    }

    /** 加密信封解密，否则按明文 JSON 解析（兼容自定义地址）。 */
    private fun decode(body: String): ModelCatalogDto? = runCatching {
        val envelope = runCatching { json.decodeFromString<CryptoHelper.Envelope>(body) }.getOrNull()
            ?.takeIf { it.alg.isNotBlank() && it.iv.isNotBlank() && it.data.isNotBlank() }
        val plain = if (envelope != null) {
            val key = settingsRepository.catalogEncKey.value.trim()
                .ifBlank { BuildConfig.CATALOG_ENC_KEY }
            if (key.isBlank()) error("缺少解密密钥")
            CryptoHelper.decrypt(envelope, key)
        } else {
            body
        }
        json.decodeFromString<ModelCatalogDto>(plain)
    }.onFailure { Log.d(TAG, "catalog decode failed: ${it.message}") }.getOrNull()

    private companion object {
        const val TAG = "PikuDiag"
    }
}
