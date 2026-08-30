package com.piku.client.data.local

import android.content.SharedPreferences
import com.piku.client.data.repository.SyncResult
import com.piku.client.data.repository.SyncState
import com.piku.client.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 目录源条目的新 id（SettingsRepository 与 ViewModel 共用的生成规则） */
internal fun newCatalogSourceId(): String = UUID.randomUUID().toString()

/**
 * 应用设置存储。
 * 使用 SharedPreferences（而非 DataStore）是为了去掉 DataStore 依赖、缩小 APK；
 * 与 [LanguageStore] 同一套模式：内存 StateFlow 为准，写后同步更新内存 + apply() 落盘。
 * 读操作全部同步内存，写操作 setter 无挂起点，调用线程上读改写原子完成。
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {

    init {
        // 双图层一次性迁移：引入头部层独立取景前只有一套背景取景参数，
        // 把它复制为头部层初值，老用户升级后跟随模式渲染与旧版完全一致。
        if (!prefs.getBoolean(KEY_DUAL_LAYER_MIGRATED, false)) {
            prefs.edit()
                .putFloat(KEY_HERO_OFFSET_X, prefs.getFloat(KEY_BACKGROUND_OFFSET_X, 0f))
                .putFloat(KEY_HERO_OFFSET_Y, prefs.getFloat(KEY_BACKGROUND_OFFSET_Y, 0f))
                .putFloat(
                    KEY_HERO_SCALE,
                    prefs.getFloat(KEY_BACKGROUND_SCALE, BACKGROUND_SCALE_DEFAULT),
                )
                .putBoolean(KEY_DUAL_LAYER_MIGRATED, true)
                .apply()
        }
    }

    private val _showAdultContent = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_ADULT_CONTENT, false),
    )
    val showAdultContent: StateFlow<Boolean> = _showAdultContent.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, null)
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** 浏览记录保留天数，0 表示永久保留 */
    private val _historyRetentionDays = MutableStateFlow(
        prefs.getInt(KEY_HISTORY_RETENTION_DAYS, 0),
    )
    val historyRetentionDays: StateFlow<Int> = _historyRetentionDays.asStateFlow()

    /** 启动时自动检查更新 */
    private val _autoCheckEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true),
    )
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheckEnabled.asStateFlow()

    /** 上次检查更新时间（epoch millis），0 表示从未检查过 */
    private val _lastUpdateCheckAt = MutableStateFlow(
        prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L),
    )
    val lastUpdateCheckAt: StateFlow<Long> = _lastUpdateCheckAt.asStateFlow()

    /** 首页自定义背景图片路径（应用私有目录），null 表示使用默认背景 */
    private val _customBackgroundPath = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_BACKGROUND_PATH, null)?.takeIf { File(it).exists() },
    )
    val customBackgroundPath: StateFlow<String?> = _customBackgroundPath.asStateFlow()

    /** 自定义背景压暗程度 0~1，0 表示不压暗 */
    private val _backgroundDim = MutableStateFlow(
        prefs.getFloat(KEY_BACKGROUND_DIM, BACKGROUND_DIM_DEFAULT),
    )
    val backgroundDim: StateFlow<Float> = _backgroundDim.asStateFlow()

    /** 自定义背景暗色遮罩色（ARGB，取自图片主色），null 表示未提取，回退纯黑 */
    private val _backgroundScrimDark = MutableStateFlow(
        prefs.getInt(KEY_BACKGROUND_SCRIM_DARK, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE },
    )
    val backgroundScrimDark: StateFlow<Int?> = _backgroundScrimDark.asStateFlow()

    /** 自定义背景亮色遮罩色（ARGB，取自图片主色），null 表示未提取，回退纯白 */
    private val _backgroundScrimLight = MutableStateFlow(
        prefs.getInt(KEY_BACKGROUND_SCRIM_LIGHT, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE },
    )
    val backgroundScrimLight: StateFlow<Int?> = _backgroundScrimLight.asStateFlow()

    /** 独立背景图在首页的水平/垂直归一化偏移（-1~1，Alignment.Offset 空间）。
     * -1 表示左/上边缘，0 表示居中，1 表示右/下边缘。
     * 双图层引入后仅作用于独立背景图的取景；跟随模式由头部层偏移驱动。 */
    private val _backgroundOffsetX = MutableStateFlow(
        prefs.getFloat(KEY_BACKGROUND_OFFSET_X, 0f)
    )
    val backgroundOffsetX: StateFlow<Float> = _backgroundOffsetX.asStateFlow()
    private val _backgroundOffsetY = MutableStateFlow(
        prefs.getFloat(KEY_BACKGROUND_OFFSET_Y, 0f)
    )
    val backgroundOffsetY: StateFlow<Float> = _backgroundOffsetY.asStateFlow()

    /** 保存的背景图片原始宽/高（像素），用于拖动手势计算溢出量。
     * 由 saveFromUri 成功后顺手写入，若为 null 则退回使用容器尺寸估算。 */
    private val _backgroundImgWidth = MutableStateFlow(
        prefs.getInt(KEY_BACKGROUND_IMG_WIDTH, 0).takeIf { it > 0 }
    )
    val backgroundImgWidth: StateFlow<Int?> = _backgroundImgWidth.asStateFlow()
    private val _backgroundImgHeight = MutableStateFlow(
        prefs.getInt(KEY_BACKGROUND_IMG_HEIGHT, 0).takeIf { it > 0 }
    )
    val backgroundImgHeight: StateFlow<Int?> = _backgroundImgHeight.asStateFlow()

    private val _backgroundScale = MutableStateFlow(
        prefs.getFloat(KEY_BACKGROUND_SCALE, BACKGROUND_SCALE_DEFAULT)
            .coerceIn(BACKGROUND_SCALE_MIN, BACKGROUND_SCALE_MAX),
    )
    val backgroundScale: StateFlow<Float> = _backgroundScale.asStateFlow()

    /** 内容区背景模糊强度（dp），0 表示不模糊 */
    private val _backgroundBlur = MutableStateFlow(
        prefs.getFloat(KEY_BACKGROUND_BLUR, BACKGROUND_BLUR_DEFAULT)
            .coerceIn(BACKGROUND_BLUR_MIN, BACKGROUND_BLUR_MAX),
    )
    val backgroundBlur: StateFlow<Float> = _backgroundBlur.asStateFlow()

    /** 头部清晰区占屏幕高度比例（0.22~0.45） */
    private val _backgroundHeroFraction = MutableStateFlow(
        prefs.getFloat(KEY_BACKGROUND_HERO, BACKGROUND_HERO_DEFAULT)
            .coerceIn(BACKGROUND_HERO_MIN, BACKGROUND_HERO_MAX),
    )
    val backgroundHeroFraction: StateFlow<Float> = _backgroundHeroFraction.asStateFlow()

    /** 头部层水平/垂直归一化偏移（-1~1），独立于背景层；缩放 <1（画框式）时不生效 */
    private val _heroOffsetX = MutableStateFlow(prefs.getFloat(KEY_HERO_OFFSET_X, 0f))
    val heroOffsetX: StateFlow<Float> = _heroOffsetX.asStateFlow()
    private val _heroOffsetY = MutableStateFlow(prefs.getFloat(KEY_HERO_OFFSET_Y, 0f))
    val heroOffsetY: StateFlow<Float> = _heroOffsetY.asStateFlow()

    /** 头部层缩放：0.5~1.5。≥1 满铺裁切取景，<1 整幅缩小为画框式呈现 */
    private val _heroScale = MutableStateFlow(
        prefs.getFloat(KEY_HERO_SCALE, HERO_SCALE_DEFAULT)
            .coerceIn(HERO_SCALE_MIN, HERO_SCALE_MAX),
    )
    val heroScale: StateFlow<Float> = _heroScale.asStateFlow()

    /** 独立背景层图片路径（应用私有目录），null 表示跟随头部图（无缝过渡）。
     * 引入后 backgroundOffsetX/Y/Scale 仅作为该独立背景图的取景参数。 */
    private val _backdropPath = MutableStateFlow(
        prefs.getString(KEY_BACKDROP_PATH, null)?.takeIf { File(it).exists() },
    )
    val backdropPath: StateFlow<String?> = _backdropPath.asStateFlow()

    /** 独立背景图原始宽/高（像素），用于分离模式下拖动溢出计算 */
    private val _backdropImgWidth = MutableStateFlow(
        prefs.getInt(KEY_BACKDROP_IMG_WIDTH, 0).takeIf { it > 0 }
    )
    val backdropImgWidth: StateFlow<Int?> = _backdropImgWidth.asStateFlow()
    private val _backdropImgHeight = MutableStateFlow(
        prefs.getInt(KEY_BACKDROP_IMG_HEIGHT, 0).takeIf { it > 0 }
    )
    val backdropImgHeight: StateFlow<Int?> = _backdropImgHeight.asStateFlow()

    fun setShowAdultContent(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ADULT_CONTENT, value).apply()
        _showAdultContent.value = value
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setHistoryRetentionDays(days: Int) {
        prefs.edit().putInt(KEY_HISTORY_RETENTION_DAYS, days).apply()
        _historyRetentionDays.value = days
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_ENABLED, enabled).apply()
        _autoCheckEnabled.value = enabled
    }

    fun setCustomBackgroundPath(path: String?) {
        if (path == null) {
            prefs.edit().remove(KEY_CUSTOM_BACKGROUND_PATH).apply()
        } else {
            prefs.edit().putString(KEY_CUSTOM_BACKGROUND_PATH, path).apply()
        }
        _customBackgroundPath.value = path
    }

    /** 使用原始图片尺寸更新背景路径，并把偏移重置居中。
     * [imgWidth] 和 [imgHeight] 为像素单位，用于后续拖动手势的溢出计算。 */
    fun setCustomBackgroundPath(path: String?, imgWidth: Int?, imgHeight: Int?) {
        if (path == null) {
            prefs.edit().remove(KEY_CUSTOM_BACKGROUND_PATH).apply()
        } else {
            prefs.edit().putString(KEY_CUSTOM_BACKGROUND_PATH, path).apply()
        }
        // 写入原始图片尺寸（用于后续拖动溢出计算）
        prefs.edit()
            .putInt(KEY_BACKGROUND_IMG_WIDTH, imgWidth ?: 0)
            .putInt(KEY_BACKGROUND_IMG_HEIGHT, imgHeight ?: 0)
            .apply()
        // 换图时把偏移和缩放重置为默认
        _backgroundOffsetX.value = 0f
        _backgroundOffsetY.value = 0f
        _backgroundScale.value = BACKGROUND_SCALE_DEFAULT
        // 持久化重置
        prefs.edit()
            .putFloat(KEY_BACKGROUND_OFFSET_X, 0f)
            .putFloat(KEY_BACKGROUND_OFFSET_Y, 0f)
            .putFloat(KEY_BACKGROUND_SCALE, BACKGROUND_SCALE_DEFAULT)
            .apply()
        _customBackgroundPath.value = path
    }

    fun setBackgroundScale(value: Float, persist: Boolean = false) {
        val clamped = value.coerceIn(BACKGROUND_SCALE_MIN, BACKGROUND_SCALE_MAX)
        if (persist) prefs.edit().putFloat(KEY_BACKGROUND_SCALE, clamped).apply()
        _backgroundScale.value = clamped
    }

    fun setBackgroundDim(value: Float, persist: Boolean = false) {
        val clamped = value.coerceIn(BACKGROUND_DIM_MIN, BACKGROUND_DIM_MAX)
        if (persist) prefs.edit().putFloat(KEY_BACKGROUND_DIM, clamped).apply()
        _backgroundDim.value = clamped
    }

    fun setBackgroundBlur(value: Float, persist: Boolean = false) {
        val clamped = value.coerceIn(BACKGROUND_BLUR_MIN, BACKGROUND_BLUR_MAX)
        if (persist) prefs.edit().putFloat(KEY_BACKGROUND_BLUR, clamped).apply()
        _backgroundBlur.value = clamped
    }

    fun setBackgroundHeroFraction(value: Float, persist: Boolean = false) {
        val clamped = value.coerceIn(BACKGROUND_HERO_MIN, BACKGROUND_HERO_MAX)
        if (persist) prefs.edit().putFloat(KEY_BACKGROUND_HERO, clamped).apply()
        _backgroundHeroFraction.value = clamped
    }

    /** 头部层缩放（0.5~1.5） */
    fun setHeroScale(value: Float, persist: Boolean = false) {
        val clamped = value.coerceIn(HERO_SCALE_MIN, HERO_SCALE_MAX)
        if (persist) prefs.edit().putFloat(KEY_HERO_SCALE, clamped).apply()
        _heroScale.value = clamped
    }

    /** 头部层归一化偏移（内存即时更新，[persist] 为 true 时同步落盘） */
    fun setHeroOffset(x: Float, y: Float, persist: Boolean = false) {
        val nx = x.coerceIn(-1f, 1f)
        val ny = y.coerceIn(-1f, 1f)
        if (persist) {
            prefs.edit()
                .putFloat(KEY_HERO_OFFSET_X, nx)
                .putFloat(KEY_HERO_OFFSET_Y, ny)
                .apply()
        }
        _heroOffsetX.value = nx
        _heroOffsetY.value = ny
    }

    /** 保存独立背景图路径与尺寸，取景参数重置为默认 */
    fun setBackdropPath(path: String?, imgWidth: Int?, imgHeight: Int?) {
        prefs.edit()
            .applyPath(KEY_BACKDROP_PATH, path)
            .putInt(KEY_BACKDROP_IMG_WIDTH, imgWidth ?: 0)
            .putInt(KEY_BACKDROP_IMG_HEIGHT, imgHeight ?: 0)
            .putFloat(KEY_BACKGROUND_OFFSET_X, 0f)
            .putFloat(KEY_BACKGROUND_OFFSET_Y, 0f)
            .putFloat(KEY_BACKGROUND_SCALE, BACKGROUND_SCALE_DEFAULT)
            .apply()
        _backdropImgWidth.value = imgWidth?.takeIf { it > 0 }
        _backdropImgHeight.value = imgHeight?.takeIf { it > 0 }
        _backgroundOffsetX.value = 0f
        _backgroundOffsetY.value = 0f
        _backgroundScale.value = BACKGROUND_SCALE_DEFAULT
        _backdropPath.value = path
    }

    /** 清除独立背景图，回到跟随头部模式（背景层重新使用头部图 + 头部取景） */
    fun clearBackdropPath() {
        prefs.edit()
            .remove(KEY_BACKDROP_PATH)
            .remove(KEY_BACKDROP_IMG_WIDTH)
            .remove(KEY_BACKDROP_IMG_HEIGHT)
            .apply()
        _backdropPath.value = null
        _backdropImgWidth.value = null
        _backdropImgHeight.value = null
    }

    fun setBackgroundScrims(scrimDark: Int?, scrimLight: Int?) {
        prefs.edit()
            .applyScrim(KEY_BACKGROUND_SCRIM_DARK, scrimDark)
            .applyScrim(KEY_BACKGROUND_SCRIM_LIGHT, scrimLight)
            .apply()
        _backgroundScrimDark.value = scrimDark
        _backgroundScrimLight.value = scrimLight
    }

    private fun SharedPreferences.Editor.applyScrim(key: String, value: Int?): SharedPreferences.Editor =
        if (value == null) remove(key) else putInt(key, value)

    private fun SharedPreferences.Editor.applyPath(key: String, value: String?): SharedPreferences.Editor =
        if (value == null) remove(key) else putString(key, value)

    /** 归一化偏移持久化（拖动结束时调用） */
    fun persistBackgroundOffset() {
        prefs.edit()
            .putFloat(KEY_BACKGROUND_OFFSET_X, _backgroundOffsetX.value)
            .putFloat(KEY_BACKGROUND_OFFSET_Y, _backgroundOffsetY.value)
            .apply()
    }

    /** 头部层归一化偏移持久化（拖动结束时调用） */
    fun persistHeroOffset() {
        prefs.edit()
            .putFloat(KEY_HERO_OFFSET_X, _heroOffsetX.value)
            .putFloat(KEY_HERO_OFFSET_Y, _heroOffsetY.value)
            .apply()
    }

    /** 设置归一化偏移（内存即时更新，[persist] 为 true 时同步落盘）。
     * [x], [y] 均在 -1~1 的 Alignment.Offset 空间。 */
    fun setBackgroundOffset(x: Float, y: Float, persist: Boolean = false) {
        val nx = x.coerceIn(-1f, 1f)
        val ny = y.coerceIn(-1f, 1f)
        if (persist) {
            prefs.edit()
                .putFloat(KEY_BACKGROUND_OFFSET_X, nx)
                .putFloat(KEY_BACKGROUND_OFFSET_Y, ny)
                .apply()
        }
        _backgroundOffsetX.value = nx
        _backgroundOffsetY.value = ny
    }

    private val _novelFontSize = MutableStateFlow(
        prefs.getFloat(KEY_NOVEL_FONT_SIZE, NOVEL_FONT_DEFAULT),
    )
    val novelFontSize: StateFlow<Float> = _novelFontSize.asStateFlow()

    /** 小说阅读器浅色模式（米色纸），独立于系统主题，由用户显式切换 */
    private val _novelReaderLight = MutableStateFlow(
        prefs.getBoolean(KEY_NOVEL_READER_LIGHT, true),
    )
    val novelReaderLight: StateFlow<Boolean> = _novelReaderLight.asStateFlow()

    fun setNovelFontSize(size: Float) {
        val clamped = size.coerceIn(NOVEL_FONT_MIN, NOVEL_FONT_MAX)
        prefs.edit().putFloat(KEY_NOVEL_FONT_SIZE, clamped).apply()
        _novelFontSize.value = clamped
    }

    fun setNovelReaderLight(light: Boolean) {
        prefs.edit().putBoolean(KEY_NOVEL_READER_LIGHT, light).apply()
        _novelReaderLight.value = light
    }

    /**
     * 读取某作品的阅读进度（百分比 0~100，0 表示无进度）。
     * 用百分比而非像素偏移存储，避免字号调整后滚动位置错位。
     */
    fun getNovelProgress(workId: Long): Int =
        prefs.getInt(novelProgressKey(workId), 0).coerceIn(0, 100)

    /** 保存某作品的阅读进度（百分比 0~100） */
    fun setNovelProgress(workId: Long, percent: Int) {
        prefs.edit().putInt(novelProgressKey(workId), percent.coerceIn(0, 100)).apply()
    }

    private fun novelProgressKey(workId: Long): String = "$KEY_NOVEL_PROGRESS_PREFIX$workId"

    fun recordUpdateCheck() {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT, System.currentTimeMillis()).apply()
        _lastUpdateCheckAt.value = System.currentTimeMillis()
    }

    // ---------------- AI 翻译 ----------------

    /**
     * AI 翻译总开关：关闭时不发任何翻译请求，UI 也不显示译文入口。
     * 新装默认开启（内置免费模型零配置可用）；老用户已保存的值不受影响。
     */
    private val _aiTranslateEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AI_TRANSLATE_ENABLED, true),
    )
    val aiTranslateEnabled: StateFlow<Boolean> = _aiTranslateEnabled.asStateFlow()

    /** LLM 服务地址（OpenAI 兼容，不含 /chat/completions 段） */
    private val _llmBaseUrl = MutableStateFlow(
        prefs.getString(KEY_LLM_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: LLM_BASE_URL_DEFAULT,
    )
    val llmBaseUrl: StateFlow<String> = _llmBaseUrl.asStateFlow()

    /**
     * 模型 id（对应目录条目，也允许自填未收录的模型）。
     * 空串 = 未显式选择，走目录 defaults.roles 解析出的场景默认；
     * 高亮与实际翻译共用同一套解析，避免"存了个对不上目录的旧默认值导致无高亮"。
     */
    private val _llmModel = MutableStateFlow(
        prefs.getString(KEY_LLM_MODEL, null)?.trim() ?: LLM_MODEL_DEFAULT,
    )
    val llmModel: StateFlow<String> = _llmModel.asStateFlow()

    /**
     * 小说正文专用模型地址：空串表示跟随文本翻译模型（[llmBaseUrl]）。
     * 仅长篇正文走此模型，短文本字段仍用 [llmBaseUrl]/[llmModel]。
     */
    private val _llmNovelBaseUrl = MutableStateFlow(
        prefs.getString(KEY_LLM_NOVEL_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: "",
    )
    val llmNovelBaseUrl: StateFlow<String> = _llmNovelBaseUrl.asStateFlow()

    /** 小说正文专用模型 id：空串表示跟随文本翻译模型（[llmModel]） */
    private val _llmNovelModel = MutableStateFlow(
        prefs.getString(KEY_LLM_NOVEL_MODEL, null)?.takeIf { it.isNotBlank() } ?: "",
    )
    val llmNovelModel: StateFlow<String> = _llmNovelModel.asStateFlow()

    /**
     * 远程模型目录地址：默认即内置加密目录（jsDelivr 分发，见 [CATALOG_URL_DEFAULT]），
     * 启动时自动拉取以获得内置免费模型的共享 key 与模型修正；
     * 用户清空则不发任何请求、只用编译期内置列表。
     */
    private val _catalogRemoteUrl = MutableStateFlow(
        prefs.getString(KEY_CATALOG_REMOTE_URL, null)?.takeIf { it.isNotBlank() } ?: CATALOG_URL_DEFAULT,
    )
    val catalogRemoteUrl: StateFlow<String> = _catalogRemoteUrl.asStateFlow()

    /**
     * 自定义加密目录的解密密钥（64 位 hex，空串 = 使用编译期内置密钥）。
     * 与 [catalogRemoteUrl] 配套：第三方列表作者把地址和密钥一起分发，
     * 使用者在此成对填入；恢复默认地址时一并清空。
     */
    private val _catalogEncKey = MutableStateFlow(
        prefs.getString(KEY_CATALOG_ENC_KEY, null)?.trim()?.lowercase() ?: "",
    )
    val catalogEncKey: StateFlow<String> = _catalogEncKey.asStateFlow()

    /**
     * 已保存的自定义目录源列表（官方默认不入库，UI 固定首行渲染）。
     * 激活态即上面 [catalogRemoteUrl]/[catalogEncKey] 两键，切换 = 写入这两键。
     */
    private val _catalogSources = MutableStateFlow(loadCatalogSources())
    val catalogSources: StateFlow<List<CatalogSource>> = _catalogSources.asStateFlow()

    /** 首次读取时迁移：旧版单源自定义（非默认地址）自动种为列表第一条 */
    private fun loadCatalogSources(): List<CatalogSource> {
        val raw = prefs.getString(KEY_CATALOG_SOURCES, null)?.takeIf { it.isNotBlank() }
        if (raw != null) return CatalogSourceCodec.decode(raw)
        val legacyUrl = prefs.getString(KEY_CATALOG_REMOTE_URL, null)?.trim().orEmpty()
        if (legacyUrl.isEmpty() || legacyUrl == CATALOG_URL_DEFAULT) return emptyList()
        val seeded = listOf(
            CatalogSource(
                id = newCatalogSourceId(),
                name = CatalogSourceCodec.autoName(legacyUrl),
                url = legacyUrl,
                encKey = prefs.getString(KEY_CATALOG_ENC_KEY, null)?.trim()?.lowercase().orEmpty(),
            ),
        )
        prefs.edit().putString(KEY_CATALOG_SOURCES, CatalogSourceCodec.encode(seeded)).apply()
        return seeded
    }

    private fun persistCatalogSources(sources: List<CatalogSource>) {
        prefs.edit().putString(KEY_CATALOG_SOURCES, CatalogSourceCodec.encode(sources)).apply()
        _catalogSources.value = sources
    }

    /** 按 id upsert；同 id 已存在则整体替换 */
    fun saveCatalogSource(source: CatalogSource) {
        persistCatalogSources(_catalogSources.value.filterNot { it.id == source.id } + source)
    }

    fun renameCatalogSource(id: String, name: String) {
        persistCatalogSources(_catalogSources.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deleteCatalogSource(id: String) {
        persistCatalogSources(_catalogSources.value.filterNot { it.id == id })
    }

    fun setAiTranslateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_TRANSLATE_ENABLED, enabled).apply()
        _aiTranslateEnabled.value = enabled
    }

    /** 空值回退默认地址，避免用户清空后无法发请求 */
    fun setLlmBaseUrl(url: String) {
        val value = url.trim().ifBlank { LLM_BASE_URL_DEFAULT }
        prefs.edit().putString(KEY_LLM_BASE_URL, value).apply()
        _llmBaseUrl.value = value
    }

    /** 空串恢复「未显式选择」状态（跟随目录场景默认）；不做非空兜底以免伪造出假选中态 */
    fun setLlmModel(model: String) {
        val value = model.trim()
        prefs.edit().putString(KEY_LLM_MODEL, value).apply()
        _llmModel.value = value
    }

    /** 空串回退为「跟随文本翻译」（不持久化默认空串以外的回退值） */
    fun setLlmNovelBaseUrl(url: String) {
        val value = url.trim()
        prefs.edit().putString(KEY_LLM_NOVEL_BASE_URL, value).apply()
        _llmNovelBaseUrl.value = value
    }

    /** 空串表示跟随文本翻译模型 */
    fun setLlmNovelModel(model: String) {
        val value = model.trim()
        prefs.edit().putString(KEY_LLM_NOVEL_MODEL, value).apply()
        _llmNovelModel.value = value
    }

    fun setCatalogRemoteUrl(url: String) {
        val value = url.trim()
        prefs.edit().putString(KEY_CATALOG_REMOTE_URL, value).apply()
        _catalogRemoteUrl.value = value
    }

    /** 空串表示清除自定义密钥（回退编译期内置密钥）；格式校验由 UI 层负责 */
    fun setCatalogEncKey(key: String) {
        val value = key.trim().lowercase()
        prefs.edit().putString(KEY_CATALOG_ENC_KEY, value).apply()
        _catalogEncKey.value = value
    }

    // ---------------- WebDAV 同步 ----------------

    /** WebDAV 服务器地址 */
    private val _webDavUrl = MutableStateFlow(
        prefs.getString(KEY_WEBDAV_URL, null)?.takeIf { it.isNotBlank() } ?: "",
    )
    val webDavUrl: StateFlow<String> = _webDavUrl.asStateFlow()

    /** WebDAV 用户名 */
    private val _webDavUsername = MutableStateFlow(
        prefs.getString(KEY_WEBDAV_USERNAME, null)?.takeIf { it.isNotBlank() } ?: "",
    )
    val webDavUsername: StateFlow<String> = _webDavUsername.asStateFlow()

    /** WebDAV 密码（明文存储，SharedPreferences 本身受应用沙箱保护） */
    private val _webDavPassword = MutableStateFlow(
        prefs.getString(KEY_WEBDAV_PASSWORD, null) ?: "",
    )
    val webDavPassword: StateFlow<String> = _webDavPassword.asStateFlow()

    /** WebDAV 同步总开关 */
    private val _webDavEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_WEBDAV_ENABLED, false),
    )
    val webDavEnabled: StateFlow<Boolean> = _webDavEnabled.asStateFlow()

    /** 上次同步时间（epoch millis），0 表示从未同步 */
    private val _lastSyncAt = MutableStateFlow(
        prefs.getLong(KEY_WEBDAV_LAST_SYNC_AT, 0L),
    )
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    /** 上次同步结果（内存态，不持久化，每次启动重置） */
    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    fun setWebDavUrl(url: String) {
        val value = url.trim()
        prefs.edit().putString(KEY_WEBDAV_URL, value).apply()
        _webDavUrl.value = value
    }

    fun setWebDavUsername(username: String) {
        val value = username.trim()
        prefs.edit().putString(KEY_WEBDAV_USERNAME, value).apply()
        _webDavUsername.value = value
    }

    fun setWebDavPassword(password: String) {
        prefs.edit().putString(KEY_WEBDAV_PASSWORD, password).apply()
        _webDavPassword.value = password
    }

    fun setWebDavEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEBDAV_ENABLED, enabled).apply()
        _webDavEnabled.value = enabled
    }

    fun recordSync(result: SyncResult) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_WEBDAV_LAST_SYNC_AT, now).apply()
        _lastSyncAt.value = now
        _lastSyncResult.value = result
    }

    fun clearSyncResult() {
        _lastSyncResult.value = null
    }

    companion object {
        const val KEY_SHOW_ADULT_CONTENT = "show_adult_content"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HISTORY_RETENTION_DAYS = "history_retention_days"
        const val KEY_AUTO_CHECK_ENABLED = "auto_check_update_enabled"
        const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
        const val KEY_NOVEL_FONT_SIZE = "novel_font_size"
        const val KEY_NOVEL_READER_LIGHT = "novel_reader_light"
        const val KEY_NOVEL_PROGRESS_PREFIX = "novel_progress_"
        const val KEY_CUSTOM_BACKGROUND_PATH = "custom_background_path"
        const val KEY_BACKGROUND_DIM = "background_dim"
        const val KEY_BACKGROUND_SCRIM_DARK = "background_scrim_dark"
        const val KEY_BACKGROUND_SCRIM_LIGHT = "background_scrim_light"
        /** 首页背景水平/垂直归一化偏移与缩放：双图层引入后仅作为
         * 独立背景图（backdrop）的取景参数；跟随模式下由头部层参数驱动。 */
        const val KEY_BACKGROUND_OFFSET_X = "background_offset_x"
        const val KEY_BACKGROUND_OFFSET_Y = "background_offset_y"
        const val KEY_BACKGROUND_IMG_WIDTH = "background_img_width"
        const val KEY_BACKGROUND_IMG_HEIGHT = "background_img_height"
        const val KEY_BACKGROUND_SCALE = "background_scale"
        const val KEY_BACKGROUND_BLUR = "background_blur"
        const val KEY_BACKGROUND_HERO = "background_hero"

        /** 头部层独立取景参数 */
        const val KEY_HERO_OFFSET_X = "hero_offset_x"
        const val KEY_HERO_OFFSET_Y = "hero_offset_y"
        const val KEY_HERO_SCALE = "hero_scale"

        /** 独立背景图（null = 跟随头部图） */
        const val KEY_BACKDROP_PATH = "backdrop_path"
        const val KEY_BACKDROP_IMG_WIDTH = "backdrop_img_width"
        const val KEY_BACKDROP_IMG_HEIGHT = "backdrop_img_height"

        /** 双图层迁移标记：老版本只有一套取景参数，首次启动复制为头部层初值 */
        const val KEY_DUAL_LAYER_MIGRATED = "dual_layer_migrated"

        /** 小说阅读器字号范围与默认值（sp） */
        const val NOVEL_FONT_MIN = 13f
        const val NOVEL_FONT_MAX = 24f
        const val NOVEL_FONT_DEFAULT = 16f

        /** 自定义背景压暗范围与默认值（0~1） */
        const val BACKGROUND_DIM_MIN = 0f
        const val BACKGROUND_DIM_MAX = 0.8f
        const val BACKGROUND_DIM_DEFAULT = 0.35f

        /** 自定义背景缩放范围与默认值（1~1.5）。
         * 下限 1.0：缩放只负责放大取景，铺满由 Crop 保证，避免缩小露边。
         * 仅作用于独立背景图；头部层用 HERO_SCALE_*（允许 <1 画框式）。 */
        const val BACKGROUND_SCALE_MIN = 1.0f
        const val BACKGROUND_SCALE_MAX = 1.5f
        const val BACKGROUND_SCALE_DEFAULT = 1.0f

        /** 头部层缩放范围与默认值：≥1 满铺裁切，<1 整幅缩小为画框式 */
        const val HERO_SCALE_MIN = 0.5f
        const val HERO_SCALE_MAX = BACKGROUND_SCALE_MAX
        const val HERO_SCALE_DEFAULT = 1.0f

        /** 内容区背景模糊强度范围与默认值（dp） */
        const val BACKGROUND_BLUR_MIN = 0f
        const val BACKGROUND_BLUR_MAX = 48f
        const val BACKGROUND_BLUR_DEFAULT = 32f

        /** 头部清晰区高度比例范围与默认值（占屏幕高度） */
        const val BACKGROUND_HERO_MIN = 0.22f
        const val BACKGROUND_HERO_MAX = 0.45f
        const val BACKGROUND_HERO_DEFAULT = 0.34f

        /** AI 翻译设置 */
        const val KEY_AI_TRANSLATE_ENABLED = "ai_translate_enabled"
        const val KEY_LLM_BASE_URL = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LLM_NOVEL_BASE_URL = "llm_novel_base_url"
        const val KEY_LLM_NOVEL_MODEL = "llm_novel_model"
        const val KEY_CATALOG_REMOTE_URL = "llm_catalog_remote_url"
        const val KEY_CATALOG_ENC_KEY = "llm_catalog_enc_key"
        const val KEY_CATALOG_SOURCES = "llm_catalog_sources"

        /** WebDAV 同步设置 */
        const val KEY_WEBDAV_URL = "webdav_url"
        const val KEY_WEBDAV_USERNAME = "webdav_username"
        const val KEY_WEBDAV_PASSWORD = "webdav_password"
        const val KEY_WEBDAV_ENABLED = "webdav_enabled"
        const val KEY_WEBDAV_LAST_SYNC_AT = "webdav_last_sync_at"

        /**
         * 列表外自定义模型的兜底端点；文本模型 id 默认空串 = 未显式选择，
         * 实际默认由远程目录 defaults.roles 声明（官方目录 text → Qwen3-8B，novel → DeepSeek）。
         */
        const val LLM_BASE_URL_DEFAULT = "https://open.bigmodel.cn/api/paas/v4"
        const val LLM_MODEL_DEFAULT = ""

        /**
         * 内置远程模型目录：AES-256-GCM 密文由 piku-models 仓库的 CI 发布到
         * catalog 分支，jsDelivr 主用（国内可达），GitHub raw 作为被墙时的直连回退。
         */
        const val CATALOG_URL_DEFAULT = "https://cdn.jsdelivr.net/gh/NLick47/piku-models@catalog/models.enc.json"
        const val CATALOG_URL_FALLBACK = "https://raw.githubusercontent.com/NLick47/piku-models/catalog/models.enc.json"
    }
}