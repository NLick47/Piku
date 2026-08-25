package com.piku.client.ui.detail

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.data.local.ImageSaver
import com.piku.client.data.local.WorkPasswordRepository
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.DetailRepository
import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.data.repository.FollowResult
import com.piku.client.data.repository.ReactionResult
import com.piku.client.data.repository.ThumbnailResolver
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.translation.TranslationRepository
import com.piku.client.data.remote.translation.ModelCatalogRepository
import com.piku.client.data.remote.translation.ModelEntry
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import com.piku.client.domain.model.mergeTranslatedFields
import com.piku.client.domain.usecase.LoadWorkDetailUseCase
import com.piku.client.domain.usecase.LoadWorkFullImagesUseCase
import com.piku.client.domain.usecase.ObserveAuthStatusUseCase
import com.piku.client.domain.usecase.ObserveCustomTagsUseCase
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.ObserveLanguageUseCase
import com.piku.client.domain.usecase.RecordHistoryUseCase
import com.piku.client.domain.usecase.AddCustomTagUseCase
import com.piku.client.domain.usecase.RemoveCustomTagUseCase
import com.piku.client.R
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** 可独立切换原文/译文的文本字段 */
enum class TranslateField { TITLE, DESCRIPTION, AUTHOR_PROFILE, TAGS, NOVEL }

data class DetailUiState(
    val detail: WorkDetail? = null,
    val fullImageUrls: List<String> = emptyList(),
    val fullImagesLoading: Boolean = false,
    val loading: Boolean = false,
    val errorRes: Int? = null,
    val isFavorite: Boolean = false,
    val favoriteFolders: List<FavoriteFolder> = emptyList(),
    val workFavoriteFolderIds: Set<Long> = emptySet(),
    val shareUrl: String = "",
    val password: String = "",
    val passwordLoading: Boolean = false,
    val loggedIn: Boolean = false,
    val reactionSending: Boolean = false,
    val reactionFeedbackRes: Int? = null,
    val followSending: Boolean = false,
    val followFeedbackRes: Int? = null,
    val favoriteFeedbackRes: Int? = null,
    val savingImage: Boolean = false,
    val saveFeedbackRes: Int? = null,
    /** 个人自定义标签（用于详情页把作品标签收藏进个人标签） */
    val customTags: List<String> = emptyList(),
    val tagFeedbackRes: Int? = null,
    /** 底部菜单一次性新手引导（仅首次进入详情页显示） */
    val guideVisible: Boolean = false,
    /** 全屏小说阅读器：当前是否打开 */
    val novelReaderOpen: Boolean = false,
    /** 全屏小说阅读器：字号（sp） */
    val novelFontSize: Float = NOVEL_FONT_DEFAULT,
    /** 全屏小说阅读器：浅色模式（米色纸），独立于系统主题 */
    val novelReaderLight: Boolean = true,
    /** 全屏小说阅读器：该作品已保存的阅读进度（百分比 0~100） */
    val novelProgressPercent: Int = 0,
    /** AI 翻译开关是否开启（只控制自动翻译，不影响手动入口） */
    val aiTranslateEnabled: Boolean = false,
    /** 配置了可用 key（顶栏手动翻译按钮的显示条件） */
    val canTranslate: Boolean = false,
    /** 译文拉取中 */
    val translating: Boolean = false,
    /** 手动翻译失败的轻提示（snackbar，可重试）；自动路径保持静默 */
    val translateFeedbackRes: Int? = null,
    /** 是否展示"换模型重翻"的模型选择弹窗 */
    val showModelPicker: Boolean = false,
    /** 当前这轮拉取包含长篇正文（阅读器入口触发）；用于精确驱动阅读器的加载态 */
    val fetchingNovelText: Boolean = false,
    /**
     * 顶栏全局开关（方案 B）：true = 整页显示译文。
     * 默认 false（显示原文）；自动翻译首次拿到译文时自动置为 true
     * （见 [DetailViewModel.translate] 的 showAfter），之后完全由用户切换控制。
     */
    val showTranslationAll: Boolean = false,
    /**
     * 单字段覆盖（方案 C）：仅记录与 [showTranslationAll] 相反的字段。
     * 顶栏切换时清空，保证"全局切换"语义直观。
     */
    val fieldOverrides: Set<TranslateField> = emptySet(),
) {
    /** 该字段当前是否显示译文：全局态异或单字段覆盖 */
    fun showTranslation(field: TranslateField): Boolean =
        showTranslationAll != (field in fieldOverrides)

    /** 是否有任何译文可展示（决定顶栏按钮高亮与各字段 chip 是否出现） */
    val hasTranslation: Boolean
        get() = detail?.translated?.hasAny == true

    /** 查看器图片对：缩略图 + 原图（未就绪时为 null），长度不等时互相兜底 */
    val viewerImages: List<ViewerImage>
        get() {
            val detail = detail ?: return emptyList()
            if (detail.imageUrls.isEmpty()) return emptyList()
            val thumbs = detail.imageUrls
            val fulls = fullImageUrls
            val size = maxOf(thumbs.size, fulls.size)
            return List(size) { i ->
                ViewerImage(
                    thumbnailUrl = thumbs.getOrElse(i) { thumbs.last() },
                    fullUrl = fulls.getOrNull(i),
                )
            }
        }
}

/** 查看器单页图片：缩略图常驻打底，原图就绪后替换 */
data class ViewerImage(
    val thumbnailUrl: String,
    val fullUrl: String?,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val loadWorkDetailUseCase: LoadWorkDetailUseCase,
    private val loadWorkFullImagesUseCase: LoadWorkFullImagesUseCase,
    private val favoriteRepository: FavoriteRepository,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val observeAuthStatusUseCase: ObserveAuthStatusUseCase,
    private val detailRepository: DetailRepository,
    private val authRepository: AuthRepository,
    private val thumbnailResolver: ThumbnailResolver,
    private val workPasswordRepository: WorkPasswordRepository,
    private val imageSaver: ImageSaver,
    private val recordHistoryUseCase: RecordHistoryUseCase,
    private val observeCustomTagsUseCase: ObserveCustomTagsUseCase,
    private val addCustomTagUseCase: AddCustomTagUseCase,
    private val removeCustomTagUseCase: RemoveCustomTagUseCase,
    private val settingsRepository: SettingsRepository,
    private val translationRepository: TranslationRepository,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val observeLanguageUseCase: ObserveLanguageUseCase,
    private val prefs: SharedPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val authorId: Long = savedStateHandle["authorId"] ?: -1L
    val workId: Long = savedStateHandle["workId"] ?: -1L
    private val work = Work(
        id = workId,
        authorId = authorId,
        authorName = "",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "",
        // 来源页（feed/历史/收藏/相关作品）缩略图：密码作品未解锁时用它回填历史/收藏
        thumbnailUrl = savedStateHandle["thumb"] ?: "",
        imageCount = 0,
        r18 = false,
    )

    private val showBottomGuide = !prefs.getBoolean(KEY_BOTTOM_GUIDE_SHOWN, false)

    /** 上次手动翻译的入口：失败提示的重试按原样重发（顶栏=false / 阅读器长文=true） */
    private var lastManualTranslateIncludeNovel = false
    /** 上次"换模型重翻"所选模型，供失败重试复用 */
    private var lastForcedEntry: ModelEntry? = null

    private val _uiState = MutableStateFlow(
        DetailUiState(
            shareUrl = "https://poipiku.com/$authorId/$workId.html",
            guideVisible = showBottomGuide,
            novelProgressPercent = settingsRepository.getNovelProgress(workId),
            canTranslate = translationRepository.hasKey(),
        ),
    )
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    /** 目录模型列表（用于"换模型重翻"弹窗），不含分类硬限制，全部可用模型都可选 */
    private val _catalogModels = MutableStateFlow(modelCatalogRepository.models.value)
    val catalogModels: StateFlow<List<ModelEntry>> = _catalogModels.asStateFlow()

    /** 关闭底部菜单新手引导 */
    fun dismissGuide() {
        if (!_uiState.value.guideVisible) return
        _uiState.update { it.copy(guideVisible = false) }
    }

    /** 打开/关闭全屏小说阅读器 */
    fun setNovelReaderOpen(open: Boolean) {
        _uiState.update { it.copy(novelReaderOpen = open) }
    }

    /** 保存该作品的阅读进度（百分比 0~100） */
    fun saveNovelProgress(percent: Int) {
        settingsRepository.setNovelProgress(workId, percent)
    }

    /** 调整阅读器字号（持久化） */
    fun setNovelFontSize(size: Float) {
        settingsRepository.setNovelFontSize(size)
    }

    /** 切换阅读器配色（浅米底深字 / 深底浅字，独立于系统主题，持久化） */
    fun setNovelReaderLight(light: Boolean) {
        settingsRepository.setNovelReaderLight(light)
    }

    init {
        // “已显示”标记在展示前就写入：之前是自动隐藏完成才写，用户提前离开详情页
        // 或进程被杀（如卡死后强杀）会导致标记永远存不上，每篇详情页都重复弹（历史 bug）
        if (showBottomGuide) prefs.edit().putBoolean(KEY_BOTTOM_GUIDE_SHOWN, true).apply()
        viewModelScope.launch {
            observeFavoriteIdsUseCase().collect { ids ->
                _uiState.update { it.copy(isFavorite = workId in ids) }
            }
        }
        viewModelScope.launch {
            favoriteRepository.observeFolders().collect { folders ->
                _uiState.update { it.copy(favoriteFolders = folders) }
            }
        }
        viewModelScope.launch {
            favoriteRepository.observeWorkFolderIds(workId).collect { folderIds ->
                _uiState.update { it.copy(workFavoriteFolderIds = folderIds) }
            }
        }
        viewModelScope.launch {
            settingsRepository.novelFontSize.collect { size ->
                _uiState.update { it.copy(novelFontSize = size) }
            }
        }
        viewModelScope.launch {
            settingsRepository.novelReaderLight.collect { light ->
                _uiState.update { it.copy(novelReaderLight = light) }
            }
        }
        viewModelScope.launch {
            // 自动重登成功后重新加载详情（登录墙作品的真实图依赖有效会话）
            authRepository.sessionRefreshed.collect {
                if (_uiState.value.detail != null) load()
            }
        }
        viewModelScope.launch {
            observeAuthStatusUseCase().collect { status ->
                val loggedIn = status == AuthStatus.LOGGED_IN
                val prevLoggedIn = _uiState.value.loggedIn
                _uiState.update { it.copy(loggedIn = loggedIn) }
                if (loggedIn != prevLoggedIn && _uiState.value.detail != null && loggedIn) {
                    load()
                }
            }
        }
        viewModelScope.launch {
            observeCustomTagsUseCase().collect { tags ->
                _uiState.update { it.copy(customTags = tags) }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiTranslateEnabled.collect { enabled ->
                _uiState.update { it.copy(aiTranslateEnabled = enabled) }
                // 开关是在详情页停留期间打开的：立刻补翻当前作品并自动呈现译文
                if (enabled && _uiState.value.detail?.translated == null) translate(showAfter = true)
            }
        }
        viewModelScope.launch {
            modelCatalogRepository.models.collect { models -> _catalogModels.value = models }
        }
        viewModelScope.launch {
            // key 可用性（远程目录内置）变化时同步手动按钮可见性；
            // 冷启动时目录常晚于首个详情页到达，此前打开的作品会被无 key 静默跳过——
            // key 一到位就对当前作品补翻，否则"开自动翻译却不翻"直到下次进页
            translationRepository.hasKeyFlow.collect { hasKey ->
                _uiState.update { state -> state.copy(canTranslate = hasKey) }
                if (hasKey && _uiState.value.aiTranslateEnabled &&
                    _uiState.value.detail?.translated == null
                ) {
                    translate(showAfter = true)
                }
            }
        }
        load()
    }

    /**
     * 顶栏全局切换（方案 B）：整页原文 ⇄ 译文。
     * 同时清掉单字段覆盖，避免"全局切了但个别字段没跟着变"的困惑。
     */
    fun toggleTranslationAll() {
        _uiState.update {
            it.copy(showTranslationAll = !it.showTranslationAll, fieldOverrides = emptySet())
        }
    }

    /** 单字段切换（方案 C）：只翻转该字段，与全局态相反时记录为覆盖 */
    fun toggleField(field: TranslateField) {
        _uiState.update { state ->
            val overrides = state.fieldOverrides.toMutableSet()
            if (field in overrides) overrides -= field else overrides += field
            state.copy(fieldOverrides = overrides)
        }
    }

    /**
     * 顶栏手动入口（最短路径）：
     * - 已有译文 → 整页原/译切换；
     * - 没有 → 立即翻译，并预先把展示态切到译文，翻完自动呈现。
     * 与自动翻译同范围（不含长正文）：长篇只在阅读器内按需拉取。
     */
    fun onTopBarTranslateClick() {
        if (_uiState.value.detail?.translated?.hasAny == true) {
            toggleTranslationAll()
            return
        }
        translate(requireAutoEnabled = false)
        _uiState.update { it.copy(showTranslationAll = true, fieldOverrides = emptySet()) }
    }

    /**
     * 阅读器内"原/译"切换（长正文的唯一翻译入口）：
     * - 正文尚无译文 → 此刻才发起拉取（显式意图才花额度），并预切到译文展示态；
     * - 已有（或原文本身为空）→ 仅切换显示。
     */
    fun onReaderTranslateToggle() {
        val detail = _uiState.value.detail ?: return
        val novelDone = detail.translated?.novelText != null || detail.novelText.isNullOrBlank()
        if (novelDone) {
            toggleField(TranslateField.NOVEL)
            return
        }
        translate(includeLongNovel = true, requireAutoEnabled = false)
        _uiState.update { it.copy(showTranslationAll = true, fieldOverrides = emptySet()) }
    }

    /** 一次翻译请求的参数快照：in-flight 期间收到的新请求记为待补跑 */
    private data class TranslateRequest(
        val includeLongNovel: Boolean,
        val requireAutoEnabled: Boolean,
        val showAfter: Boolean,
        /** 一次性重翻覆盖的模型（仅记快照，不写入默认设置） */
        val forcedEntry: ModelEntry? = null,
        /** 重翻强制切到译文视图（已存在译文时仍翻面，保证看到新结果） */
        val forceShow: Boolean = false,
    )

    /** 翻译在途时收到的新请求只保留最近一次，当前这轮完成后补跑，不再静默丢弃 */
    private var pendingTranslate: TranslateRequest? = null

    /**
     * 拉取译文（缓存优先，命中时不发网络请求）。
     * 自动路径失败静默：UI 继续显示原文，不弹错误打扰阅读；
     * 手动路径（顶栏/阅读器显式点击）失败给 snackbar 轻提示，可一键重试。
     *
     * 锁定占位页直接跳过：没有可翻的有效内容（判定与 [maybeAutoUnlock] 的
     * "已解锁"同款），白烧免费额度不说，还会占住 translating 让解锁完成后
     * 触发的那次翻译撞上 in-flight 守卫被吞——历史重进密码作品"解锁了却不翻"。
     *
     * @param includeLongNovel 长正文是否随本次一起翻；仅阅读器入口传 true
     * @param requireAutoEnabled 自动路径要求总开关打开；顶栏/阅读器的显式点击不受限
     * @param forcedEntry 一次性重翻：指定则短字段与小说正文都强制用此模型
     */
    private fun translate(
        includeLongNovel: Boolean = false,
        requireAutoEnabled: Boolean = true,
        showAfter: Boolean = false,
        forcedEntry: ModelEntry? = null,
        forceShow: Boolean = false,
    ) {
        val detail = _uiState.value.detail ?: return
        if (_uiState.value.translating) {
            pendingTranslate = TranslateRequest(includeLongNovel, requireAutoEnabled, showAfter, forcedEntry, forceShow)
            return
        }
        if (detail.passwordProtected && detail.imageUrls.isEmpty() && detail.novelText.isBlank()) return
        if (requireAutoEnabled && !settingsRepository.aiTranslateEnabled.value) return
        if (!translationRepository.hasKey()) {
            Log.d("PikuDiag", "translate skip work=$workId: no api key (catalog pending?)")
            return
        }
        val manual = !requireAutoEnabled
        if (manual) {
            lastManualTranslateIncludeNovel = includeLongNovel
            lastForcedEntry = forcedEntry
        }
        viewModelScope.launch {
            _uiState.update { it.copy(translating = true, fetchingNovelText = includeLongNovel) }
            val outcome = runCatching {
                translationRepository.translate(
                    detail, observeLanguageUseCase().value, includeLongNovel, forcedEntry,
                )
            }.onFailure { error ->
                Log.d("PikuDiag", "translate fail work=$workId: ${error.message}")
            }.getOrNull()
            // 只有引擎/网络层面的真失败才提示；预检透传等“正常无译文”保持静默
            val failed = manual && (outcome == null || outcome.failed)
            val fields = outcome?.takeUnless { it.failed }?.fields
            _uiState.update { state ->
                // 期间可能已重新加载/解锁成 detail，按当前 detail 回填
                val current = state.detail ?: return@update state.copy(
                    translating = false,
                    fetchingNovelText = false,
                    translateFeedbackRes = if (failed) R.string.detail_translate_failed else null,
                )
                val merged = mergeTranslatedFields(current.translated, fields)
                // 自动路径在“本次首次拿到译文”时翻面到译文视图；已有译文后的
                // 重跑（解锁补翻、重登刷新）不翻转——此时 showTranslationAll=false
                // 可能正是用户刚切回原文的显式选择，不能覆盖。translated == null 时
                // 用户不可能做过原/译选择（chip 与顶栏按钮都尚未出现），翻面安全。
                val shouldShow = merged != null &&
                    ((showAfter && current.translated == null) || forceShow)
                state.copy(
                    translating = false,
                    fetchingNovelText = false,
                    detail = if (merged != null) current.copy(translated = merged) else current,
                    showTranslationAll = if (shouldShow) true else state.showTranslationAll,
                    translateFeedbackRes = if (failed) R.string.detail_translate_failed else null,
                )
            }
            // 补跑在途期间记下的最近一次请求（先清空再跑，防循环）
            pendingTranslate?.let { pending ->
                pendingTranslate = null
                translate(
                    pending.includeLongNovel,
                    pending.requireAutoEnabled,
                    pending.showAfter,
                    pending.forcedEntry,
                    pending.forceShow,
                )
            }
        }
    }

    /**
     * 换模型重翻：一次性用指定模型重翻当前作品（含小说正文如有），不写入默认设置。
     * 失败照常给 snackbar 可重试；重试会复用所选模型。
     */
    fun reTranslateWith(entry: ModelEntry) {
        val hasNovel = _uiState.value.detail?.novelText?.isNotBlank() == true
        translate(
            includeLongNovel = hasNovel,
            requireAutoEnabled = false,
            showAfter = true,
            forcedEntry = entry,
            forceShow = true,
        )
        _uiState.update { it.copy(showModelPicker = false) }
    }

    /** 打开"换模型重翻"模型选择弹窗 */
    fun openModelPicker() {
        if (!translationRepository.hasKey()) return
        _uiState.update { it.copy(showModelPicker = true) }
    }

    /** 关闭模型选择弹窗 */
    fun dismissModelPicker() {
        _uiState.update { it.copy(showModelPicker = false) }
    }

    /** 手动翻译失败的 snackbar 重试：按上次入口原样重发（含一次性重翻模型） */
    fun retryLastTranslate() {
        val forced = lastForcedEntry
        if (forced != null) {
            reTranslateWith(forced)
            return
        }
        if (lastManualTranslateIncludeNovel) onReaderTranslateToggle() else onTopBarTranslateClick()
    }

    fun clearTranslateFeedback() {
        _uiState.update { it.copy(translateFeedbackRes = null) }
    }

    fun retry() = load()

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun submitPassword() {
        val pwd = _uiState.value.password
        if (pwd.isBlank() || _uiState.value.passwordLoading) return
        // 复用已解析的锁页 detail，跳过重复的详情页 HTML 请求；解锁只发一次 append POST
        val existing = _uiState.value.detail
        viewModelScope.launch {
            _uiState.update { it.copy(passwordLoading = true) }
            loadWorkDetailUseCase(work, pwd, existing)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(detail = detail, passwordLoading = false)
                    }
                    recordHistory(detail)
                    // 解锁成功后后台升级全尺寸原图（不阻塞缩略图展示）；
                    // 匿名时 ShowIllustDetailF 恒 -2、账号受限（-4）时跳过无效请求链
                    if (!detail.passwordError && !detail.unlockBlocked && _uiState.value.loggedIn) {
                        loadFullImages(pwd)
                    }
                    // 解锁后才拿到正文/描述，此时才有东西可翻；自动开启则翻完直接呈现。
                    // 密码错误也是 onSuccess 返回（passwordError=true）：不得触发翻译，
                    // 否则用户每输错一次就重跑一轮请求
                    if (!detail.passwordError) {
                        translate(showAfter = settingsRepository.aiTranslateEnabled.value)
                    }
                }
                .onFailure { error ->
                    Log.d(
                        "PikuDiag",
                        "unlock fail work=$workId error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update { it.copy(passwordLoading = false) }
                }
        }
    }

    /**
     * 自动进入：密码作品 + 未解锁 + 用户未在手动输入 + 存在已保存密码（仅服务端验证
     * 成功时写入，不可被查看/修改）→ 自动解锁。失败（-2，作者已改密码）时清除失效
     * 记录并显示密码框，由用户手动输入新密码，成功后重新保存（自愈，不会死循环）。
     */
    private fun maybeAutoUnlock(detail: WorkDetail) {
        val state = _uiState.value
        Log.d(
            "PikuDiag",
            "maybeAutoUnlock work=$workId locked=${detail.passwordProtected} imgs=${detail.imageUrls.size} " +
                "typedPw=${state.password.isNotBlank()} loading=${state.loading} pwLoading=${state.passwordLoading}",
        )
        if (!detail.passwordProtected) return
        if (detail.imageUrls.isNotEmpty() || detail.novelText.isNotBlank()) return // 已解锁（图片作品看图列表，文字作品看正文）
        if (state.password.isNotBlank()) return // 用户正在输入，不打扰
        if (state.passwordLoading || state.loading) return
        viewModelScope.launch {
            val saved = workPasswordRepository.getPassword(workId)
            Log.d("PikuDiag", "maybeAutoUnlock work=$workId saved=${saved != null}")
            if (saved.isNullOrBlank()) return@launch
            _uiState.update { it.copy(passwordLoading = true) }
            loadWorkDetailUseCase(work, saved, detail)
                .onSuccess { unlocked ->
                    val failed = unlocked.passwordError
                    Log.d(
                        "PikuDiag",
                        "maybeAutoUnlock work=$workId done failed=$failed urls=${unlocked.imageUrls.size}",
                    )
                    _uiState.update { it.copy(detail = unlocked, passwordLoading = false) }
                    recordHistory(unlocked)
                    if (failed) {
                        // 保存的密码已失效：清除，让用户手动输入新密码
                        workPasswordRepository.deletePassword(workId)
                    } else if (!unlocked.unlockBlocked && _uiState.value.loggedIn) {
                        loadFullImages(saved)
                    }
                    if (!failed) translate(showAfter = settingsRepository.aiTranslateEnabled.value)
                }
                .onFailure {
                    Log.d("PikuDiag", "maybeAutoUnlock work=$workId network/parse failure, keep password")
                    _uiState.update { it.copy(passwordLoading = false) }
                }
        }
    }

    fun loadFullImages(password: String = "") {
        val state = _uiState.value
        if (state.fullImageUrls.isNotEmpty() || state.fullImagesLoading) return
        val detail = state.detail ?: return
        if (detail.warning) {
            _uiState.update { it.copy(fullImageUrls = detail.imageUrls) }
            return
        }
        // 未解锁的密码作品拿不到原图，跳过无效请求（也不烧 append 限速槽）
        if (detail.passwordProtected && password.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(fullImagesLoading = true) }
            loadWorkFullImagesUseCase(work, password)
                .onSuccess { urls ->
                    _uiState.update { it.copy(fullImageUrls = urls, fullImagesLoading = false) }
                }
                .onFailure { error ->
                    Log.d(
                        "PikuDiag",
                        "loadFullImages fail work=$workId error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update { it.copy(fullImagesLoading = false) }
                }
        }
    }

    /** 快速收藏切换：单击星标 → 加入/移出默认收藏夹，并给出提示。 */
    fun quickFavorite() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            val added = favoriteRepository.toggleFavorite(currentWork(detail))
            _uiState.update {
                it.copy(
                    favoriteFeedbackRes = if (added) {
                        R.string.detail_favorite_added
                    } else {
                        R.string.detail_favorite_removed
                    },
                )
            }
        }
    }

    fun clearFavoriteFeedback() {
        _uiState.update { it.copy(favoriteFeedbackRes = null) }
    }

    /**
     * 把作品标签加入/移出个人自定义标签（用于首页标签筛选快捷入口）。
     * 已存在则移除，否则添加，操作后给出 snackbar 反馈。
     */
    fun toggleCustomTag(tag: String) {
        val state = _uiState.value
        viewModelScope.launch {
            if (tag in state.customTags) {
                removeCustomTagUseCase(tag)
                _uiState.update { it.copy(tagFeedbackRes = R.string.detail_tag_removed) }
            } else {
                addCustomTagUseCase(tag)
                _uiState.update { it.copy(tagFeedbackRes = R.string.detail_tag_added) }
            }
        }
    }

    fun clearTagFeedback() {
        _uiState.update { it.copy(tagFeedbackRes = null) }
    }

    /**
     * 保存第 [page] 张图片到系统相册。
     * 原图未就绪时先触发全尺寸加载并限时等待，超时/拿不到原图则退回缩略图，
     * 保证长按一定能存到图。
     */
    fun saveImage(page: Int) {
        val state = _uiState.value
        if (state.savingImage) return
        val detail = state.detail ?: return
        val fallbackUrl = detail.imageUrls.getOrNull(page) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(savingImage = true) }
            if (!detail.passwordProtected && !detail.warning) {
                if (state.viewerImages.getOrNull(page)?.fullUrl == null) {
                    loadFullImages()
                    withTimeoutOrNull(IMAGE_WAIT_MILLIS) {
                        _uiState.filter { it.fullImageUrls.isNotEmpty() }.first()
                    }
                }
            }
            val url = _uiState.value.viewerImages.getOrNull(page)?.fullUrl ?: fallbackUrl
            val result = runCatching {
                imageSaver.save(url, "Piku_${workId}_${page + 1}")
            }
            _uiState.update {
                it.copy(
                    savingImage = false,
                    saveFeedbackRes = if (result.isSuccess) {
                        R.string.detail_save_saved
                    } else {
                        R.string.detail_save_failed
                    },
                )
            }
        }
    }

    fun clearSaveFeedback() {
        _uiState.update { it.copy(saveFeedbackRes = null) }
    }

    fun toggleFavoriteFolder(folderId: Long) {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            favoriteRepository.toggleFolder(currentWork(detail), folderId)
        }
    }

    fun createFavoriteFolder(name: String) {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            favoriteRepository.createFolder(name, currentWork(detail))
        }
    }

    private fun currentWork(detail: WorkDetail): Work = work.copy(
        title = detail.title,
        authorName = detail.authorName,
        authorAvatarUrl = detail.authorAvatarUrl.ifBlank { null },
        categoryName = detail.categoryName,
        thumbnailUrl = stableThumbnail(detail),
        imageCount = detail.imageUrls.size,
        r18 = detail.r18,
    )

    /**
     * 历史/收藏使用的稳定缩略图，优先级：
     * 1. 已回填的真实 _360 缩略图（解锁后 append 返回的 640 图转换，稳定不过期）
     * 2. 详情页第一张图（普通作品为 640 缩略图）
     * 3. 来源页占位图（密码/warning 作品未解锁时 feed 的 publish_pass 等占位图），
     *    保证历史/收藏记录永远有图
     */
    private fun stableThumbnail(detail: WorkDetail): String =
        thumbnailResolver.thumbFor(work) ?: detail.imageUrls.firstOrNull() ?: work.thumbnailUrl

    fun sendReaction(emoji: String) {
        val state = _uiState.value
        if (state.reactionSending) return
        val uid = authRepository.currentUserId()
        val detail = state.detail ?: return
        if (uid == null) {
            _uiState.update { it.copy(reactionFeedbackRes = R.string.detail_reaction_login_hint) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(reactionSending = true) }
            val result = detailRepository.sendReaction(work.id, emoji, uid)
            _uiState.update {
                it.copy(
                    reactionSending = false,
                    reactionFeedbackRes = when (result) {
                        ReactionResult.Success -> R.string.detail_reaction_sent
                        ReactionResult.LimitReached -> R.string.detail_reaction_limit
                        is ReactionResult.Failure -> R.string.detail_reaction_send_failed
                    },
                )
            }
            if (result is ReactionResult.Success) {
                _uiState.update { state ->
                    val detail = state.detail ?: return@update state
                    state.copy(
                        detail = detail.copy(
                            reactions = (detail.reactions + emoji).distinct(),
                            reactionCounts = detail.reactionCounts +
                                (emoji to ((detail.reactionCounts[emoji] ?: 0) + 1)),
                            reactionCount = detail.reactionCount + 1,
                        ),
                    )
                }
            }
        }
    }

    fun clearReactionFeedback() {
        _uiState.update { it.copy(reactionFeedbackRes = null) }
    }

    /**
     * 关注/取消关注作者。关注状态以详情页解析出的 [WorkDetail.followed] 为准，
     * 切换成功后原地更新，等待下次加载详情时由服务端渲染校正。
     */
    fun toggleFollow() {
        val state = _uiState.value
        if (state.followSending || state.detail == null) return
        if (!state.loggedIn) {
            _uiState.update { it.copy(followFeedbackRes = R.string.detail_follow_login_hint) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(followSending = true) }
            val result = detailRepository.updateFollow(authorId)
            _uiState.update { s ->
                val detail = s.detail ?: return@update s
                s.copy(
                    followSending = false,
                    followFeedbackRes = when (result) {
                        FollowResult.Followed -> R.string.detail_follow_sent
                        FollowResult.Unfollowed -> R.string.detail_unfollow_sent
                        FollowResult.NotLoggedIn -> R.string.detail_follow_login_hint
                        is FollowResult.Failure -> R.string.detail_follow_failed
                    },
                    detail = when (result) {
                        FollowResult.Followed -> detail.copy(followed = true)
                        FollowResult.Unfollowed -> detail.copy(followed = false)
                        else -> detail
                    },
                )
            }
        }
    }

    fun clearFollowFeedback() {
        _uiState.update { it.copy(followFeedbackRes = null) }
    }

    private fun recordHistory(detail: WorkDetail) {
        viewModelScope.launch {
            recordHistoryUseCase(
                work.copy(
                    authorName = detail.authorName,
                    authorAvatarUrl = detail.authorAvatarUrl.ifBlank { null },
                    categoryName = detail.categoryName,
                    title = detail.title,
                    thumbnailUrl = stableThumbnail(detail),
                    imageCount = detail.imageUrls.size,
                    r18 = detail.r18,
                ),
            )
        }
    }

    private fun load() {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorRes = null) }
            // 保留已输入的密码：自动重登/刷新详情后已解锁作品不会重新锁回
            val retainedPassword = _uiState.value.password
            loadWorkDetailUseCase(work, retainedPassword)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(detail = detail, loading = false, errorRes = null)
                    }
                    recordHistory(detail)
                    loadFullImages(retainedPassword)
                    maybeAutoUnlock(detail)
                    translate(showAfter = settingsRepository.aiTranslateEnabled.value)
                }
                .onFailure { error ->
                    Log.d(
                        "PikuDiag",
                        "loadDetail fail work=$workId error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        it.copy(
                            loading = false,
                            errorRes = (error as? AppError)?.toFeedErrorRes(),
                        )
                    }
                }
        }
    }

    private companion object {
        /** 长按保存时等待原图加载的最长时间*/
        const val IMAGE_WAIT_MILLIS = 8_000L

        /** 底部菜单新手引导已展示标记 */
        const val KEY_BOTTOM_GUIDE_SHOWN = "detail_bottom_guide_shown"
    }
}
