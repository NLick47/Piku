package com.piku.client.ui.detail

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.data.local.ImageSaver
import com.piku.client.data.local.ImageShareHelper
import com.piku.client.data.local.WorkPasswordRepository
import com.piku.client.data.repository.AdultContentRepository
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.BlockResult
import com.piku.client.data.repository.DetailRepository
import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.data.repository.FollowResult
import com.piku.client.data.repository.ReactionResult
import com.piku.client.data.repository.ThumbnailResolver
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.translation.NovelStreamEvent
import com.piku.client.data.remote.translation.TranslationRepository
import com.piku.client.data.remote.translation.ModelCatalogRepository
import com.piku.client.data.remote.translation.ModelEntry
import com.piku.client.data.remote.translation.ImageTranslateEngine
import com.piku.client.data.remote.translation.ImageTranslationPrompts
import com.piku.client.data.remote.translation.ImageTranslateError
import com.piku.client.data.remote.translation.ImageTranslateResult
import com.piku.client.data.remote.translation.LlmTranslateEngine
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.RestrictionReason
import com.piku.client.domain.model.TranslatedFields
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import com.piku.client.ui.common.FeedbackChannel
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import com.piku.client.di.ApplicationScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** 可独立切换原文/译文的文本字段 */
enum class TranslateField { TITLE, DESCRIPTION, AUTHOR_PROFILE, TAGS, NOVEL }

/** 图片翻译失败的轻提示（snackbar）：按错误类型给文案，[retryable] 决定是否给「重试」按钮 */
data class DetailUiState(
    val detail: WorkDetail? = null,
    val fullImageUrls: List<String> = emptyList(),
    val fullImagesLoading: Boolean = false,
    val loading: Boolean = false,
    /**
     * 阶段一内容已经画出来了，append（追加图/正文）还在路上。
     * 目前只用来压住图区的页码角标：此刻的页数只有 HTML 主图这一张，
     * 画出来会在 append 到位时从 "1/1" 跳到 "1/12"。
     */
    val detailLoadingMore: Boolean = false,
    val errorRes: Int? = null,
    /** 错误副文案：说明可能的成因，仅部分错误类型有（如作品不存在） */
    val errorHintRes: Int? = null,
    /**
     * 错误是否可通过重试解决。作品被删除/不存在（404）是终态，重试只会反复失败，
     * 此时 UI 不给「重试」按钮，改给「在浏览器打开」让用户自行确认。
     */
    val errorRetryable: Boolean = true,
    /**
     * 受限门卡：需登录 / 需开启 R-18 显示 / 需关注作者（poipiku）/ 需 Twitter 关注 / 需转推。
     * 与 [errorRes] 互斥——门卡是可操作的引导页（渲染在图区，一键处理动作后自动
     * 恢复），错误页是终态提示。取值只来自 `detail.gate`（Repository 单一决策），
     * ViewModel 不再自行推导；仅原图被拒（缩略图可见）不算受限：静默沿用缩略图。
     */
    val restrictionReason: RestrictionReason? = null,
    /** R-18 一键开启进行中（防连点；成功后自动重载） */
    val enablingAdultContent: Boolean = false,
    val isFavorite: Boolean = false,
    val favoriteFolders: List<FavoriteFolder> = emptyList(),
    val workFavoriteFolderIds: Set<Long> = emptySet(),
    val shareUrl: String = "",
    val password: String = "",
    val passwordPrefilled: Boolean = false,
    val passwordLoading: Boolean = false,
    val loggedIn: Boolean = false,
    /** 当前详情页的作者即登录用户自己：屏蔽入口整项不显示 */
    val isSelf: Boolean = false,
    val reactionSending: Boolean = false,
    /** 仅内存：本会话发过反应，重进页面重置 */
    val hasReacted: Boolean = false,
    val followSending: Boolean = false,
    /** 屏蔽操作进行中（防连点） */
    val blockSending: Boolean = false,
    val savingImage: Boolean = false,
    /** 个人自定义标签（用于详情页把作品标签收藏进个人标签） */
    val customTags: List<String> = emptyList(),
    /** 底部菜单一次性新手引导（仅首次进入详情页显示） */
    val guideVisible: Boolean = false,
    /**
     * 图区「翻译图片」的一次性提示。
     * 刻意与 [guideVisible] 分开存标记：老用户升级前就看过了底部引导，
     * 若复用同一个标记，这个新加的提示对存量用户将永远不会出现。
     */
    val imageHintVisible: Boolean = false,

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
    /** 文本通道可用，顶栏短字段翻译按钮的显示条件 */
    val canTranslate: Boolean = false,
    /** 译文拉取中 */
    val translating: Boolean = false,
    /** 是否展示"换模型重翻"的模型选择弹窗 */
    val showModelPicker: Boolean = false,
    /** 当前这轮拉取包含长篇正文（阅读器入口触发）；用于精确驱动阅读器的加载态 */
    val fetchingNovelText: Boolean = false,
    /**
     * 小说分块流式翻译进度（百分比 0~100）；null = 空闲。
     * 流式期间 [translating]/[fetchingNovelText] 保持 false——旧语义不被污染，
     * 阅读器 chip 据此显示"翻译中 N%"且保持可点（点按仅切换原/译展示）。
     */
    val novelStreamProgress: Int? = null,
    /** 流式期间尚未翻译的剩余原文；译文模式下阅读器把它拼接在已译前缀之后 */
    val novelRemainder: String? = null,
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
    // ---- 图片翻译 ----
    /** 已翻译的页码 → Bitmap（退出页面时清理） */
    val translatedImages: Map<Int, android.graphics.Bitmap> = emptyMap(),
    /** 当前正在翻译的页码（null = 空闲） */
    val imageTranslatingPage: Int? = null,
    /** 图片翻译失败反馈（snackbar）；开始新翻译或成功时清空 */
    /** 当前页是否显示译图 */
    val showTranslatedImage: Boolean = false,
    /** 是否有可用的 image 模型 */
    val hasImageModel: Boolean = false,
    /** 是否有可用的正文翻译模型，宁缺毋滥：无则正文保留原文，阅读器不提供翻译入口 */
    val hasNovelModel: Boolean = false,
    /** 分享图片时的 loading 状态 */
    val sharingImage: Boolean = false,
    /** 正在分享的目标包名（null = 系统面板）：loading 只转圈在被点的那一行 */
    val sharingTargetPackage: String? = null,
    /** 分享失败的轻提示（snackbar，可重试）；成功时直接拉起分享面板不需要提示 */
) {
    /** 该字段当前是否显示译文：全局态异或单字段覆盖 */
    fun showTranslation(field: TranslateField): Boolean =
        showTranslationAll != (field in fieldOverrides)

    /** 是否有任何译文可展示（决定顶栏按钮高亮与各字段 chip 是否出现） */
    val hasTranslation: Boolean
        get() = detail?.translated?.hasAny == true

    /** 正文译文是否为历史缓存：有缓存但当前无正文模型，阅读器展示时须标注历史译文 */
    val novelTranslationStale: Boolean
        get() = detail?.translated?.novelText != null && !hasNovelModel

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

/** 批量保存全部的一次性结果：失败数 = total - ok */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val loadWorkDetailUseCase: LoadWorkDetailUseCase,
    private val loadWorkFullImagesUseCase: LoadWorkFullImagesUseCase,
    private val favoriteRepository: FavoriteRepository,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val observeAuthStatusUseCase: ObserveAuthStatusUseCase,
    private val detailRepository: DetailRepository,
    private val authRepository: AuthRepository,
    private val adultContentRepository: AdultContentRepository,
    private val thumbnailResolver: ThumbnailResolver,
    private val workPasswordRepository: WorkPasswordRepository,
    private val imageSaver: ImageSaver,
    private val imageShareHelper: ImageShareHelper,
    private val recordHistoryUseCase: RecordHistoryUseCase,
    private val observeCustomTagsUseCase: ObserveCustomTagsUseCase,
    private val addCustomTagUseCase: AddCustomTagUseCase,
    private val removeCustomTagUseCase: RemoveCustomTagUseCase,
    private val settingsRepository: SettingsRepository,
    private val translationRepository: TranslationRepository,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val observeLanguageUseCase: ObserveLanguageUseCase,
    private val imageTranslateEngine: ImageTranslateEngine,
    private val prefs: SharedPreferences,
    savedStateHandle: SavedStateHandle,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val authorId: Long = savedStateHandle["authorId"] ?: -1L
    val workId: Long = savedStateHandle["workId"] ?: -1L

    /**
     * 来源页（feed/历史/收藏/相关作品）的缩略图：与详情页首图是同一张图的两个尺寸
     * （列表卡片渲染 _360，详情页首图是 _640）。图区拿它当低清打底，避免首图到位前
     * 空一块，见 DetailContent 的 underlayUrl。
     */
    val sourceThumbnailUrl: String = savedStateHandle["thumb"] ?: ""

    private val work = Work(
        id = workId,
        authorId = authorId,
        authorName = "",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "",
        // 来源页（feed/历史/收藏/相关作品）缩略图：密码作品未解锁时用它回填历史/收藏
        thumbnailUrl = sourceThumbnailUrl,
        imageCount = 0,
        r18 = false,
    )

    private val showBottomGuide = !prefs.getBoolean(KEY_BOTTOM_GUIDE_SHOWN, false)
    // 独立于底部引导的标记，保证新加的提示对升级上来的老用户也能展示一次
    private val showImageHint = !prefs.getBoolean(KEY_IMAGE_HINT_SHOWN, false)

    /** 上次手动翻译的入口：失败提示的重试按原样重发（顶栏=false / 阅读器长文=true） */
    private var lastManualTranslateIncludeNovel = false
    /** 上次"换模型重翻"所选模型，供失败重试复用 */
    private var lastForcedEntry: ModelEntry? = null

    /** 小说分块流式翻译的唯一持有 job；新流启动前必杀旧流，VM 销毁自动取消 */
    private var novelStreamJob: Job? = null

    private val _uiState = MutableStateFlow(
        DetailUiState(
            shareUrl = "https://poipiku.com/$authorId/$workId.html",
            guideVisible = showBottomGuide,
            imageHintVisible = showImageHint,
            novelProgressPercent = settingsRepository.getNovelProgress(workId),
            canTranslate = translationRepository.hasTextModel(),
        ),
    )
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    /** 一次性反馈（反应/关注/屏蔽/收藏/保存/标签/翻译/图片翻译/分享/批量保存） */
    val feedback = FeedbackChannel()

    /**
     * 分享准备失败的一次性信号：失败时图片操作面板仍开着（loading 刚结束），
     * snackbar 会被 BottomSheet 盖住，UI 收到后先收起面板。
     * 与 [feedback] 分开是因为它驱动的是 UI 动作而非提示文案。
     */
    private val _shareSheetDismiss = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shareSheetDismiss: SharedFlow<Unit> = _shareSheetDismiss.asSharedFlow()

    /** 目录模型列表（用于"换模型重翻"弹窗），不含分类硬限制，全部可用模型都可选 */
    private val _catalogModels = MutableStateFlow(modelCatalogRepository.models.value)
    val catalogModels: StateFlow<List<ModelEntry>> = _catalogModels.asStateFlow()


    data class ImageShareRequest(
        val uri: android.net.Uri,
        val targetPackage: String?,
        val shareText: String,
    )

    private val _shareRequest = MutableStateFlow<ImageShareRequest?>(null)
    val shareRequest: StateFlow<ImageShareRequest?> = _shareRequest.asStateFlow()

    fun clearShareRequest() {
        _shareRequest.value = null
    }

    /** 关闭底部菜单新手引导 */
    fun dismissGuide() {
        if (!_uiState.value.guideVisible) return
        _uiState.update { it.copy(guideVisible = false) }
        prefs.edit().putBoolean(KEY_BOTTOM_GUIDE_SHOWN, true).apply()
    }

    /**
     * 消耗图区「翻译图片」提示的一次机会。
     *
     * 由按钮真的把文字展开出来时回调触发，而不是页面初始化时就写标记：
     * `hasImageModel` 是异步得到的（冷启动时模型目录常晚于首个详情页到达），
     * 提前写的话，没配 image 模型的用户会白白用掉这次机会——
     * 等他后来配好了模型，却再也看不到这个提示了。
     */
    fun consumeImageHint() {
        if (!showImageHint) return
        if (prefs.getBoolean(KEY_IMAGE_HINT_SHOWN, false)) return
        prefs.edit().putBoolean(KEY_IMAGE_HINT_SHOWN, true).apply()
    }

    /** 打开/关闭全屏小说阅读器 */
    fun setNovelReaderOpen(open: Boolean) {
        _uiState.update { it.copy(novelReaderOpen = open) }
    }

    /** 保存该作品的阅读进度（百分比 0~100） */
    fun saveNovelProgress(percent: Int) {
        settingsRepository.setNovelProgress(workId, percent)
    }

    /** 保存图集的阅读进度（页码 1 起）：收藏夹据此在卡片上画进度条 */
    fun saveImageProgress(page: Int) {
        settingsRepository.setImageProgress(workId, page)
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
        // 注意：图区「翻译图片」提示的标记不在这里写，见 [consumeImageHint]
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
            authRepository.userProfile.collect { profile ->
                _uiState.update { it.copy(isSelf = profile?.uid?.toLongOrNull() == authorId) }
            }
        }
        viewModelScope.launch {
            observeAuthStatusUseCase().collect { status ->
                val loggedIn = status == AuthStatus.LOGGED_IN
                val prevLoggedIn = _uiState.value.loggedIn
                _uiState.update { it.copy(loggedIn = loggedIn) }
                // detail==null 的受限态（append 抛 Restricted）也要求重载：
                // 门卡页去登录回来，同样要重新拉详情拿真实内容
                val needsReload = loggedIn != prevLoggedIn && loggedIn &&
                    (_uiState.value.detail != null || _uiState.value.restrictionReason != null)
                if (needsReload) {
                    // 登录成功（含从门卡「去登录」回来）：重载拿真实内容。
                    // 不清 restrictionReason——重载期间门卡留在屏上，等新 detail 到了由它决定去留，
                    // 否则这段等待里图区会从门卡闪成"暂无图片"
                    load()
                }
            }
        }
        viewModelScope.launch {
            // R-18 显示开关（含本页一键开启、抽屉开关、自动重登还原）变化时：
            // 停留在详情页的 R-18 门卡作品整页重载，不用退出重进。
            // 同样不清 restrictionReason：重载期间门卡留在屏上（见上）
            settingsRepository.showAdultContent.collect { enabled ->
                if (enabled && _uiState.value.restrictionReason == RestrictionReason.ADULT) {
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
            // 各角色模型可用性变化时按自己的角色闸门入口：顶栏/短字段看文本通道，
            // 正文/图片各看各的，冷启动目录晚到时也由此同步显隐
            translationRepository.roleModelAvailability.collect { avail ->
                _uiState.update {
                    it.copy(
                        canTranslate = avail.text,
                        hasImageModel = avail.image,
                        hasNovelModel = avail.novel,
                    )
                }
                // 补翻：文本或正文通道有模型时补上此前被无 key 静默跳过的作品，按角色判定不算兜底
                if ((avail.text || avail.novel) && _uiState.value.aiTranslateEnabled &&
                    _uiState.value.detail?.translated == null
                ) {
                    translate(showAfter = true)
                }
                // 图片模型失效时清掉内存译图：无法再生的译图不该继续展示
                if (!avail.image && _uiState.value.imageTranslatingPage == null) {
                    clearImageTranslations()
                }
            }
        }
        load()
    }

    override fun onCleared() {
        super.onCleared()
        clearImageTranslations()
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
     * - 流式翻译进行中 → 仅切换显示，后台继续翻（发射后不管，已完成块已入缓存）；
     * - 正文模型不可用时只允许看已有缓存译文，无缓存则轻提示，绝不发起注定空跑的流；
     * - 正文尚无译文且未在翻 → 此刻才发起分块流式拉取（显式意图才花额度），并预切到译文展示态；
     * - 已有（或原文本身为空）→ 仅切换显示。
     */
    fun onReaderTranslateToggle() {
        val detail = _uiState.value.detail ?: return
        if (_uiState.value.novelStreamProgress != null) {
            toggleField(TranslateField.NOVEL)
            return
        }
        if (!_uiState.value.hasNovelModel) {
            if (detail.translated?.novelText != null) {
                toggleField(TranslateField.NOVEL)
            } else {
                feedback.show(R.string.detail_novel_model_unavailable)
            }
            return
        }
        val novelDone = detail.translated?.novelText != null || detail.novelText.isNullOrBlank()
        if (novelDone) {
            toggleField(TranslateField.NOVEL)
            return
        }
        startNovelStream()
        _uiState.update { it.copy(showTranslationAll = true, fieldOverrides = emptySet()) }
    }

    /**
     * 启动小说分块流式翻译：逐事件把累计译文写入 [DetailUiState.detail] 的 novelText，
     * 阅读器按 "已译前缀 + 剩余原文" 拼接实现边翻边读。失败块由仓库层回退原文继续流，
     * 全部结束后若有失败段给 snackbar——重试重启流，缓存命中让成功块秒过。
     */
    private fun startNovelStream() {
        val detail = _uiState.value.detail ?: return
        if (detail.novelText.isBlank()) return
        // 正文流必须以正文模型为闸门，文本通道的 key 不能放行
        if (!translationRepository.hasNovelModel()) return
        cancelNovelStream()
        lastManualTranslateIncludeNovel = true
        novelStreamJob = viewModelScope.launch {
            _uiState.update { it.copy(novelStreamProgress = 0) }
            try {
                translationRepository.translateNovelStreaming(
                    detail,
                    observeLanguageUseCase().value,
                ).collect { event ->
                    when (event) {
                        is NovelStreamEvent.Progress -> _uiState.update { state ->
                            val current = state.detail ?: return@update state
                            state.copy(
                                detail = current.copy(
                                    translated = withProgressiveNovel(current.translated, event.translatedSoFar),
                                ),
                                // 首块完成前 translated 仍为空，阅读器显示纯原文；末块剩余为空串，
                                // 由 Screen 的 isNullOrEmpty 判空避免拼接出尾部分隔
                                novelRemainder = current.novelText
                                    .takeIf { it.isNotBlank() }
                                    ?.let { it.substring(event.consumedOffset) },
                                novelStreamProgress =
                                if (event.totalChunks > 0) event.doneChunks * 100 / event.totalChunks else null,
                            )
                        }

                        is NovelStreamEvent.Completed -> {
                            if (event.failedCount > 0) feedback.show(R.string.detail_novel_partial_failed)
                            _uiState.update { state ->
                                val current = state.detail ?: return@update state
                                state.copy(
                                    detail = current.copy(
                                        translated = withProgressiveNovel(current.translated, event.translatedSoFar),
                                    ),
                                    novelStreamProgress = null,
                                    novelRemainder = null,
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d("PikuDiag", "novel stream fail work=$workId: ${e.message}")
                _uiState.update { it.copy(novelStreamProgress = null, novelRemainder = null) }
            } finally {
                if (novelStreamJob?.isCancelled != false) novelStreamJob = null
            }
        }
    }

    /** 渐进写入：把流式累计译文合入 translated（无则构造最小字段集，保证 chip 出现） */
    private fun withProgressiveNovel(old: TranslatedFields?, novelText: String): TranslatedFields? {
        if (novelText.isBlank()) return old
        val base = old ?: TranslatedFields()
        return if (base.novelText == novelText) base else base.copy(novelText = novelText)
    }

    /** 杀掉进行中的小说流并清理进度状态 */
    private fun cancelNovelStream() {
        novelStreamJob?.cancel()
        novelStreamJob = null
        _uiState.update { it.copy(novelStreamProgress = null, novelRemainder = null) }
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
     * @param forcedEntry 一次性重翻：指定则短字段强制用此模型，正文只走正文默认
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
                if (failed) notifyTranslateFailed()
                val current = state.detail ?: return@update state.copy(
                    translating = false,
                    fetchingNovelText = false,
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

    /** 换模型重翻：一次性重翻短字段，不写入默认设置，失败可重试并复用所选模型
     *  正文不在此列，正文模型由 AI 翻译设置统一决定，正文失败的重试在阅读器内重启流 */
    fun reTranslateWith(entry: ModelEntry) {
        translate(
            includeLongNovel = false,
            requireAutoEnabled = false,
            showAfter = true,
            forcedEntry = entry,
            forceShow = true,
        )
        _uiState.update { it.copy(showModelPicker = false) }
    }

    /** 打开换模型重翻弹窗，可选列表为空则不开 */
    fun openModelPicker() {
        if (retranslatePickableModels(_catalogModels.value).isEmpty()) return
        _uiState.update { it.copy(showModelPicker = true) }
    }

    /** 关闭模型选择弹窗 */
    fun dismissModelPicker() {
        _uiState.update { it.copy(showModelPicker = false) }
    }

    /** 手动翻译失败的 snackbar 重试：按上次入口原样重发
     *  正文流式的失败重试直接重启流，缓存命中让已成功块秒过，只补失败块
     *  不能走 onReaderTranslateToggle：部分失败后 novelText 已非空，会被误判为已有译文
     *  正文重试一律走正文默认模型，一次性强制模型只服务短字段 */
    fun retryLastTranslate() {
        if (lastManualTranslateIncludeNovel) {
            startNovelStream()
            return
        }
        val forced = lastForcedEntry
        if (forced != null) {
            reTranslateWith(forced)
            return
        }
        onTopBarTranslateClick()
    }

    // =====================================================================
    // 图片翻译
    // =====================================================================

    private val _translatedImages = mutableMapOf<Int, android.graphics.Bitmap>()
    private var imageTranslateJob: Job? = null

    /**
     * 详情页内嵌图集翻页：更新翻译按钮状态，同时记下阅读进度。
     * 这里收到的 [page] 从 0 起，落盘统一用 1 起的页码。
     */
    fun onImagePageChanged(page: Int) {
        _uiState.update {
            it.copy(showTranslatedImage = _translatedImages.containsKey(page))
        }
        saveImageProgress(page + 1)
    }

    /** 点击翻译按钮：已翻译则切换原图/译图，否则开始翻译 */
    fun onImageTranslateClick(page: Int) {
        if (_uiState.value.imageTranslatingPage != null) return
        if (_translatedImages.containsKey(page)) {
            _uiState.update { it.copy(showTranslatedImage = !it.showTranslatedImage) }
            return
        }
        translateImage(page)
    }

    private fun translateImage(page: Int) {
        val detail = _uiState.value.detail ?: return
        val imageUrl = detail.imageUrls.getOrNull(page) ?: return

        _uiState.update { it.copy(imageTranslatingPage = page) }
        imageTranslateJob?.cancel()
        imageTranslateJob = viewModelScope.launch {
            when (val result = translateImageWithRetry(imageUrl)) {
                is ImageTranslateResult.Success -> {
                    _translatedImages[page] = result.bitmap
                    _uiState.update {
                        it.copy(
                            translatedImages = HashMap(_translatedImages),
                            imageTranslatingPage = null,
                            showTranslatedImage = true,
                        )
                    }
                }
                is ImageTranslateResult.Failure -> {
                    Log.e(
                        "ImageTranslate",
                        "page=$page failed: ${result.error::class.simpleName}: ${result.error.message}",
                    )
                    _uiState.update { it.copy(imageTranslatingPage = null) }
                    val errorRes = imageErrorRes(result.error)
                    if (result.error.retryable) {
                        feedback.showAction(errorRes, R.string.detail_translate_retry) {
                            onImageTranslateClick(page)
                        }
                    } else {
                        // 拒绝/无模型是终态：不给「重试」按钮
                        feedback.show(errorRes)
                    }
                }
            }
        }
    }

    /** 失败自动重试一次：限流按 Retry-After 退避，其余固定短退避；拒绝/无模型不重试 */
    private suspend fun translateImageWithRetry(imageUrl: String): ImageTranslateResult {
        val first = translateImageOnce(imageUrl)
        val error = (first as? ImageTranslateResult.Failure)?.error ?: return first
        if (!error.retryable) return first
        delay(
            when (error) {
                is ImageTranslateError.RateLimited -> (error.retryAfterSec?.coerceIn(1L, 30L) ?: 5L) * 1000
                else -> 1500L + Random.nextLong(500)
            },
        )
        return translateImageOnce(imageUrl)
    }

    private suspend fun translateImageOnce(imageUrl: String): ImageTranslateResult {
        val result = withTimeoutOrNull(PAGE_TRANSLATE_TIMEOUT_MS) { doTranslateImage(imageUrl) }
        if (result != null) return result
        // 超时与外层取消都返回 null：取消必须继续抛，否则会被误报成网络错误
        if (!currentCoroutineContext().isActive) throw CancellationException("image translate cancelled")
        return ImageTranslateResult.Failure(ImageTranslateError.Network())
    }

    private fun imageErrorRes(error: ImageTranslateError): Int = when (error) {
        is ImageTranslateError.Refused -> R.string.image_translate_refused
        is ImageTranslateError.RateLimited -> R.string.image_translate_rate_limited
        is ImageTranslateError.DownloadFailed -> R.string.image_translate_download_failed
        is ImageTranslateError.NoModel -> R.string.image_translate_no_model
        is ImageTranslateError.Network -> R.string.image_translate_network
        is ImageTranslateError.Upstream, is ImageTranslateError.BadResponse -> R.string.image_translate_upstream
    }

    /** 手动翻译失败提示（带「重试」动作）；自动路径保持静默 */
    private fun notifyTranslateFailed() {
        feedback.showAction(
            R.string.detail_translate_failed,
            R.string.detail_translate_retry,
        ) { retryLastTranslate() }
    }

    private suspend fun doTranslateImage(imageUrl: String): ImageTranslateResult {
        // 1. 下载原图
        val imageBytes = downloadImage(imageUrl)
            ?: return ImageTranslateResult.Failure(ImageTranslateError.DownloadFailed())

        // 2. 获取提示词（跟随 App 语言设置）
        val language = observeLanguageUseCase().value
        val targetLang = TranslationRepository.targetLangName(language)
        val prompt = getImagePrompt(targetLang)

        // 3. 获取 image 模型的 baseUrl（优先用户选择，降级到目录默认）
        val imageEntry = translationRepository.effectiveImageEntry()
            ?: return ImageTranslateResult.Failure(ImageTranslateError.NoModel())

        // 4. 调用翻译引擎
        return imageTranslateEngine.translate(
            imageBytes = imageBytes,
            prompt = prompt,
            targetLang = targetLang,
            proxyBaseUrl = imageEntry.baseUrl,
        )
    }

    private suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .build()
            // 复用注入的 OkHttpClient（带连接池和超时配置）
            imageTranslateEngine.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ImageTranslate", "download failed: ${e.message}")
            null
        }
    }

    private fun getImagePrompt(targetLang: String): String {
        // 1. 尝试从 catalog 读取 image 模式的提示词
        val defaults = modelCatalogRepository.catalogDefaults.value
        val catalogPrompt = defaults?.prompts?.image?.get(langKey(targetLang))
        if (!catalogPrompt.isNullOrBlank()) return catalogPrompt

        // 2. 尝试从 image 模型自带的 prompts 读取
        val imageEntry = translationRepository.effectiveImageEntry()
        val modelPrompt = imageEntry?.prompts?.image?.get(langKey(targetLang))
        if (!modelPrompt.isNullOrBlank()) return modelPrompt

        // 3. 回退内置提示词
        return ImageTranslationPrompts.prompt(targetLang)
    }

    private fun langKey(targetLang: String): String = when (targetLang) {
        LlmTranslateEngine.TARGET_ZH -> "zh"
        LlmTranslateEngine.TARGET_JA -> "ja"
        else -> "en"
    }

    /** 清理图片翻译缓存（退出页面时调用） */
    fun clearImageTranslations() {
        imageTranslateJob?.cancel()
        _translatedImages.clear()
        _uiState.update {
            it.copy(
                translatedImages = emptyMap(),
                imageTranslatingPage = null,
                showTranslatedImage = false,
            )
        }
    }

    fun retry() = load()

    fun updatePassword(value: String) {
        // 用户动过输入框，内容即为本人输入，不再是预填建议
        _uiState.update { it.copy(password = value, passwordPrefilled = false) }
    }

    /**
     * 服务端拒绝原文的一次性提示（未知错误码兜底，与网页端 DispMsg(html) 对齐）。
     * 同一句在本次页面停留期间只提示一次，避免刷新/重载时反复弹。
     */
    private var lastServerNotice: String? = null

    private fun showServerNoticeOnce(detail: WorkDetail) {
        val notice = detail.serverNotice ?: return
        if (notice == lastServerNotice) return
        lastServerNotice = notice
        feedback.showText(notice)
    }

    fun submitPassword() {
        val pwd = _uiState.value.password
        if (pwd.isBlank() || _uiState.value.passwordLoading) return
        // 点了「解锁」即视为用户认可这个值，此后刷新可带上它重试
        _uiState.update { it.copy(passwordPrefilled = false) }
        // 复用已解析的锁页 detail，跳过重复的详情页 HTML 请求；解锁只发一次 append POST
        val existing = _uiState.value.detail
        viewModelScope.launch {
            _uiState.update { it.copy(passwordLoading = true) }
            loadWorkDetailUseCase(work, pwd, existing)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            detail = detail,
                            passwordLoading = false,
                            // 门卡类型由 Repository 单一决策写入 detail.gate
                            // （如匿名提交密码成功后服务端仍要求登录）
                            restrictionReason = detail.gate,
                        )
                    }
                    showServerNoticeOnce(detail)
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
                    // 失败（网络等）只清 loading，门卡状态保持原样——
                    // 门卡由 Repository 决策写入 detail.gate，失败路径不再自行推导
                    _uiState.update { it.copy(passwordLoading = false) }
                }
        }
    }

    /**
     * 自动进入：密码作品 + 未解锁 + 用户未在手动输入 + 存在已保存密码（仅服务端验证
     * 成功时写入，不可被查看/修改）→ 自动解锁。失败（-2，作者已改密码）时清除失效
     * 记录并显示密码框，由用户手动输入新密码，成功后重新保存（自愈，不会死循环）。
     * 无已保存密码时改为预填 [COMMON_WORK_PASSWORD]，只省掉输入、不代为提交。
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
        if (state.password.isNotBlank() && !state.passwordPrefilled) return // 用户正在输入，不打扰
        if (state.passwordLoading || state.loading) return
        viewModelScope.launch {
            val saved = workPasswordRepository.getPassword(workId)
            Log.d("PikuDiag", "maybeAutoUnlock work=$workId saved=${saved != null}")
            if (saved.isNullOrBlank()) {
                // 没有可复用的历史密码：预填最常见的作品密码，用户只需点一下「解锁」。
                // 只能在这里预填——提前写进 state 会被上面的「用户正在输入」判断挡掉自动解锁
                _uiState.update {
                    it.copy(password = COMMON_WORK_PASSWORD, passwordPrefilled = true)
                }
                return@launch
            }
            _uiState.update { it.copy(passwordLoading = true) }
            loadWorkDetailUseCase(work, saved, detail)
                .onSuccess { unlocked ->
                    val failed = unlocked.passwordError
                    Log.d(
                        "PikuDiag",
                        "maybeAutoUnlock work=$workId done failed=$failed urls=${unlocked.imageUrls.size}",
                    )
                    _uiState.update { it.copy(detail = unlocked, passwordLoading = false, restrictionReason = unlocked.gate) }
                    showServerNoticeOnce(unlocked)
                    recordHistory(unlocked)
                    if (failed) {
                        // 保存的密码已失效：清除，让用户手动输入新密码
                        workPasswordRepository.deletePassword(workId)
                    } else if (!unlocked.unlockBlocked && _uiState.value.loggedIn) {
                        loadFullImages(saved)
                    }
                    if (!failed) translate(showAfter = settingsRepository.aiTranslateEnabled.value)
                }
                .onFailure { error ->
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
        // 门卡态原图请求必然被拒，跳过（不烧限速槽）：任何门卡在场都不拉原图；
        // 门卡解除后（关注/登录/开 R-18）会重载，届时自然补拉
        if (detail.gate != null) return
        // 未解锁的密码作品拿不到原图，跳过无效请求（也不烧 append 限速槽）
        if (detail.passwordProtected && password.isBlank()) return
        // 未登录拿不到原图（服务端 -2）：直接跳过，不白跑请求，也不让退化的 append 链
        // 吃掉全局限速槽位（下一次进详情页的追加图会因此晚到）
        if (!state.loggedIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(fullImagesLoading = true) }
            loadWorkFullImagesUseCase(work, password)
                .onSuccess { urls ->
                    _uiState.update {
                        it.copy(fullImageUrls = urls, fullImagesLoading = false)
                    }
                }
                .onFailure { error ->
                    Log.d(
                        "PikuDiag",
                        "loadFullImages fail work=$workId error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    // 原图被拒（未登录/未开 R-18 的 -2 等）静默降级：缩略图仍可看，
                    // 不给逐作品的"去登录"提示（用户反馈：太像强制登录引导）
                    _uiState.update { it.copy(fullImagesLoading = false) }
                }
        }
    }

    // =====================================================================
    // 受限门卡（登录 / R-18 显示）
    // =====================================================================

    /**
     * 门卡的一键动作分发（含导航）。UI 只调这一个入口：
     * - LOGIN：发登录导航信号（UI 收到后跳登录页，本页留在返回栈）；
     * - FOLLOW：poipiku 内关注门 → App 内一键关注作者，成功后重载放行；
     * - FOLLOW_TWITTER / RETWEET：解锁动作在 Twitter/网页端完成
     *   （关注/转推），发信号让 UI 用浏览器打开作品页；
     * - ADULT：直接调与抽屉开关同款的 SwitchContentsViewModeF（匿名也生效，
     *   视图模式靠 cookie 存），成功后 showAdultContent 变化触发自动重载。
     */
    fun onUnlockRestriction() {
        when (_uiState.value.restrictionReason) {
            RestrictionReason.LOGIN -> requestLogin()
            RestrictionReason.FOLLOW -> followAuthorForUnlock()
            RestrictionReason.FOLLOW_TWITTER, RestrictionReason.RETWEET -> _openInBrowser.tryEmit(Unit)
            RestrictionReason.ADULT -> enableAdultContent()
            null -> Unit
        }
    }

    /** 门卡主按钮是「去登录」时的导航请求（UI 层收到后跳转） */
    private val _loginRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginRequest: SharedFlow<Unit> = _loginRequest.asSharedFlow()

    /** 关注门「在浏览器打开」请求（UI 层收到后拉起浏览器看作品页） */
    private val _openInBrowser = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openInBrowser: SharedFlow<Unit> = _openInBrowser.asSharedFlow()

    fun requestLogin() {
        _loginRequest.tryEmit(Unit)
    }

    /** 关注门「关注作者」：App 内一键关注（poipiku こっそりフォロー），
     *  成功后重载放行（实测服务端立即生效，无需等待） */
    private fun followAuthorForUnlock() {
        if (_uiState.value.followSending) return
        viewModelScope.launch {
            _uiState.update { it.copy(followSending = true) }
            val result = detailRepository.updateFollow(authorId)
            _uiState.update { it.copy(followSending = false) }
            when (result) {
                FollowResult.Followed -> {
                    feedback.show(R.string.detail_follow_sent)
                    // 关注门放行：重载后走密码框（此类作品常另有口令）或直接出图
                    load()
                }
                FollowResult.NotLoggedIn -> {
                    feedback.show(R.string.detail_follow_login_hint)
                    requestLogin()
                }
                else -> feedback.show(R.string.detail_follow_failed)
            }
        }
    }

    /** R-18 一键开启：直接发请求（与抽屉开关同款，匿名也生效——视图模式靠 cookie 存）。
     *  失败（服务端拒绝/网络）给轻提示；成功由设置流驱动自动重载 */
    private fun enableAdultContent() {
        val state = _uiState.value
        if (state.enablingAdultContent) return
        viewModelScope.launch {
            _uiState.update { it.copy(enablingAdultContent = true) }
            val ok = runCatching { adultContentRepository.setEnabled(true) }
                .onFailure { Log.d("PikuDiag", "enable adult fail: ${it.message}") }
                .getOrDefault(false)
            _uiState.update { it.copy(enablingAdultContent = false) }
            if (ok) {
                feedback.show(R.string.home_r18_on)
                // settingsRepository.showAdultContent 收到变化后自动清受限态并重载
            } else {
                feedback.show(R.string.detail_adult_enable_failed)
            }
        }
    }

    /** 快速收藏切换：单击星标 → 加入/移出默认收藏夹，并给出提示。 */
    fun quickFavorite() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            val added = favoriteRepository.toggleFavorite(currentWork(detail))
            feedback.show(
                if (added) R.string.detail_favorite_added else R.string.detail_favorite_removed,
            )
        }
    }

    /**
     * 把作品标签加入/移出个人自定义标签（用于搜索页与标签页的快捷入口）。
     * 已存在则移除，否则添加，操作后给出 snackbar 反馈。
     */
    fun toggleCustomTag(tag: String) {
        val state = _uiState.value
        viewModelScope.launch {
            if (tag in state.customTags) {
                removeCustomTagUseCase(tag)
                feedback.show(R.string.detail_tag_removed)
            } else {
                addCustomTagUseCase(tag)
                feedback.show(R.string.detail_tag_added)
            }
        }
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
            // 未登录拿不到原图，不必空等：直接存当前可见的图
            if (!detail.passwordProtected && !detail.warning && state.loggedIn) {
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
            _uiState.update { it.copy(savingImage = false) }
            feedback.show(
                if (result.isSuccess) R.string.detail_save_saved else R.string.detail_save_failed,
            )
        }
    }

    /**
     * 分享第 [page] 张图片，直接用作品页当前显示的缩略图。刻意不等原图：
     * 全尺寸图要额外请求且受 append 限速，等它分享就慢了。
     * [targetPackage] 为 null 时走系统分享面板，否则先检查定向 Intent 可解析才直跳，
     * 不可解析时由 UI 层回落到系统面板（微信/QQ 不一定接通用 ACTION_SEND）。
     *
     * 面板在准备期间保持打开（loading 转圈在被点的那一行），成功后 UI 层拉起
     * 分享面板并关闭；失败则通过 [feedback] 提示、并通过 [shareSheetDismiss] 收起面板。
     * 用户中途划掉面板可调 [cancelShare] 中断。
     */
    private var shareJob: Job? = null

    fun shareImage(page: Int, targetPackage: String? = null) {
        val state = _uiState.value
        if (state.sharingImage) return
        val detail = state.detail ?: return
        val item = state.viewerImages.getOrNull(page) ?: return
        // 原图页数多于缩略图时，越界的页只能用原图，否则会分享到重复的最后一张
        val url = if (page < detail.imageUrls.size) item.thumbnailUrl
        else item.fullUrl ?: item.thumbnailUrl
        shareJob?.cancel()
        shareJob = viewModelScope.launch {
            _uiState.update {
                it.copy(sharingImage = true, sharingTargetPackage = targetPackage)
            }
            try {
                val uri = imageShareHelper.getImageUri(url, workId, page)
                _uiState.update { it.copy(sharingImage = false, sharingTargetPackage = null) }
                _shareRequest.value = ImageShareRequest(
                    uri = uri,
                    targetPackage = targetPackage,
                    shareText = _uiState.value.shareUrl,
                )
            } catch (e: CancellationException) {
                _uiState.update { it.copy(sharingImage = false, sharingTargetPackage = null) }
                throw e
            } catch (e: Exception) {
                Log.d("PikuDiag", "shareImage fail work=$workId page=$page: ${e.message}", e)
                _uiState.update {
                    it.copy(sharingImage = false, sharingTargetPackage = null)
                }
                // 失败时面板仍开着（loading 刚结束），snackbar 会被盖住：先让 UI 收起面板
                _shareSheetDismiss.tryEmit(Unit)
                feedback.show(R.string.detail_share_failed)
            }
        }
    }

    /** 用户划掉面板时中断正在进行的分享下载，避免关了面板分享面板又弹出来。 */
    fun cancelShare() {
        shareJob?.cancel()
        shareJob = null
        if (_uiState.value.sharingImage) {
            _uiState.update { it.copy(sharingImage = false, sharingTargetPackage = null) }
        }
    }


    fun saveAllImages() {
        val state = _uiState.value
        val detail = state.detail ?: return
        val total = detail.imageUrls.size
        if (total == 0) return
        if (!runningSaveAlls.add(workId)) return
        appScope.launch {
            try {
                // 未登录拿不到原图，不必空等：直接存当前可见的图
                val needFull = !detail.passwordProtected && !detail.warning &&
                    state.loggedIn && state.fullImageUrls.isEmpty()
                if (needFull) {
                    loadFullImages()
                    withTimeoutOrNull(IMAGE_WAIT_MILLIS) {
                        _uiState.filter { it.fullImageUrls.isNotEmpty() }.first()
                    }
                }
                var ok = 0
                for (page in 0 until total) {
                    val url = _uiState.value.fullImageUrls.getOrNull(page) ?: detail.imageUrls[page]
                    runCatching { imageSaver.save(url, "Piku_${workId}_${page + 1}") }
                        .onSuccess { ok++ }
                }
                // 批量保存完成：带数字的结果文案（全失败沿用单张保存的静态失败文案）
                when {
                    ok == 0 -> feedback.show(R.string.detail_save_failed)
                    ok == total -> feedback.show(R.string.detail_save_all_success, total)
                    else -> feedback.show(R.string.detail_save_all_partial, ok, total - ok)
                }
            } finally {
                runningSaveAlls.remove(workId)
            }
        }
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
            // 重名会被数据层拒掉（同步按名字认收藏夹），要说清为什么没建成
            if (favoriteRepository.createFolder(name, currentWork(detail)) == null) {
                feedback.show(R.string.collection_folder_name_taken)
            }
        }
    }

    private fun WorkDetail.toHistoryWork(): Work = work.copy(
        title = title,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl.ifBlank { null },
        categoryName = categoryName,
        thumbnailUrl = stableThumbnail(this),
        imageCount = imageUrls.size,
        r18 = r18,
    )

    private fun currentWork(detail: WorkDetail): Work = detail.toHistoryWork()

    /**
     * 历史/收藏使用的稳定缩略图，优先级：
     * 1. 列表当前看不到内容（占位图/空图）时用已回填的真实 _360 缩略图（解锁后 append 的
     *    640 图转换，稳定不过期）——这类作品只有回填缓存里有真图
     * 2. 详情页第一张图（普通作品为 640 缩略图）——与列表卡片、详情页展示的是同一张
     * 3. 来源页缩略图（兜底），保证历史/收藏记录永远有图
     *
     * 列表已有真实缩略图时不看回填缓存：缓存里可能是旧版无条件回填写下的追加图（第 2 张起），
     * 会让记录里的作品图与列表/详情不一致（见 ThumbnailResolver.backfillThumbnailUrl）。
     */
    private fun stableThumbnail(detail: WorkDetail): String {
        if (ThumbnailResolver.needsThumbnailBackfill(work.thumbnailUrl)) {
            thumbnailResolver.thumbFor(work)?.let { return it }
        }
        return detail.imageUrls.firstOrNull() ?: work.thumbnailUrl
    }

    fun sendReaction(emoji: String) {
        val state = _uiState.value
        if (state.reactionSending) return
        val uid = authRepository.currentUserId()
        val detail = state.detail ?: return
        if (uid == null) {
            feedback.show(R.string.detail_reaction_login_hint)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(reactionSending = true) }
            val result = detailRepository.sendReaction(work.id, emoji, uid)
            _uiState.update { it.copy(reactionSending = false) }
            feedback.show(
                when (result) {
                    ReactionResult.Success -> R.string.detail_reaction_sent
                    ReactionResult.LimitReached -> R.string.detail_reaction_limit
                    is ReactionResult.Failure -> R.string.detail_reaction_send_failed
                },
            )
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
                        hasReacted = true,
                    )
                }
            }
        }
    }

    /**
     * 关注/取消关注作者。关注状态以详情页解析出的 [WorkDetail.followed] 为准，
     * 切换成功后原地更新，等待下次加载详情时由服务端渲染校正。
     */
    fun toggleFollow() {
        val state = _uiState.value
        if (state.followSending || state.detail == null) return
        if (!state.loggedIn) {
            feedback.show(R.string.detail_follow_login_hint)
            return
        }
        viewModelScope.launch {
            // 关注门卡在场时（底栏入口也能关注）：关注成功需重载放行——
            // 无密码作品直接拿真实内容，有密码作品进入密码框
            val wasGateFollow = _uiState.value.restrictionReason == RestrictionReason.FOLLOW
            _uiState.update { it.copy(followSending = true) }
            val result = detailRepository.updateFollow(authorId)
            _uiState.update { s ->
                val detail = s.detail ?: return@update s
                s.copy(
                    followSending = false,
                    detail = when (result) {
                        FollowResult.Followed -> detail.copy(followed = true)
                        FollowResult.Unfollowed -> detail.copy(followed = false)
                        else -> detail
                    },
                )
            }
            feedback.show(
                when (result) {
                    FollowResult.Followed -> R.string.detail_follow_sent
                    FollowResult.Unfollowed -> R.string.detail_unfollow_sent
                    FollowResult.NotLoggedIn -> R.string.detail_follow_login_hint
                    is FollowResult.Failure -> R.string.detail_follow_failed
                },
            )
            if (result == FollowResult.Followed && wasGateFollow) load()
        }
    }

    /**
     * 屏蔽/解除屏蔽作者。屏蔽状态以详情页解析出的 [WorkDetail.blocked] 为准。
     * 屏蔽成功时服务端会一并解除关注（网页端 UpdateBlock 同款行为），
     * 本地同步把关注态置为未关注，避免作者行按钮显示成"已关注"。
     */
    fun toggleBlock() {
        val state = _uiState.value
        val detail = state.detail ?: return
        // 自己查看自己的作品：入口在 UI 已隐藏，这里再兜一层，避免非法请求
        if (state.isSelf) return
        if (state.blockSending) return
        if (!state.loggedIn) {
            feedback.show(R.string.detail_block_login_hint)
            return
        }
        val target = !detail.blocked
        viewModelScope.launch {
            _uiState.update { it.copy(blockSending = true) }
            val result = detailRepository.updateBlock(
                authorId,
                target,
                name = detail.authorName,
                avatarUrl = detail.authorAvatarUrl.takeIf { it.isNotBlank() },
            )
            _uiState.update { s ->
                val current = s.detail ?: return@update s
                s.copy(
                    blockSending = false,
                    detail = when (result) {
                        BlockResult.Blocked -> current.copy(blocked = true, followed = false)
                        BlockResult.Unblocked -> current.copy(blocked = false)
                        else -> current
                    },
                )
            }
            feedback.show(
                when (result) {
                    BlockResult.Blocked -> R.string.detail_block_sent
                    BlockResult.Unblocked -> R.string.detail_unblock_sent
                    BlockResult.NotLoggedIn -> R.string.detail_block_login_hint
                    is BlockResult.Failure -> R.string.detail_block_failed
                },
            )
        }
    }

    private fun recordHistory(detail: WorkDetail) {
        viewModelScope.launch {
            recordHistoryUseCase(detail.toHistoryWork())
        }
    }

    private fun load() {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            // 不动 restrictionReason：门卡触发的重载期间让门卡留在屏上，
            // 新的 detail 到了由 detail.gate 决定去留（否则等待期会闪成"暂无图片"）
            _uiState.update {
                it.copy(
                    loading = true,
                    detailLoadingMore = false,
                    errorRes = null,
                    errorHintRes = null,
                    errorRetryable = true,
                )
            }
            // 保留已输入的密码：自动重登/刷新详情后已解锁作品不会重新锁回
            val retainedPassword = retainedUnlockPassword()
            loadWorkDetailUseCase(
                work = work,
                password = retainedPassword,
                onPartial = { partial ->
                    // 阶段一：HTML 解析完（门判定也已定下）就先把标题/描述/标签/作者/主图
                    // 画出来，不必等 append 与它前面的限速等待；追加图与正文到了原地补上。
                    // 不碰 loading：整页加载仍在进行，只是屏幕上已经有内容了
                    _uiState.update {
                        it.copy(
                            detail = partial,
                            detailLoadingMore = true,
                            errorRes = null,
                            errorHintRes = null,
                        )
                    }
                },
            )
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            detail = detail,
                            detailLoadingMore = false,
                            loading = false,
                            errorRes = null,
                            errorHintRes = null,
                            errorRetryable = true,
                            // 门卡类型由 Repository 单一决策写入 detail.gate
                            restrictionReason = detail.gate,
                        )
                    }
                    showServerNoticeOnce(detail)
                    recordHistory(detail)
                    // 密码作品的原图要带上解锁口令才拿得到，是保存/查看的前提，照旧即时预热；
                    // 普通作品等首图渲染完再解析（见 ensureFullImages）
                    if (detail.passwordProtected) loadFullImages(retainedPassword)
                    maybeAutoUnlock(detail)
                    translate(showAfter = settingsRepository.aiTranslateEnabled.value)
                }
                .onFailure { error ->
                    Log.d(
                        "PikuDiag",
                        "loadDetail fail work=$workId error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    // 404（作品已删除/链接有误）是终态：文案直说原因，且不给重试按钮。
                    // 作者被屏蔽时服务端把作品页 302 到其主页，同样是终态，但提示指向屏蔽。
                    // 其余错误（网络、解析、未知）保留重试；未知类型也要有文案，
                    // 否则 errorRes 为 null 会退化成一片空白页。
                    // 注意：受限门卡不走失败路径（Repository 正常返回带 gate 的 detail），
                    // 这里只需处理真正的错误
                    val notFound = error is AppError.NotFound
                    val blockedAuthor = error is AppError.BlockedAuthor
                    val errorRes = when {
                        blockedAuthor -> R.string.detail_blocked_notice
                        notFound -> R.string.detail_error_not_found
                        else -> (error as? AppError)?.toFeedErrorRes() ?: R.string.home_error_parse
                    }
                    val errorHintRes = when {
                        blockedAuthor -> R.string.detail_blocked_notice_hint
                        notFound -> R.string.detail_error_not_found_hint
                        else -> null
                    }
                    val retryable = !notFound && !blockedAuthor
                    // 屏幕上已经有内容（这次画出来的部分态，或上一次加载的详情）时，
                    // 失败只轻提示：不能把用户已经看到的内容换成整页错误页——网页端同样
                    // 如此，追加图/正文拉不到不挡整页。只有真的没东西可显示才让错误页接管
                    val hasContent = _uiState.value.detail != null
                    // 屏幕上有内容时也把 errorRes 留着：UI 据此在图区角落给一个常驻「重试」
                    // （只靠 snackbar 的话，提示消失后用户没有重来的入口）
                    _uiState.update {
                        it.copy(
                            loading = false,
                            detailLoadingMore = false,
                            errorRes = errorRes,
                            errorHintRes = if (hasContent) null else errorHintRes,
                            errorRetryable = retryable,
                        )
                    }
                    if (hasContent) {
                        // 终态（作品已删除 / 作者被屏蔽）不给重试：重试只会反复失败
                        if (retryable) {
                            feedback.showAction(errorRes, R.string.home_retry) { retry() }
                        } else {
                            feedback.show(errorRes)
                        }
                    }
                }
        }
    }

    /**
     * 可用于后续请求的解锁口令。预填值不算——用户还没确认过，不能替他发出去。
     */
    private fun retainedUnlockPassword(): String =
        if (_uiState.value.passwordPrefilled) "" else _uiState.value.password

    /**
     * 解析全尺寸原图 URL（图片字节仍由查看器按需下载）。两个触发点：首图渲染完成
     * （不和首图抢带宽，且通常赶在用户点图之前）、查看器打开（首图还没画出来就被点开的兜底）。
     * 幂等，内部有"已就绪/进行中"守卫。
     */
    fun ensureFullImages() {
        loadFullImages(retainedUnlockPassword())
    }

    private companion object {
        /** 批量保存进行中的作品集合：跨 VM 实例防重入 */
        val runningSaveAlls: MutableSet<Long> = ConcurrentHashMap.newKeySet()

        /** 预填进解锁框的常见作品密码（Poipiku 上大量作品沿用 Pixiv 的 `yes` 惯例） */
        const val COMMON_WORK_PASSWORD = "yes"

        /** 长按保存时等待原图加载的最长时间*/
        const val IMAGE_WAIT_MILLIS = 8_000L

        /** 单页图片翻译总超时（含下载）；超时按可重试的网络错误处理 */
        const val PAGE_TRANSLATE_TIMEOUT_MS = 180_000L

        /** 底部菜单新手引导已展示标记 */
        const val KEY_BOTTOM_GUIDE_SHOWN = "detail_bottom_guide_shown_v2"
        const val KEY_IMAGE_HINT_SHOWN = "detail_image_hint_shown_v1"
    }
}
