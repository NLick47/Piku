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

/**
 * 模型列表来源：启动时从远程加密目录整体拉取，内置默认仅作离线兜底。
 *
 * 免费模型经常下架/改名，key 也可能需要轮换，远程目录让这些修正不必发版：
 * 默认地址指向 piku-models 仓库经 jsDelivr 分发的 AES-256-GCM 密文，
 * [refresh] 拉取后解密并整体替换当前列表（远程为准，已下架条目随之消失）；
 * 用户也可自填明文 JSON 地址（旧用法兼容）。
 * 拉取失败时静默沿用当前列表（冷启动即内置默认），模型选择不能因为这个可选功能而不可用。
 */
@Singleton
class ModelCatalogRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @Named("translate") private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    private val _models = MutableStateFlow(ModelCatalog.DEFAULTS)
    val models: StateFlow<List<ModelEntry>> = _models.asStateFlow()

    /** 目录全局默认（请求参数 + 提示词），供翻译引擎按模型继承/覆盖 */
    private val _defaults = MutableStateFlow<CatalogDefaults?>(null)
    val catalogDefaults: StateFlow<CatalogDefaults?> = _defaults.asStateFlow()

    /**
     * 拉取远程目录并整体替换当前列表（远程为准：新增/修改生效，已删除条目随之移除）。
     * 默认地址主用 jsDelivr，被墙时回退 GitHub 直连；自定义地址不附加回退。
     * jsDelivr 对分支 URL 有最长约 12h 的边缘缓存，拉取前先 purge 强制回源，
     * 保证发布新目录后下一次冷启动就能拿到（见 [purgeJsDelivrCache]）。
     * 远程返回空列表视为无效数据，不清空当前列表；任一候选成功即返回 true。
     */
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
            if (dto.models.isEmpty()) {
                Log.d(TAG, "catalog from $candidate has no models, ignored")
                continue
            }
            _models.value = dto.models
            _defaults.value = dto.defaults
            Log.d(TAG, "catalog refreshed from $candidate: ${dto.models.size} entries, " +
                "keyed=${dto.models.count { !it.apiKey.isNullOrBlank() }}")
            return@withContext true
        }
        Log.d(TAG, "catalog refresh failed: all ${candidates.size} candidate(s) unreachable/undecodable/empty")
        false
    }

    private fun fetch(url: String): String? = runCatching {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string().orEmpty().ifEmpty { error("empty body") }
        }
    }.onFailure { Log.d(TAG, "catalog fetch failed ($url): ${it.message}") }.getOrNull()

    /**
     * 请求 jsDelivr 官方 purge 端点，把内置目录分支 URL 的边缘缓存清掉。
     * 尽力而为：失败只记日志不影响后续拉取（大不了沿用缓存 TTL 内的旧数据）；
     * 短超时避免网络不佳时拖慢启动刷新。仅对内置默认地址生效，自定义地址不 purge。
     */
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

    /** 加密信封优先解密；否则按用户自定义的明文 JSON 解析（保持旧用法兼容） */
    private fun decode(body: String): ModelCatalogDto? = runCatching {
        val envelope = runCatching { json.decodeFromString<CryptoHelper.Envelope>(body) }.getOrNull()
            ?.takeIf { it.alg.isNotBlank() && it.iv.isNotBlank() && it.data.isNotBlank() }
        val plain = if (envelope != null) {
            val key = BuildConfig.CATALOG_ENC_KEY
            if (key.isBlank()) error("缺少 BuildConfig.CATALOG_ENC_KEY")
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
