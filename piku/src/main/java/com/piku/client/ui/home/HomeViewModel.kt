package com.piku.client.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.GitHubRelease
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.ThumbnailResolver
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
import com.piku.client.domain.usecase.SetAdultContentUseCase
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

/**
 * 瀑布流列表保留上限（约 10~15 页）。超出后从头部裁剪，保证深滚时内存有界。
 * 裁剪时 Compose 会按 item key 自动保持当前浏览位置
 * （LazyStaggeredGridScrollPosition.updateScrollPositionIfTheFirstItemWasMoved），无需额外补偿。
 */
private const val MAX_WORKS = 600

data class HomeUiState(
    val feedTab: FeedTab = FeedTab.LATEST,
    val category: PoipikuCategory = PoipikuCategory.ALL,
    /** 内容换血计数：tab/分类/标签切换、缓存恢复、重载、洗牌时 +1，UI 据此回顶 */
    val feedEpoch: Int = 0,
    val works: List<Work> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
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
)

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
    private val authRepository: AuthRepository,
    private val thumbnailResolver: ThumbnailResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var page = 0
    private var generation = 0
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetched: List<Work>? = null
    private var noticePending = false
    private var oldFirstId: Long? = null

    /** 各 tab/分类/标签的列表快照缓存；登录态/adult 开关/session 刷新时整体失效 */
    private val feedCache = FeedSnapshotCache()

    init {
        viewModelScope.launch {
            observeFavoriteIdsUseCase().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
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
            // 自动重登成功后登录态未变化，但数据已过期（登录墙作品缩略图等），需重新加载
            authRepository.sessionRefreshed.collect {
                android.util.Log.d("PikuDiag", "session refreshed, reloading feed")
                reload()
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
                // 缓存快照同步替换缩略图，避免切回该 tab 后又显示旧图
                feedCache.updateThumbnail(updated.id, updated.thumbnailUrl)
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
                if (prevLoggedIn != null && loggedIn != prevLoggedIn) reload()
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
        loadFirstPage()
    }

    fun selectFeedTab(tab: FeedTab) {
        if (tab == _uiState.value.feedTab) return
        switchFeed(targetTab = tab, targetCategory = null, targetTag = null)
    }

    fun selectCategory(category: PoipikuCategory) {
        if (category == _uiState.value.category && _uiState.value.currentTag == null) return
        switchFeed(targetTab = null, targetCategory = category, targetTag = null)
    }

    fun selectTag(tag: String?) {
        if (tag == _uiState.value.currentTag) return
        switchFeed(targetTab = FeedTab.LATEST, targetCategory = PoipikuCategory.ALL, targetTag = tag)
    }

    /**
     * 数据源切换（tab/分类/标签）统一入口：
     * 先把当前列表存入缓存，命中目标缓存则直接恢复（秒开），否则清空走正常网络加载。
     */
    private fun switchFeed(targetTab: FeedTab?, targetCategory: PoipikuCategory?, targetTag: String?) {
        cacheCurrentFeed()
        generation++
        loadJob?.cancel()
        prefetchJob?.cancel()
        prefetched = null
        page = 0
        noticePending = false
        oldFirstId = null
        val cur = _uiState.value
        val newTab = targetTab ?: cur.feedTab
        val newCategory = when {
            targetCategory != null -> targetCategory
            newTab == FeedTab.LATEST -> cur.category
            else -> PoipikuCategory.ALL
        }
        val key = FeedKey(newTab, newCategory, targetTag)
        val cached = if (newTab == FeedTab.RANDOM) null else feedCache[key]
        _uiState.update {
            it.copy(
                feedTab = newTab,
                category = newCategory,
                currentTag = targetTag,
                works = cached?.works ?: emptyList(),
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = cached?.endReached ?: false,
                refreshNotice = null,
                followNeedLogin = false,
                loading = false,
                loadingMore = false,
                feedEpoch = it.feedEpoch + 1,
            )
        }
        if (cached != null) {
            page = cached.page
            if (!cached.endReached) prefetchNextPage()
        } else {
            loadPage(append = false)
        }
    }

    /** 把当前列表快照写入缓存（RANDOM 是随机语义不缓存；空列表无缓存价值） */
    private fun cacheCurrentFeed() {
        val s = _uiState.value
        if (s.feedTab == FeedTab.RANDOM || s.works.isEmpty()) return
        feedCache[FeedKey(s.feedTab, s.category, s.currentTag)] =
            FeedSnapshot(s.works, page, s.endReached)
    }

    fun retry() {
        generation++
        noticePending = true
        oldFirstId = _uiState.value.works.firstOrNull()?.id
        android.util.Log.d("PikuDiag", "retry refresh notice pending, oldFirstId=$oldFirstId")
        loadFirstPage()
    }

    fun dismissRefreshNotice() {
        _uiState.update { it.copy(refreshNotice = null) }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.endReached || state.errorRes != null || state.loadMoreErrorRes != null) return
        val cached = prefetched
        if (cached != null) {
            prefetched = null
            page += 1
            updateWorks(state.works + cached)
            _uiState.update { it.copy(endReached = cached.isEmpty()) }
            cacheCurrentFeed()
            prefetchNextPage()
        } else {
            loadPage(append = true)
        }
    }

    fun retryLoadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.endReached || state.errorRes != null || state.loadMoreErrorRes == null) return
        _uiState.update { it.copy(loadMoreErrorRes = null) }
        loadPage(append = true)
    }

    fun shuffleRandom() {
        if (_uiState.value.feedTab != FeedTab.RANDOM) return
        generation++
        page = 0
        noticePending = false
        oldFirstId = null
        _uiState.update {
            it.copy(
                works = emptyList(),
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
                refreshNotice = null,
                feedEpoch = it.feedEpoch + 1,
            )
        }
        loadFirstPage()
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
            if (changed && old != target) reload()
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

    fun setBackgroundDim(value: Float) {
        setBackgroundDimUseCase(value)
    }

    fun setBackgroundScale(value: Float) {
        settingsRepository.setBackgroundScale(value)
    }

    fun setBackgroundBlur(value: Float) {
        settingsRepository.setBackgroundBlur(value)
    }

    fun setBackgroundHeroFraction(value: Float) {
        settingsRepository.setBackgroundHeroFraction(value)
    }

    fun setBackgroundOffset(x: Float, y: Float, persist: Boolean = false) {
        settingsRepository.setBackgroundOffset(x, y, persist)
    }

    fun setHeroScale(value: Float) {
        settingsRepository.setHeroScale(value)
    }

    fun setHeroOffset(x: Float, y: Float, persist: Boolean = false) {
        settingsRepository.setHeroOffset(x, y, persist)
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

    /** 弹层内手动检查更新（含失败重试） */
    fun checkForUpdateManual() = checkForUpdate(silent = false)

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateBanner = null) }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        setAutoCheckEnabledUseCase(enabled)
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

    private fun reload() {
        generation++
        page = 0
        noticePending = false
        oldFirstId = null
        // 登录态/adult 开关/session 刷新都会走到这里：过滤条件变了，全部快照作废
        feedCache.clear()
        _uiState.update {
            it.copy(
                works = emptyList(),
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
                refreshNotice = null,
                feedEpoch = it.feedEpoch + 1,
            )
        }
        loadFirstPage()
    }

    private fun loadFirstPage() {
        if (_uiState.value.loading) return
        loadPage(append = false)
    }

    private fun loadPage(append: Boolean) {
        val tab = _uiState.value.feedTab
        val category = _uiState.value.category
        val tag = _uiState.value.currentTag
        val gen = generation
        val targetPage = if (append) page + 1 else 0
        prefetchJob?.cancel()
        prefetched = null
        loadJob?.cancel()
        // 关注流需要登录：未登录时不发请求（服务端会返回登录页），直接展示登录引导
        if (tab == FeedTab.FOLLOW && !authRepository.isLoggedIn()) {
            _uiState.update {
                it.copy(
                    loading = false,
                    loadingMore = false,
                    errorRes = null,
                    loadMoreErrorRes = null,
                    endReached = true,
                    followNeedLogin = true,
                    refreshNotice = null,
                    works = if (append) it.works else emptyList(),
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(loadingMore = true, loadMoreErrorRes = null)
            else it.copy(loading = true, errorRes = null, loadMoreErrorRes = null, followNeedLogin = false)
        }
        loadJob = viewModelScope.launch {
            val result = when {
                tag != null -> loadTagFeedUseCase(tag, targetPage)
                tab == FeedTab.HOT -> loadPopularFeedUseCase(targetPage)
                tab == FeedTab.FOLLOW -> loadFollowFeedUseCase(targetPage)
                tab == FeedTab.RANDOM -> loadRandomFeedUseCase()
                else -> loadFeedUseCase(targetPage, category.cd)
            }
            result.onSuccess { list ->
                if (generation != gen) return@launch
                page = targetPage
                val notice = if (noticePending && !append && (tab == FeedTab.LATEST || tab == FeedTab.FOLLOW || tab == FeedTab.HOT)) {
                    val old = oldFirstId
                    if (old != null) list.takeWhile { it.id != old }.size else 0
                } else {
                    null
                }
                android.util.Log.d(
                    "PikuDiag",
                    "loadPage success append=$append tab=$tab listFirst=${list.firstOrNull()?.id} oldFirst=$oldFirstId notice=$notice",
                )
                noticePending = false
                oldFirstId = null
                updateWorks(if (append) _uiState.value.works + list else list)
                _uiState.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        loadMoreErrorRes = null,
                        endReached = if (tab == FeedTab.RANDOM) true else list.isEmpty(),
                        refreshNotice = notice,
                    )
                }
                cacheCurrentFeed()
                prefetchNextPage()
            }
                .onFailure { error ->
                    if (generation != gen) return@launch
                    android.util.Log.d(
                        "PikuDiag",
                        "loadPage fail append=$append tab=$tab tag=$tag " +
                            "category=$category page=$targetPage error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    noticePending = false
                    oldFirstId = null
                    _uiState.update {
                        if (append) {
                            it.copy(
                                loading = false,
                                loadingMore = false,
                                loadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                loading = false,
                                loadingMore = false,
                                errorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }

    private fun updateWorks(newWorks: List<Work>) {
        val unique = newWorks.distinctBy { w -> w.id }
        val capped = if (unique.size > MAX_WORKS) unique.takeLast(MAX_WORKS) else unique
        _uiState.update { it.copy(works = capped) }
    }

    private fun prefetchNextPage() {
        val gen = generation
        val nextPage = page + 1
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val tab = _uiState.value.feedTab
            val tag = _uiState.value.currentTag
            val category = _uiState.value.category
            if (tab == FeedTab.RANDOM && tag == null) return@launch
            if (tab == FeedTab.FOLLOW && !authRepository.isLoggedIn()) return@launch
            val result = when {
                tag != null -> loadTagFeedUseCase(tag, nextPage)
                tab == FeedTab.HOT -> loadPopularFeedUseCase(nextPage)
                tab == FeedTab.FOLLOW -> loadFollowFeedUseCase(nextPage)
                else -> loadFeedUseCase(nextPage, category.cd)
            }
            result.onSuccess { list ->
                if (generation != gen) return@launch
                prefetched = list
            }
        }
    }
}
