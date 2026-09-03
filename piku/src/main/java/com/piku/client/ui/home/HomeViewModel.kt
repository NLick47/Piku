package com.piku.client.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.local.BlockedUser
import com.piku.client.data.local.BlockedContentRepository
import com.piku.client.data.local.CatalogSource
import com.piku.client.data.local.CatalogSourceCodec
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.newCatalogSourceId
import com.piku.client.data.remote.GitHubRelease
import com.piku.client.data.remote.translation.ModelCatalogRepository
import com.piku.client.data.remote.translation.ModelEntry
import com.piku.client.data.remote.translation.RoleDefaultIds
import com.piku.client.data.remote.translation.TranslationRepository
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.ThumbnailResolver
import com.piku.client.data.repository.WebDavSyncRepository
import com.piku.client.data.repository.SyncResult
import com.piku.client.data.repository.TestConnectionState
import com.piku.client.data.repository.SyncState
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.ThemeMode
import com.piku.client.domain.model.UserProfile
import com.piku.client.domain.model.Work
import com.piku.client.domain.usecase.CheckForUpdateUseCase
import com.piku.client.domain.usecase.LoadFeedUseCase
import com.piku.client.domain.usecase.LoadFollowFeedUseCase
import com.piku.client.domain.usecase.LoadPopularFeedUseCase
import com.piku.client.domain.usecase.LoadRandomFeedUseCase
import com.piku.client.domain.usecase.LoadTagFeedUseCase
import com.piku.client.domain.usecase.ObserveAdultContentUseCase
import com.piku.client.domain.usecase.ObserveAutoCheckEnabledUseCase
import com.piku.client.domain.usecase.ObserveBackgroundDimUseCase
import com.piku.client.domain.usecase.ObserveCustomBackgroundUseCase
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.ObserveHistoryRetentionUseCase
import com.piku.client.domain.usecase.ObserveLanguageUseCase
import com.piku.client.domain.usecase.ObserveThemeModeUseCase
import com.piku.client.domain.usecase.RestoreAdultContentUseCase
import com.piku.client.domain.usecase.SelectTranslateModelUseCase
import com.piku.client.domain.usecase.SelectTranslateNovelModelUseCase
import com.piku.client.domain.usecase.SetAdultContentUseCase
import com.piku.client.domain.usecase.SetAiTranslateEnabledUseCase
import com.piku.client.domain.usecase.SetAutoCheckEnabledUseCase
import com.piku.client.domain.usecase.SetBackgroundDimUseCase
import com.piku.client.domain.usecase.SetCustomBackgroundUseCase
import com.piku.client.domain.usecase.SetHistoryRetentionUseCase
import com.piku.client.domain.usecase.SetLanguageUseCase
import com.piku.client.domain.usecase.SetThemeModeUseCase
import com.piku.client.domain.usecase.ToggleFavoriteUseCase
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FeedTab { HOT, LATEST, FOLLOW, RANDOM }

/** 检查更新状态机：弹层内结果区据此渲染 */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object Latest : UpdateCheckState
    data class Available(val release: GitHubRelease) : UpdateCheckState
    data object Failed : UpdateCheckState
}

/** 远程模型列表刷新状态：列表来源弹层的结果区据此渲染 */
sealed interface CatalogRefreshState {
    data object Idle : CatalogRefreshState
    data object Loading : CatalogRefreshState
    data class Success(val modelCount: Int) : CatalogRefreshState
    data object Failed : CatalogRefreshState
}

/** 同时驻留的 FeedLoader 上限：每个 loader 兼作该 feed 的内存缓存 */
private const val MAX_LOADERS = 12

data class HomeUiState(
    val feedTab: FeedTab = FeedTab.LATEST,
    val category: PoipikuCategory = PoipikuCategory.ALL,
    /** 内容换血计数：tab/分类/标签切换、缓存恢复、重载、洗牌时 +1，UI 据此回顶 */
    val feedEpoch: Int = 0,
    val works: List<Work> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    /** 首页内容屏蔽：屏蔽标签关键词与屏蔽用户（抽屉入口面板管理） */
    val blockedTags: List<String> = emptyList(),
    val blockedUsers: List<BlockedUser> = emptyList(),
    val adultEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val historyRetentionDays: Int = 0,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val userProfile: UserProfile? = null,
    val userAvatarUrl: String? = null,
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    val currentTag: String? = null,
    val refreshNotice: Int? = null,
    /** 关注页未登录：不发请求，直接展示登录引导 */
    val followNeedLogin: Boolean = false,
    val autoCheckEnabled: Boolean = true,
    /** 发现的新版本（首页横幅展示），null 表示无 */
    val updateBanner: GitHubRelease? = null,
    /** 检查更新状态（弹层结果区渲染） */
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    val customBackgroundPath: String? = null,
    val backgroundDim: Float = SettingsRepository.BACKGROUND_DIM_DEFAULT,
    val backgroundErrorRes: Int? = null,
    val backgroundScrimDark: Int? = null,
    val backgroundScrimLight: Int? = null,
    val backgroundOffsetX: Float = 0f,
    val backgroundOffsetY: Float = 0f,
    val backgroundScale: Float = SettingsRepository.BACKGROUND_SCALE_DEFAULT,
    val backgroundBlur: Float = SettingsRepository.BACKGROUND_BLUR_DEFAULT,
    val backgroundHeroFraction: Float = SettingsRepository.BACKGROUND_HERO_DEFAULT,
    val backgroundImgWidth: Int? = null,
    val backgroundImgHeight: Int? = null,
    /** 独立背景层图片路径，null 表示跟随头部图（背景层复用头部图与头部取景） */
    val backdropPath: String? = null,
    val heroOffsetX: Float = 0f,
    val heroOffsetY: Float = 0f,
    val heroScale: Float = SettingsRepository.HERO_SCALE_DEFAULT,
    /** 独立背景图原始宽度（像素），null 表示未分离或未知 */
    val backdropImgWidth: Int? = null,
    /** 独立背景图原始高度（像素），null 表示未分离或未知 */
    val backdropImgHeight: Int? = null,
    // ---------------- AI 翻译 ----------------
    val aiTranslateEnabled: Boolean = false,
    val llmBaseUrl: String = SettingsRepository.LLM_BASE_URL_DEFAULT,
    val llmModel: String = SettingsRepository.LLM_MODEL_DEFAULT,
    /** 小说正文专用模型（空串表示跟随文本翻译模型） */
    val llmNovelBaseUrl: String = "",
    val llmNovelModel: String = "",
    /** 可选模型列表（内置默认 + 远程覆盖） */
    val translateModels: List<ModelEntry> = emptyList(),
    /** 各场景当前生效的默认模型 id，选择器据此高亮"未手动选择的默认项" */
    val roleDefaultIds: RoleDefaultIds = RoleDefaultIds(),
    /** 远程模型目录地址（官方默认或用户自定义，见 SettingsRepository.CATALOG_URL_DEFAULT） */
    val catalogUrl: String = SettingsRepository.CATALOG_URL_DEFAULT,
    /** 自定义加密目录的解密密钥（空串 = 用编译期内置密钥，官方列表即此） */
    val catalogEncKey: String = "",
    /** 已保存的自定义目录源（官方默认不入库，UI 固定首行渲染） */
    val catalogSources: List<CatalogSource> = emptyList(),
    /** 列表来源弹层的刷新结果状态 */
    val catalogRefreshState: CatalogRefreshState = CatalogRefreshState.Idle,
    // ---------------- WebDAV 同步 ----------------
    val webDavEnabled: Boolean = false,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val lastSyncAt: Long = 0L,
    val syncState: SyncState = SyncState.IDLE,
    val syncResult: SyncResult? = null,
    val testConnectionState: TestConnectionState = TestConnectionState.IDLE,
) {
    /** 当前是否为自定义目录地址：入口行副文案与二级弹层据此区分展示 */
    val catalogIsCustom: Boolean
        get() = catalogUrl != SettingsRepository.CATALOG_URL_DEFAULT
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val loadFeedUseCase: LoadFeedUseCase,
    private val loadPopularFeedUseCase: LoadPopularFeedUseCase,
    private val loadFollowFeedUseCase: LoadFollowFeedUseCase,
    private val loadRandomFeedUseCase: LoadRandomFeedUseCase,
    private val loadTagFeedUseCase: LoadTagFeedUseCase,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val observeAdultContentUseCase: ObserveAdultContentUseCase,
    private val restoreAdultContentUseCase: RestoreAdultContentUseCase,
    private val setAdultContentUseCase: SetAdultContentUseCase,
    private val observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val observeHistoryRetentionUseCase: ObserveHistoryRetentionUseCase,
    private val setHistoryRetentionUseCase: SetHistoryRetentionUseCase,
    private val observeLanguageUseCase: ObserveLanguageUseCase,
    private val setLanguageUseCase: SetLanguageUseCase,
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val observeAutoCheckEnabledUseCase: ObserveAutoCheckEnabledUseCase,
    private val setAutoCheckEnabledUseCase: SetAutoCheckEnabledUseCase,
    private val observeCustomBackgroundUseCase: ObserveCustomBackgroundUseCase,
    private val setCustomBackgroundUseCase: SetCustomBackgroundUseCase,
    private val observeBackgroundDimUseCase: ObserveBackgroundDimUseCase,
    private val setBackgroundDimUseCase: SetBackgroundDimUseCase,
    private val settingsRepository: SettingsRepository,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val translationRepository: TranslationRepository,
    private val selectTranslateModelUseCase: SelectTranslateModelUseCase,
    private val selectTranslateNovelModelUseCase: SelectTranslateNovelModelUseCase,
    private val setAiTranslateEnabledUseCase: SetAiTranslateEnabledUseCase,
    private val authRepository: AuthRepository,
    private val blockedContentRepository: BlockedContentRepository,
    private val thumbnailResolver: ThumbnailResolver,
    private val webDavSyncRepository: WebDavSyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * 各 tab/分类/标签的自治加载器（兼内存缓存）：LRU 限容，逐出非当前项并停其后台任务。
     * 切换 tab 只换渲染的 loader，不取消在途请求——刷新后台飞完自动落位。
     */
    private val loaders = object : LinkedHashMap<FeedKey, FeedLoader>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FeedKey, FeedLoader>): Boolean {
            if (size <= MAX_LOADERS || eldest.key == currentKey) return false
            eldest.value.dispose()
            return true
        }
    }
    private var currentKey: FeedKey? = null
    private var currentCollectJob: Job? = null

    init {
        viewModelScope.launch {
            observeFavoriteIdsUseCase().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
        viewModelScope.launch {
            blockedContentRepository.blockedTags.collect { tags ->
                _uiState.update { it.copy(blockedTags = tags) }
            }
        }
        viewModelScope.launch {
            blockedContentRepository.blockedUsers.collect { users ->
                _uiState.update { it.copy(blockedUsers = users) }
            }
        }
        viewModelScope.launch {
            restoreAdultContentUseCase()
            observeAdultContentUseCase().collect { enabled ->
                _uiState.update { it.copy(adultEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            observeThemeModeUseCase().collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            observeHistoryRetentionUseCase().collect { days ->
                _uiState.update { it.copy(historyRetentionDays = days) }
            }
        }
        viewModelScope.launch {
            observeLanguageUseCase().collect { language ->
                _uiState.update { it.copy(language = language) }
            }
        }
        viewModelScope.launch {
            // 每次启动自动检查一次；手动打开开关时也会触发一次
            observeAutoCheckEnabledUseCase().collect { enabled ->
                _uiState.update { it.copy(autoCheckEnabled = enabled) }
                if (enabled && _uiState.value.updateBanner == null) checkForUpdate(silent = true)
            }
        }
        viewModelScope.launch {
            observeCustomBackgroundUseCase().collect { path ->
                _uiState.update { it.copy(customBackgroundPath = path) }
            }
        }
        viewModelScope.launch {
            observeBackgroundDimUseCase().collect { dim ->
                _uiState.update { it.copy(backgroundDim = dim) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundScrimDark.collect { scrim ->
                _uiState.update { it.copy(backgroundScrimDark = scrim) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundScrimLight.collect { scrim ->
                _uiState.update { it.copy(backgroundScrimLight = scrim) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundOffsetX.collect { offset ->
                _uiState.update { it.copy(backgroundOffsetX = offset) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundOffsetY.collect { offset ->
                _uiState.update { it.copy(backgroundOffsetY = offset) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundScale.collect { scale ->
                _uiState.update { it.copy(backgroundScale = scale) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundBlur.collect { blur ->
                _uiState.update { it.copy(backgroundBlur = blur) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundHeroFraction.collect { fraction ->
                _uiState.update { it.copy(backgroundHeroFraction = fraction) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundImgWidth.collect { width ->
                _uiState.update { it.copy(backgroundImgWidth = width) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundImgHeight.collect { height ->
                _uiState.update { it.copy(backgroundImgHeight = height) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backdropPath.collect { path ->
                _uiState.update { it.copy(backdropPath = path) }
            }
        }
        viewModelScope.launch {
            settingsRepository.heroOffsetX.collect { offset ->
                _uiState.update { it.copy(heroOffsetX = offset) }
            }
        }
        viewModelScope.launch {
            settingsRepository.heroOffsetY.collect { offset ->
                _uiState.update { it.copy(heroOffsetY = offset) }
            }
        }
        viewModelScope.launch {
            settingsRepository.heroScale.collect { scale ->
                _uiState.update { it.copy(heroScale = scale) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backdropImgWidth.collect { width ->
                _uiState.update { it.copy(backdropImgWidth = width) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backdropImgHeight.collect { height ->
                _uiState.update { it.copy(backdropImgHeight = height) }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiTranslateEnabled.collect { enabled ->
                _uiState.update { it.copy(aiTranslateEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.llmBaseUrl.collect { url ->
                _uiState.update { it.copy(llmBaseUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsRepository.llmModel.collect { model ->
                _uiState.update { it.copy(llmModel = model) }
            }
        }
        viewModelScope.launch {
            settingsRepository.llmNovelBaseUrl.collect { url ->
                _uiState.update { it.copy(llmNovelBaseUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsRepository.llmNovelModel.collect { model ->
                _uiState.update { it.copy(llmNovelModel = model) }
            }
        }
        viewModelScope.launch {
            modelCatalogRepository.models.collect { models ->
                _uiState.update { it.copy(translateModels = models) }
            }
        }
        viewModelScope.launch {
            translationRepository.roleDefaultIds.collect { ids ->
                _uiState.update { it.copy(roleDefaultIds = ids) }
            }
        }
        viewModelScope.launch {
            settingsRepository.catalogRemoteUrl.collect { url ->
                _uiState.update { it.copy(catalogUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsRepository.catalogEncKey.collect { key ->
                _uiState.update { it.copy(catalogEncKey = key) }
            }
        }
        viewModelScope.launch {
            settingsRepository.catalogSources.collect { sources ->
                _uiState.update { it.copy(catalogSources = sources) }
            }
        }
        viewModelScope.launch {
            // 自动重登成功后登录态未变化，但数据已过期（登录墙作品缩略图等），需重新加载
            authRepository.sessionRefreshed.collect {
                android.util.Log.d("PikuDiag", "session refreshed, reloading feed")
                reloadAllFeeds()
            }
        }
        viewModelScope.launch {
            // 只替换缩略图字段：事件里的 work 可能是详情页传入的瘦对象（title 等为空），
            // 整体替换会把卡片标题/作者等字段清空（历史 bug：密码作品解锁后回退，标题消失）
            thumbnailResolver.thumbUpdated.collect { updated ->
                _uiState.update { s ->
                    if (s.works.none { it.id == updated.id && it.thumbnailUrl != updated.thumbnailUrl }) {
                        s
                    } else {
                        s.copy(works = s.works.map {
                            if (it.id == updated.id && it.thumbnailUrl != updated.thumbnailUrl) {
                                it.copy(thumbnailUrl = updated.thumbnailUrl)
                            } else {
                                it
                            }
                        })
                    }
                }
                // 所有 loader 的快照同步替换缩略图（loader 即缓存，无需另套同步逻辑）
                for (loader in loaders.values.toList()) {
                    loader.updateThumbnail(updated.id, updated.thumbnailUrl)
                }
            }
        }
        viewModelScope.launch {
            var prevLoggedIn: Boolean? = null
            authRepository.authStatus.collect { status ->
                android.util.Log.d("PikuDiag", "authStatus=$status")
                val loggedIn = status == AuthStatus.LOGGED_IN
                _uiState.update {
                    it.copy(
                        loggedIn = loggedIn,
                        userAvatarUrl = if (loggedIn) {
                            authRepository.userProfile.value?.avatarUrl
                        } else null,
                    )
                }
                if (prevLoggedIn != null && loggedIn != prevLoggedIn) reloadAllFeeds()
                prevLoggedIn = loggedIn
                if (loggedIn) authRepository.refreshUserProfile()
            }
        }
        viewModelScope.launch {
            authRepository.userProfile.collect { profile ->
                android.util.Log.d("PikuDiag", "userProfile=$profile")
                _uiState.update {
                    it.copy(userProfile = profile, userAvatarUrl = profile?.avatarUrl)
                }
            }
        }
        // WebDAV 同步状态
        viewModelScope.launch {
            settingsRepository.webDavEnabled.collect { enabled ->
                _uiState.update { it.copy(webDavEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.webDavUrl.collect { url ->
                _uiState.update { it.copy(webDavUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsRepository.webDavUsername.collect { username ->
                _uiState.update { it.copy(webDavUsername = username) }
            }
        }
        viewModelScope.launch {
            settingsRepository.webDavPassword.collect { password ->
                _uiState.update { it.copy(webDavPassword = password) }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastSyncAt.collect { at ->
                _uiState.update { it.copy(lastSyncAt = at) }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastSyncResult.collect { result ->
                _uiState.update { it.copy(syncResult = result) }
            }
        }
        viewModelScope.launch {
            webDavSyncRepository.syncState.collect { state ->
                _uiState.update { it.copy(syncState = state) }
            }
        }
        viewModelScope.launch {
            webDavSyncRepository.testConnectionState.collect { state ->
                _uiState.update { it.copy(testConnectionState = state) }
            }
        }
        select(FeedKey(FeedTab.LATEST, PoipikuCategory.ALL, null))
    }

    fun selectFeedTab(tab: FeedTab) {
        val key = resolveKey(targetTab = tab, targetCategory = null, targetTag = null)
        if (key == currentKey) return
        select(key)
    }

    fun selectCategory(category: PoipikuCategory) {
        val cur = _uiState.value
        if (category == cur.category && cur.currentTag == null) return
        select(resolveKey(targetTab = null, targetCategory = category, targetTag = null))
    }

    fun selectTag(tag: String?) {
        val key = resolveKey(targetTab = FeedTab.LATEST, targetCategory = PoipikuCategory.ALL, targetTag = tag)
        if (key == currentKey) return
        select(key)
    }

    /** 解析目标 FeedKey：未显式指定时沿用当前值；非 LATEST tab 分类强制回 ALL */
    private fun resolveKey(
        targetTab: FeedTab?,
        targetCategory: PoipikuCategory?,
        targetTag: String?,
    ): FeedKey {
        val cur = _uiState.value
        val tab = targetTab ?: cur.feedTab
        val category = when {
            targetCategory != null -> targetCategory
            tab == FeedTab.LATEST -> cur.category
            else -> PoipikuCategory.ALL
        }
        return FeedKey(tab, category, targetTag)
    }

    /** 切换当前渲染的 loader：同步合并一次快照（零帧延迟），再持续收集后续更新 */
    private fun select(key: FeedKey) {
        currentKey = key
        val loader = obtainLoader(key)
        currentCollectJob?.cancel()
        // 提示条是一次性事件，不跨切换存活（历史 bug：随快照持久化导致每次切回都复现）
        loader.clearNotice()
        applySnapshot(loader.state.value)
        _uiState.update { it.copy(feedEpoch = it.feedEpoch + 1) }
        currentCollectJob = viewModelScope.launch {
            // 守卫：已派发的旧回调可能在切换后才执行，只允许渲染订阅时的这份 feed
            loader.state.collect { if (currentKey == key) applySnapshot(it) }
        }
    }

    private fun obtainLoader(key: FeedKey): FeedLoader =
        loaders.getOrPut(key) { createLoader(key) }

    private fun createLoader(key: FeedKey): FeedLoader {
        val loader = FeedLoader(
            key = key,
            scope = viewModelScope,
            fetchPage = { page -> fetchPageFor(key, page) },
            isLoggedIn = authRepository::isLoggedIn,
        )
        // 新建即加载首屏（等价旧版“缓存未命中走网络”路径）
        loader.refresh(countNotice = false)
        return loader
    }

    private suspend fun fetchPageFor(key: FeedKey, page: Int): Result<List<Work>> {
        val result = when {
            key.tag != null -> loadTagFeedUseCase(key.tag, page)
            key.tab == FeedTab.HOT -> loadPopularFeedUseCase(page)
            key.tab == FeedTab.FOLLOW -> loadFollowFeedUseCase(page)
            key.tab == FeedTab.RANDOM -> loadRandomFeedUseCase()
            else -> loadFeedUseCase(page, key.category.cd)
        }
        return result.map { blockedContentRepository.filterWorks(it) }
    }

    fun addBlockedTag(tag: String): Boolean = blockedContentRepository.addBlockedTag(tag)

    fun removeBlockedTag(tag: String) = blockedContentRepository.removeBlockedTag(tag)

    fun unblockUser(authorId: Long) = blockedContentRepository.unblockUser(authorId)

    /** 把当前 loader 的快照合并进对外 UI 状态（错误在此处映射成文案资源） */
    private fun applySnapshot(snap: FeedSnapshot) {
        val key = currentKey ?: return
        _uiState.update {
            it.copy(
                feedTab = key.tab,
                category = key.category,
                currentTag = key.tag,
                works = snap.works,
                loading = snap.loading,
                loadingMore = snap.loadingMore,
                endReached = snap.endReached,
                errorRes = snap.error?.toFeedErrorRes(),
                loadMoreErrorRes = snap.loadMoreError?.toFeedErrorRes(),
                followNeedLogin = snap.followNeedLogin,
                refreshNotice = snap.refreshNotice,
            )
        }
    }

    fun retry() {
        val loader = currentLoader() ?: return
        android.util.Log.d("PikuDiag", "retry tab=${loader.key.tab}")
        // 下拉刷新：按当前首条 id 计算新增提示；刷新归属 loader 本身，
        // 切走不取消，回来数据已就位
        loader.refresh(countNotice = true)
    }

    fun dismissRefreshNotice() {
        currentLoader()?.clearNotice()
    }

    fun loadMore() {
        currentLoader()?.loadMore()
    }

    fun retryLoadMore() {
        currentLoader()?.retryLoadMore()
    }

    fun shuffleRandom() {
        if (_uiState.value.feedTab != FeedTab.RANDOM) return
        // 随机流重新洗牌：loader 复用（切走再回仍保留上次内容），仅显式洗牌才重拉
        _uiState.update { it.copy(feedEpoch = it.feedEpoch + 1) }
        currentLoader()?.refresh(countNotice = false)
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }

    fun logout() {
        authRepository.logout()
    }

    fun toggleAdultContent() {
        val target = !_uiState.value.adultEnabled
        val old = _uiState.value.adultEnabled
        viewModelScope.launch {
            val changed = setAdultContentUseCase(target)
            if (changed && old != target) reloadAllFeeds()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { setThemeModeUseCase(mode) }
    }

    fun setCustomBackground(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = setCustomBackgroundUseCase(uri)
            if (!ok) {
                _uiState.update { it.copy(backgroundErrorRes = R.string.background_read_failed) }
            }
        }
    }

    fun clearCustomBackground() {
        viewModelScope.launch { setCustomBackgroundUseCase.clear() }
    }

    fun setBackgroundDim(value: Float, persist: Boolean = false) {
        setBackgroundDimUseCase(value, persist)
    }

    fun setBackgroundScale(value: Float, persist: Boolean = false) {
        settingsRepository.setBackgroundScale(value, persist)
    }

    fun setBackgroundBlur(value: Float, persist: Boolean = false) {
        settingsRepository.setBackgroundBlur(value, persist)
    }

    fun setBackgroundHeroFraction(value: Float, persist: Boolean = false) {
        settingsRepository.setBackgroundHeroFraction(value, persist)
    }

    fun setBackgroundOffset(x: Float, y: Float, persist: Boolean = false) {
        settingsRepository.setBackgroundOffset(x, y, persist)
    }

    fun setHeroScale(value: Float, persist: Boolean = false) {
        settingsRepository.setHeroScale(value, persist)
    }

    fun setHeroOffset(x: Float, y: Float, persist: Boolean = false) {
        settingsRepository.setHeroOffset(x, y, persist)
    }

    fun persistHeroOffset() {
        settingsRepository.persistHeroOffset()
    }

    /** 保存相册图片为独立背景层；失败时置错误提示（弹层内展示） */
    fun setCustomBackdrop(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = setCustomBackgroundUseCase.saveBackdrop(uri)
            if (!ok) {
                _uiState.update { it.copy(backgroundErrorRes = R.string.background_read_failed) }
            }
        }
    }

    /** 清除独立背景层，回到跟随头部模式（无缝过渡） */
    fun restoreFollowBackground() {
        viewModelScope.launch { setCustomBackgroundUseCase.clearBackdrop() }
    }

    fun consumeBackgroundError() {
        _uiState.update { it.copy(backgroundErrorRes = null) }
    }

    fun setHistoryRetentionDays(days: Int) {
        viewModelScope.launch { setHistoryRetentionUseCase(days) }
    }

    fun setLanguage(language: AppLanguage) {
        setLanguageUseCase(language)
    }

    // ---------------- AI 翻译设置 ----------------

    fun setAiTranslateEnabled(enabled: Boolean) {
        setAiTranslateEnabledUseCase(enabled)
    }

    fun selectTranslateModel(entry: ModelEntry) {
        selectTranslateModelUseCase(entry)
    }

    /** 小说正文模型：传 null 表示跟随文本翻译模型 */
    fun selectTranslateNovelModel(entry: ModelEntry?) {
        selectTranslateNovelModelUseCase(entry)
    }

    /**
     * 保存远程目录地址与解密密钥并立即刷新（地址空白 = 恢复官方默认，密钥空白 = 用内置解密）。
     * 第三方加密目录需成对填入作者分发的地址 + 密钥；明文 JSON 只填地址即可。
     * 拉取成功后 models StateFlow 整体替换，两个模型选择器自动跟随。
     */
    fun saveCatalog(url: String, encKey: String) {
        val normalizedUrl = url.trim().ifBlank { SettingsRepository.CATALOG_URL_DEFAULT }
        settingsRepository.setCatalogRemoteUrl(normalizedUrl)
        settingsRepository.setCatalogEncKey(encKey)
        refreshCatalog()
    }

    /** 恢复官方默认列表并立即刷新 */
    fun resetCatalogUrl() = saveCatalog(SettingsRepository.CATALOG_URL_DEFAULT, "")

    /** 切换到某个已保存的源：写入激活两键并立即刷新 */
    fun activateCatalogSource(source: CatalogSource) = saveCatalog(source.url, source.encKey)

    /**
     * 把弹层当前编辑内容（地址+密钥）存为一个新源并立即激活。
     * 名称留空时从 URL 自动推导；同 id 不存在即追加。
     */
    fun saveCatalogAsSource(name: String?, url: String, encKey: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        val source = CatalogSource(
            id = newCatalogSourceId(),
            name = name?.trim()?.ifEmpty { null }
                ?: CatalogSourceCodec.autoName(trimmedUrl),
            url = trimmedUrl,
            encKey = encKey.trim(),
        )
        settingsRepository.saveCatalogSource(source)
        activateCatalogSource(source)
    }

    fun renameCatalogSource(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) settingsRepository.renameCatalogSource(id, trimmed)
    }

    /** 删除已存源；若删除的是当前激活的自定义源，回退官方默认避免悬空激活态 */
    fun deleteCatalogSource(id: String) {
        val target = _uiState.value.catalogSources.firstOrNull { it.id == id } ?: return
        settingsRepository.deleteCatalogSource(id)
        val active = _uiState.value
        if (target.url == active.catalogUrl && target.encKey == active.catalogEncKey) {
            resetCatalogUrl()
        }
    }

    private fun refreshCatalog() {
        _uiState.update { it.copy(catalogRefreshState = CatalogRefreshState.Loading) }
        viewModelScope.launch {
            val ok = modelCatalogRepository.refresh()
            _uiState.update {
                it.copy(
                    catalogRefreshState = if (ok) {
                        CatalogRefreshState.Success(modelCatalogRepository.models.value.size)
                    } else {
                        CatalogRefreshState.Failed
                    },
                )
            }
        }
    }

    /** 弹层内手动检查更新（含失败重试） */
    fun checkForUpdateManual() = checkForUpdate(silent = false)

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateBanner = null) }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        setAutoCheckEnabledUseCase(enabled)
    }

    // ---------------- WebDAV 同步 ----------------

    fun setWebDavUrl(url: String) {
        settingsRepository.setWebDavUrl(url)
    }

    fun setWebDavUsername(username: String) {
        settingsRepository.setWebDavUsername(username)
    }

    fun setWebDavPassword(password: String) {
        settingsRepository.setWebDavPassword(password)
    }

    fun setWebDavEnabled(enabled: Boolean) {
        settingsRepository.setWebDavEnabled(enabled)
    }

    fun testWebDavConnection() {
        viewModelScope.launch {
            // 测试通过说明服务器与凭据可用，顺手清掉残留的同步失败态，
            // 避免总览卡"连接成功"与"同步失败"同时出现的矛盾
            if (webDavSyncRepository.testConnection() == TestConnectionState.SUCCESS) {
                webDavSyncRepository.clearSyncFailure()
            }
        }
    }

    fun clearTestConnectionState() {
        webDavSyncRepository.clearTestConnectionState()
    }

    fun syncNow() {
        viewModelScope.launch {
            webDavSyncRepository.sync()
        }
    }

    fun clearSyncResult() {
        settingsRepository.clearSyncResult()
    }

    private fun checkForUpdate(silent: Boolean) {
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(updateCheckState = UpdateCheckState.Checking) }
            checkForUpdateUseCase().onSuccess { release ->
                if (release != null) {
                    // 手动检查发现新版：弹层内展示 + 关弹层后首页横幅常驻入口
                    _uiState.update {
                        it.copy(
                            updateCheckState = UpdateCheckState.Available(release),
                            updateBanner = release,
                        )
                    }
                } else {
                    _uiState.update { it.copy(updateCheckState = UpdateCheckState.Latest) }
                }
            }.onFailure {
                // 自动检查失败静默，不打扰用户（GitHub 限流等场景）
                _uiState.update {
                    it.copy(
                        updateCheckState = if (silent) UpdateCheckState.Idle else UpdateCheckState.Failed,
                    )
                }
            }
        }
    }

    private fun currentLoader(): FeedLoader? = currentKey?.let { loaders[it] }

    /**
     * 全量失效：登录态变化 / adult 开关 / session 刷新都会改变所有 feed 的过滤条件。
     * 逐个停掉后台任务再清表，重建当前 loader 立即加载——不存在守卫吞加载的窗口。
     */
    private fun reloadAllFeeds() {
        currentCollectJob?.cancel()
        for (loader in loaders.values.toList()) loader.dispose()
        loaders.clear()
        val key = currentKey
            ?: resolveKey(targetTab = null, targetCategory = null, targetTag = null)
        currentKey = null
        select(key)
    }
}
