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

    /** 当前已加载的目录版本号，用于对比远程版本决定是否更新 */
    private var cachedVersion: Int = 0

    /** 远程目录 URL 上次写入缓存时的地址，用于检测源切换 */
    private var cachedUrl: String = ""

    init {
        // 启动时先从磁盘缓存加载，UI 立即有数据展示
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

    /** 拉取远程目录并整体替换当前列表，成功后写入磁盘缓存；版本号低于等于缓存时跳过更新。 */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val url = settingsRepository.catalogRemoteUrl.value.trim()
        if (url.isBlank()) return@withContext false
        // 检测源切换：URL 变化时清空旧缓存和版本号，避免跨源版本对比误跳过
        if (url != cachedUrl) {
            Log.d(TAG, "catalog source changed: $cachedUrl -> $url, clearing cache")
            settingsRepository.clearCatalogCache()
            cachedVersion = 0
            cachedUrl = url
            // 清空旧源的模型数据，回退到内置默认，避免 UI 短暂展示不属于当前源的模型
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
            // 版本号对比：远程 <= 本地缓存则跳过此源，继续尝试下一个
            if (dto.version in 1..cachedVersion) {
                Log.d(TAG, "catalog skip $candidate: remote v${dto.version} <= cached v$cachedVersion")
                continue
            }
            _models.value = dto.models
            _defaults.value = dto.defaults
            cachedVersion = dto.version
            cachedUrl = candidate
            // 写入磁盘缓存，下次启动立即可用
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
