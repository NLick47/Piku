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
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** 远程加密模型目录：启动时先从磁盘缓存加载，再后台拉取最新替换。 */
@Singleton
class ModelCatalogRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @Named("translate") private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    private val _models: MutableStateFlow<List<ModelEntry>>
    val models: StateFlow<List<ModelEntry>> get() = _models.asStateFlow()

    private val _defaults = MutableStateFlow<CatalogDefaults?>(null)
    val catalogDefaults: StateFlow<CatalogDefaults?> = _defaults.asStateFlow()

    /** 缓存的目录版本号 */
    private var cachedVersion: Int = 0

    /** 缓存对应的 URL，变化时重置版本号 */
    private var cachedUrl: String = ""

    init {
        // 启动从缓存加载，秒开
        val cached = settingsRepository.loadCatalogCache()
        val cachedModels = cached?.let { (body, _, _) -> decode(body)?.takeIf { it.models.isNotEmpty() } }
        _models = MutableStateFlow(cachedModels?.models ?: ModelCatalog.DEFAULTS)
        _defaults.value = cachedModels?.defaults
        cachedVersion = cached?.third ?: 0
        cachedUrl = cached?.second.orEmpty()
        if (cachedModels != null) {
            Log.d(TAG, "catalog loaded from cache v$cachedVersion: ${cachedModels.models.size} entries")
        }
    }

    /** 拉取远程目录并替换；版本旧则跳过。 */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val url = settingsRepository.catalogRemoteUrl.value.trim()
        if (url.isBlank()) return@withContext false
        // 源切换：重置版本和缓存
        if (url != cachedUrl) {
            Log.d(TAG, "catalog source changed: $cachedUrl -> $url, clearing cache")
            settingsRepository.clearCatalogCache()
            cachedVersion = 0
            cachedUrl = url
            // 回退到内置默认，避免展示旧源数据
            _models.value = ModelCatalog.DEFAULTS
            _defaults.value = null
        }
        val candidates = if (url == SettingsRepository.CATALOG_URL_DEFAULT) {
            listOf(url, SettingsRepository.CATALOG_URL_FALLBACK)
        } else {
            listOf(url)
        }
        for (candidate in candidates) {
            val body = fetch(candidate) ?: continue
            val dto = decode(body) ?: continue
            if (dto.models.isEmpty()) continue
            // 版本旧则跳过，继续下一个源
            if (dto.version in 1..cachedVersion) {
                Log.d(TAG, "catalog skip $candidate: remote v${dto.version} <= cached v$cachedVersion")
                continue
            }
            _models.value = dto.models
            _defaults.value = dto.defaults
            cachedVersion = dto.version
            cachedUrl = candidate
            settingsRepository.saveCatalogCache(body, candidate, dto.version)
            Log.d(TAG, "catalog refreshed from $candidate v${dto.version}: ${dto.models.size} entries")
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

    /** 信封解密，无信封则按明文解析 */
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
