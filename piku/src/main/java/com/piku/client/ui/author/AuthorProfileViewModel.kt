package com.piku.client.ui.author

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.auth.PixivAuthRepository
import com.piku.client.data.local.ImageSaver
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.remote.translation.TranslationRepository
import com.piku.client.data.repository.PixivRepository
import com.piku.client.data.repository.reloadOnSessionChange
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthorProfile
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.ObserveLanguageUseCase
import com.piku.client.domain.usecase.ToggleFavoriteUseCase
import com.piku.client.ui.common.FeedbackChannel
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 画师主页的分类页。计数挂在这些页上没有意义——只有插画/漫画/收藏三个池子 */
enum class AuthorTab { ILLUST, MANGA, BOOKMARKS }

data class AuthorUiState(
    val userId: Long = -1L,
    /** 来源页传来的昵称：资料未到之前先用它占位，头部不至于一片空白 */
    val userName: String = "",
    val profile: AuthorProfile? = null,
    val tab: AuthorTab = AuthorTab.ILLUST,
    val works: List<Work> = emptyList(),
    val favoriteIds: Set<WorkKey> = emptySet(),
    val illustCount: Int? = null,
    val mangaCount: Int? = null,
    val bookmarkCount: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** 资料请求失败：只在资料区位置提示，不截胡下面的作品列表 */
    val profileErrorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    val loggedIn: Boolean = false,
    val isSelf: Boolean = false,
    val followSending: Boolean = false,
    /** 当前 tab 第一页失败：内容区给错误态，头部与 tab 行照常 */
    val tabLoadFailed: Boolean = false,
    /** 简介译文；null = 还没翻出来 */
    val bioTranslated: String? = null,
    val translating: Boolean = false,
    /** true = 简介当前显示译文 */
    val showTranslatedBio: Boolean = false,
    /** 文本翻译模型可用；不可用就不给「译」入口（与详情页同一道闸门） */
    val canTranslate: Boolean = false,
)

/**
 * 画师主页（资料页形态）。资料与作品都来自 pixiv 的应用接口，整页需要登录：
 * 未登录时不发请求，头部用来源页带来的昵称占位，内容区给门。
 *
 * Profile 形态目前只有 pixiv 声明，所以这里直接依赖 [PixivRepository]。
 * 将来若有第二个源声明这一形态，要先把取数抽成插件，否则会拿错站点的数据。
 */
@HiltViewModel
class AuthorProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PixivRepository,
    private val authRepository: PixivAuthRepository,
    private val translationRepository: TranslationRepository,
    private val observeLanguageUseCase: ObserveLanguageUseCase,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val imageSaver: ImageSaver,
) : ViewModel() {

    private val userId: Long = savedStateHandle["userId"] ?: -1L
    private val userNameArg: String = savedStateHandle["userName"] ?: ""

    private val _uiState = MutableStateFlow(AuthorUiState(userId = userId, userName = userNameArg))
    val uiState: StateFlow<AuthorUiState> = _uiState.asStateFlow()

    val profileUrl: String = "https://www.pixiv.net/users/$userId"

    val feedback = FeedbackChannel()

    private class Paging {
        var page = 0
        var cursor: Long? = null
        var endReached = false
        var loaded = false
    }

    private val paging = mutableMapOf<AuthorTab, Paging>()
    private val loadedWorks = mutableMapOf<AuthorTab, List<Work>>()
    private var generation = 0

    init {
        viewModelScope.launch {
            observeFavoriteIdsUseCase().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
        // 目录晚到时「译」入口跟着显隐，与详情页同款闸门
        viewModelScope.launch {
            translationRepository.roleModelAvailability.collect { availability ->
                _uiState.update { it.copy(canTranslate = availability.text) }
            }
        }
        // 重登后补回失效期间拿不到的数据；登出后不能继续显示上一个账号看过的资料
        viewModelScope.reloadOnSessionChange(authRepository.sessionVersion) { load() }
        load()
    }

    /** 第一屏：资料 + 首个 tab，两条并行，谁失败只影响谁 */
    fun load() {
        val gen = ++generation
        paging.clear()
        loadedWorks.clear()
        val loggedIn = authRepository.isLoggedIn()
        _uiState.update {
            it.copy(
                loggedIn = loggedIn,
                isSelf = authRepository.currentUserId() == userId,
                // 整页重来：资料、tab 与两处错误态一起回到首屏
                profile = null,
                illustCount = null,
                mangaCount = null,
                bookmarkCount = null,
                tab = AuthorTab.ILLUST,
                works = emptyList(),
                bioTranslated = null,
                showTranslatedBio = false,
                translating = false,
                loading = loggedIn,
                loadingMore = false,
                profileErrorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
                tabLoadFailed = false,
            )
        }
        if (!loggedIn) return
        viewModelScope.launch {
            loadProfile(gen)
            loadTab(AuthorTab.ILLUST, gen)
        }
    }

    /** 资料失败后单独重试：作品列表不受影响，不该跟着整页重来 */
    fun retryProfile() {
        viewModelScope.launch { loadProfile(generation) }
    }

    /** 当前 tab 第一页失败后重试：不能走 selectTab，同 tab 会被它的去重挡掉 */
    fun retryTab() {
        val tab = _uiState.value.tab
        _uiState.update { it.copy(tabLoadFailed = false) }
        viewModelScope.launch { loadTab(tab, generation) }
    }

    fun selectTab(tab: AuthorTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update {
            it.copy(
                tab = tab,
                works = emptyList(),
                endReached = false,
                loadMoreErrorRes = null,
                tabLoadFailed = false,
            )
        }
        val gen = generation
        viewModelScope.launch {
            if (paging[tab]?.loaded == true) restoreTab(tab) else loadTab(tab, gen)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.endReached) return
        if (state.loadMoreErrorRes != null) return
        viewModelScope.launch { loadTab(state.tab, generation, append = true) }
    }

    fun retryLoadMore() {
        val state = _uiState.value
        if (state.loadMoreErrorRes == null) return
        _uiState.update { it.copy(loadMoreErrorRes = null) }
        viewModelScope.launch { loadTab(state.tab, generation, append = true) }
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }

    /**
     * 简介「译」：没有译文就现翻，有译文就在原/译之间切。
     * 与详情页同款语义——chip 表达的是「当前显示的是译文」。
     */
    fun toggleBioTranslation() {
        val state = _uiState.value
        if (state.bioTranslated != null) {
            _uiState.update { it.copy(showTranslatedBio = !it.showTranslatedBio) }
            return
        }
        if (state.translating || !state.canTranslate) return
        val source = state.profile?.comment.orEmpty()
        if (source.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(translating = true) }
            val target = TranslationRepository.targetLangName(observeLanguageUseCase().value)
            val output = runCatching {
                translationRepository.translateAll(listOf(source), target)
            }.getOrNull()
            // 译文与原文相同 = 本来就是目标语言，按「无译文」静默处理，不是失败
            val translated = output?.firstOrNull()
                ?.takeIf { it.isNotBlank() && it != source }
            _uiState.update {
                it.copy(
                    translating = false,
                    bioTranslated = translated,
                    showTranslatedBio = translated != null,
                )
            }
            // 只有引擎层面真没产出才提示
            if (output == null) feedback.show(R.string.detail_translate_failed)
        }
    }

    fun toggleFollow() {
        val state = _uiState.value
        if (state.isSelf || state.followSending) return
        if (!state.loggedIn) {
            feedback.show(R.string.detail_follow_login_hint)
            return
        }
        val target = !(state.profile?.followed ?: false)
        viewModelScope.launch {
            _uiState.update { it.copy(followSending = true) }
            val result = repository.followUser(userId, target)
            _uiState.update { s ->
                s.copy(
                    followSending = false,
                    profile = if (result.isSuccess) s.profile?.copy(followed = target) else s.profile,
                )
            }
            feedback.show(
                when {
                    result.isFailure -> R.string.detail_follow_failed
                    target -> R.string.detail_follow_sent
                    else -> R.string.detail_unfollow_sent
                },
            )
        }
    }

    suspend fun saveAvatar(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching { imageSaver.save(url, "Piku_avatar") }.isSuccess
    }

    /** 切回已加载过的 tab：把数据摆回来，不重新请求 */
    private fun restoreTab(tab: AuthorTab) {
        val state = paging[tab]
        _uiState.update {
            it.copy(
                works = loadedWorks[tab].orEmpty(),
                endReached = state?.endReached ?: true,
            )
        }
    }

    private suspend fun loadTab(tab: AuthorTab, gen: Int, append: Boolean = false) {
        val page = paging.getOrPut(tab) { Paging() }
        if (!append) {
            page.page = 0
            page.cursor = null
            page.endReached = false
        }
        _uiState.update {
            if (append) it.copy(loadingMore = true, loadMoreErrorRes = null)
            else it.copy(loading = true, loadMoreErrorRes = null)
        }
        val offset = page.page * PixivAppConfig.PAGE_SIZE
        val result = when (tab) {
            AuthorTab.ILLUST -> repository
                .authorIllusts(userId, PixivAppConfig.TYPE_ILLUST, offset)
                .map { TabPage(it.items, it.nextCursor) }

            AuthorTab.MANGA -> repository
                .authorIllusts(userId, PixivAppConfig.TYPE_MANGA, offset)
                .map { TabPage(it.items, it.nextCursor) }

            AuthorTab.BOOKMARKS -> repository
                .authorBookmarks(userId, page.cursor)
                .map { TabPage(it.items, it.nextCursor) }
        }
        if (gen != generation) return
        result
            .onSuccess { loaded ->
                page.loaded = true
                page.page += 1
                page.cursor = loaded.cursor?.toLongOrNull()
                // 两种分页都看「接口还给不给下一页令牌」：offset 型是 next_url、游标型是
                // max_bookmark_id。按这一页的条目数猜到底会被丢掉的条目骗到（占位/无图的作品）
                page.endReached = loaded.cursor == null
                val merged = if (append) loadedWorks[tab].orEmpty() + loaded.items else loaded.items
                loadedWorks[tab] = merged
                _uiState.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        loadMoreErrorRes = null,
                        works = if (it.tab == tab) merged else it.works,
                        endReached = if (it.tab == tab) page.endReached else it.endReached,
                    )
                }
            }
            .onFailure { error ->
                if (gen != generation) return@onFailure
                val res = (error as? AppError)?.toFeedErrorRes()
                _uiState.update {
                    if (append) {
                        it.copy(loading = false, loadingMore = false, loadMoreErrorRes = res)
                    } else {
                        it.copy(loading = false, tabLoadFailed = true)
                    }
                }
            }
    }

    /** 资料单独一段：失败只在资料区提示，下面的作品列表照常显示 */
    private suspend fun loadProfile(gen: Int) {
        _uiState.update { it.copy(profileErrorRes = null) }
        repository.authorProfile(userId)
            .onSuccess { profile ->
                if (gen != generation) return@onSuccess
                _uiState.update {
                    it.copy(
                        profile = profile,
                        userName = profile.name.ifBlank { it.userName },
                        illustCount = profile.illustCount,
                        mangaCount = profile.mangaCount,
                        bookmarkCount = profile.bookmarkCount,
                    )
                }
            }
            .onFailure { error ->
                if (gen != generation) return@onFailure
                _uiState.update {
                    it.copy(
                        profileErrorRes = (error as? AppError)?.toFeedErrorRes()
                            ?: R.string.home_error_parse,
                    )
                }
            }
    }
}

/** 一页结果。[cursor] = 接口给的下一页令牌（offset 型是 next_url、游标型是 max_bookmark_id），null = 到底 */
private data class TabPage(val items: List<Work>, val cursor: String?)
