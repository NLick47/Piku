package com.piku.client.data.remote.translation

import android.util.Log
import com.piku.client.BuildConfig
import com.piku.client.data.local.CatalogSourceCodec
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

    private val _sourceId = MutableStateFlow("")

    /** 当前生效目录声明的来源标识（目录里的 sourceId），界面据此标明"这是谁的列表" */
    val sourceId: StateFlow<String> = _sourceId.asStateFlow()

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
        _sourceId.value = cachedModels?.sourceId.orEmpty()
        cachedVersion = cached?.third ?: 0
        cachedUrl = cached?.second.orEmpty()
        if (cachedModels != null) {
            Log.d(TAG, "catalog loaded from cache v$cachedVersion: ${cachedModels.models.size} entries")
        }
    }

    /** 拉取远程目录并替换；版本旧则跳过。 */
    suspend fun refresh(): CatalogRefreshResult = withContext(Dispatchers.IO) {
        val url = settingsRepository.catalogRemoteUrl.value.trim()
        if (url.isBlank()) return@withContext CatalogRefreshResult.FAILED
        // 源切换：重置版本和缓存（官方几个镜像共用一份缓存，命中哪个都算同源）
        if (!catalogSameSource(url, cachedUrl)) {
            Log.d(TAG, "catalog source changed: $cachedUrl -> $url, clearing cache")
            settingsRepository.clearCatalogCache()
            cachedVersion = 0
            // 回退到内置默认，避免展示旧源数据
            _models.value = ModelCatalog.DEFAULTS
            _defaults.value = null
        }
        val official = url in SettingsRepository.CATALOG_URL_MIRRORS
        val candidates = if (official) SettingsRepository.CATALOG_URL_MIRRORS else listOf(url)
        val probe = pickCatalog(
            candidates = candidates,
            cachedVersion = cachedVersion,
            fetch = { fetch(it) },
            decode = { decode(it) },
        )
        cachedUrl = if (official) SettingsRepository.CATALOG_URL_DEFAULT else url
        val pick = probe.pick
        // 源的身份：命中的那份优先，没命中（版本没变）也要用探测到的，否则「存为新源」后名字仍是 URL 猜的
        val declared = pick?.dto?.sourceId.orEmpty().ifBlank { probe.declaredSourceId }
        if (pick == null) {
            applySourceIdentity(url, declared)
            // 取到并解开了、只是版本没变，是「已是最新」；一个源都没取到才是失败
            return@withContext if (probe.reachable) {
                Log.d(TAG, "catalog already current (v$cachedVersion), keeping cache")
                CatalogRefreshResult.UP_TO_DATE
            } else {
                Log.d(TAG, "catalog refresh failed: all ${candidates.size} candidate(s) unreachable/undecodable")
                CatalogRefreshResult.FAILED
            }
        }
        _models.value = pick.dto.models
        _defaults.value = pick.dto.defaults
        applySourceIdentity(url, declared)
        cachedVersion = pick.dto.version
        settingsRepository.saveCatalogCache(pick.body, cachedUrl, pick.dto.version)
        Log.d(TAG, "catalog refreshed from ${pick.url} v${pick.dto.version}: ${pick.dto.models.size} entries")
        CatalogRefreshResult.UPDATED
    }

    /** 目录声明的 sourceId 就是源的身份：记住它显示出来，并把自动命名的源改成它（改过名的不动） */
    private fun applySourceIdentity(url: String, declaredId: String) {
        val id = declaredId.trim()
        if (id.isEmpty()) return
        _sourceId.value = id
        val source = settingsRepository.catalogSources.value.firstOrNull { it.url == url } ?: return
        val name = CatalogSourceCodec.declaredName(source.name, source.url, id) ?: return
        Log.d(TAG, "catalog source renamed to its declared id: ${source.name} -> $name")
        settingsRepository.renameCatalogSource(source.id, name)
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
}

private const val TAG = "PikuDiag"

/**
 * 缓存归属判定：官方几个镜像是一组等价源——用户换了命中镜像、或旧版 prefs 里还存着
 * jsDelivr 地址，都不算换源，否则每次启动都会清缓存、退回没有 key 的内置列表。
 * 自定义源仍按地址严格比对。
 */
internal fun catalogSameSource(url: String, cached: String): Boolean =
    if (url in SettingsRepository.CATALOG_URL_MIRRORS) {
        cached.isBlank() || cached in SettingsRepository.CATALOG_URL_MIRRORS
    } else {
        cached == url
    }

/** 一次刷新的结果：真的换了新目录 / 源可达但没有新版本 / 候选源全不通 */
enum class CatalogRefreshResult { UPDATED, UP_TO_DATE, FAILED }

/** 一次成功的目录选取：命中的镜像、原始正文与解出的目录 */
internal data class CatalogPick(val url: String, val body: String, val dto: ModelCatalogDto)

/**
 * 候选源探测结果：[pick] 非空即命中的那一份；[reachable] 表示至少有一个源正确取到并解出了
 * 目录（只是版本不新）——用来把「已是最新」和「全都取不到」分开，两者都不能更新，但含义相反。
 * [declaredSourceId] 是取到的目录声明的来源标识：版本没变时也拿得到，源命名靠它。
 */
internal data class CatalogProbe(
    val pick: CatalogPick?,
    val reachable: Boolean,
    val declaredSourceId: String = "",
)

/**
 * 按 [candidates] 顺序尝试，返回首个「取到、解得开、非空、版本比 [cachedVersion] 新」的结果；
 * 每个候选源的失败都不影响下一个（不可达 / 解不开 / 版本旧一律继续）。
 * 全部失败返回 null——调用方按 [CatalogProbe.reachable] 区分"已是最新"与"真失败"。
 * 版本旧也继续是必需的：镜像同步会滞后，先试的源可能旧，得让后面的补上。
 */
internal suspend fun pickCatalog(
    candidates: List<String>,
    cachedVersion: Int,
    fetch: suspend (String) -> String?,
    decode: (String) -> ModelCatalogDto?,
): CatalogProbe {
    var reachable = false
    var declaredSourceId = ""
    for (candidate in candidates) {
        val body = fetch(candidate) ?: continue
        val dto = decode(body) ?: continue
        if (dto.models.isEmpty()) continue
        if (declaredSourceId.isBlank()) declaredSourceId = dto.sourceId.orEmpty()
        if (dto.version in 1..cachedVersion) {
            reachable = true
            Log.d(TAG, "catalog skip $candidate: remote v${dto.version} <= cached v$cachedVersion")
            continue
        }
        return CatalogProbe(CatalogPick(candidate, body, dto), reachable = true, declaredSourceId = declaredSourceId)
    }
    return CatalogProbe(pick = null, reachable = reachable, declaredSourceId = declaredSourceId)
}
