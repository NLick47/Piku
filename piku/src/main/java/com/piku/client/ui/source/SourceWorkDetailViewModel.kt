package com.piku.client.ui.source

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.ImageRouteController
import com.piku.client.data.remote.ImageUpstream
import com.piku.client.data.remote.translation.ImageTranslateEngine
import com.piku.client.data.remote.translation.ImageTranslateResult
import com.piku.client.data.remote.translation.ImageTranslationPrompts
import com.piku.client.data.remote.translation.LlmTranslateEngine
import com.piku.client.data.remote.translation.ModelCatalogRepository
import com.piku.client.data.remote.translation.TranslationRepository
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import com.piku.client.domain.model.WorkStats
import com.piku.client.domain.translation.TagsTranslationController
import com.piku.client.domain.model.mergeTranslatedFields
import com.piku.client.domain.source.SourceRegistry
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.usecase.ObserveLanguageUseCase
import com.piku.client.domain.usecase.RecordHistoryUseCase
import com.piku.client.ui.detail.DetailViewModel
import com.piku.client.ui.detail.ViewerImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class SourceWorkDetailViewModel @Inject constructor(
    private val sourceRegistry: SourceRegistry,
    private val recordHistoryUseCase: RecordHistoryUseCase,
    private val translationRepository: TranslationRepository,
    private val imageTranslateEngine: ImageTranslateEngine,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val observeLanguageUseCase: ObserveLanguageUseCase,
    private val imageSaver: com.piku.client.data.local.ImageSaver,
    private val imageShareHelper: com.piku.client.data.local.ImageShareHelper,
    @Named("image") private val imageClient: OkHttpClient,
    private val imageRouteController: ImageRouteController,
) : ViewModel() {

    /** 保存/分享结果的就地提示，由宿主 SnackbarHost 呈现（与 poipiku 详情同款通道） */
    val feedback = com.piku.client.ui.common.FeedbackChannel()

    data class UiState(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val detail: WorkDetail? = null,
        val translating: Boolean = false,
        val showTranslationAll: Boolean = false,
        /** 标签默认显示态：「自动翻译标签」设置，VM 内 collect 保持同步 */
        val autoTranslateTags: Boolean = true,
        /** 用户点过标签 chip 后的显式选择；null = 跟随设置，仅浏览内有效 */
        val tagsOverride: Boolean? = null,
        /** 标签懒翻译进行中：chip 半透明防连点 */
        val tagsTranslating: Boolean = false,
        val hasTextModel: Boolean = false,
        val hasImageModel: Boolean = false,
        val translatedImages: Map<Int, Bitmap> = emptyMap(),
        val imageTranslatingPage: Int? = null,
        val showTranslatedImage: Boolean = false,
        val savingImage: Boolean = false,
        val sharingImage: Boolean = false,
        val sharingTargetPackage: String? = null,
        /** 看图页（regular 展示 + original 原图）：查看器与保存/分享都从这里取 */
        val pages: List<SourceWorkPage> = emptyList(),
        /** 作品统计与元信息；poipiku 无此数据，pixiv 详情页据此渲染数据条 */
        val stats: WorkStats? = null,
        /** 底部相关作品；pixiv 才有，随详情一起回来，没有就不显示这一块 */
        val related: List<Work> = emptyList(),
        /** 计数缩写按语言分档（中日「万」/ 英文「K」） */
        val language: AppLanguage = AppLanguage.SYSTEM,
    ) {
        val hasTranslation: Boolean get() = detail?.translated?.hasAny == true

        /** 标签当前显示态：用户点过 chip 用覆盖值，否则跟随设置 */
        val showTranslatedTags: Boolean get() = tagsOverride ?: autoTranslateTags

        /** 与 poipiku 详情同构：轻量档打底 + 清晰档覆盖（pixiv 是 540 打底、1200 覆盖，原图只走保存） */
        val viewerImages: List<ViewerImage>
            get() = pages.map { page ->
                ViewerImage(
                    thumbnailUrl = page.url,
                    fullUrl = page.fullUrl.takeIf { it.isNotBlank() },
                )
            }

    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var loadedWorkId: Long = -1
    private val _shareRequest = MutableStateFlow<DetailViewModel.ImageShareRequest?>(null)
    val shareRequest: StateFlow<DetailViewModel.ImageShareRequest?> = _shareRequest.asStateFlow()
    /** 失败时收起面板的一次性事件。replay=0：依赖「面板打开期间 collector 必在」——失败只发生在点击后的下载流程里 */
    private val _shareSheetDismiss = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shareSheetDismiss: kotlinx.coroutines.flow.SharedFlow<Unit> = _shareSheetDismiss.asSharedFlow()
    private var shareJob: Job? = null
    private val translatedImages = mutableMapOf<Int, Bitmap>()
    private var imageTranslateJob: Job? = null

    /** 标签翻译编排（与 poipiku 详情页共用） */
    private val tagsTranslation = TagsTranslationController(
        repository = translationRepository,
        scope = viewModelScope,
        read = {
            val s = _ui.value
            TagsTranslationController.TagsTranslationState(
                detail = s.detail,
                showTranslated = s.showTranslatedTags,
                translating = s.tagsTranslating,
                override = s.tagsOverride,
            )
        },
        write = { transform ->
            _ui.update { s ->
                val next = transform(
                    TagsTranslationController.TagsTranslationState(
                        detail = s.detail,
                        showTranslated = s.showTranslatedTags,
                        translating = s.tagsTranslating,
                        override = s.tagsOverride,
                    )
                )
                s.copy(
                    detail = next.detail,
                    tagsTranslating = next.translating,
                    tagsOverride = next.override,
                )
            }
        },
        language = { observeLanguageUseCase().value },
        onFailed = { feedback.show(R.string.detail_translate_failed) },
    )

    init {
        viewModelScope.launch {
            translationRepository.roleModelAvailability.collect { avail ->
                _ui.update { it.copy(hasTextModel = avail.text, hasImageModel = avail.image) }
            }
        }
        viewModelScope.launch {
            // 标签默认显示态跟随「自动翻译标签」；用户点过 chip 后的覆盖值不受设置变化影响
            settingsRepository.autoTranslateTags.collect { enabled ->
                _ui.update { it.copy(autoTranslateTags = enabled) }
            }
        }
    }

    /** 打开作品时加载一次；[force] 供失败重试强制重拉 */
    fun load(work: Work, force: Boolean = false) {
        if (!force && loadedWorkId == work.id && _ui.value.detail != null) return
        loadedWorkId = work.id
        translatedImages.clear()
        imageTranslateJob?.cancel()
        // 秒进：列表里自带的标题/作者/缩略图先上屏打底，接口回来再补全简介/统计/清晰图
        _ui.value = UiState(
            language = observeLanguageUseCase().value,
            detail = WorkDetail(
                title = work.title,
                authorName = work.authorName,
                authorAvatarUrl = work.authorAvatarUrl.orEmpty(),
                categoryCd = -1,
                categoryName = "",
                imageUrls = listOf(work.thumbnailUrl),
                tags = emptyList(),
                r18 = work.r18,
            ),
        )
        viewModelScope.launch {
            // 按作品自己的源取页：跨源列表（收藏/历史）点进来的作品不必等于当前首页源
            val source = sourceRegistry.byId(work.source)
            // 与榜单同源的过滤口径：关掉成人内容显示时，相关作品里的 R-18 也一并去掉
            val adultEnabled = settingsRepository.showAdultContent.first()
            // 取页与取文本并行：总耗时从两次相加变成取最慢的一个
            coroutineScope {
                val pagesDeferred = async { source.workPages(work) }
                val textDeferred = async { source.workDetailText(work) }
                val pages = pagesDeferred.await()
                val text = textDeferred.await().getOrNull()
                pages.fold(
                    onSuccess = { list ->
                        if (list.isEmpty()) {
                            _ui.update { it.copy(loading = false, failed = true, detail = null) }
                            return@fold
                        }
                        val detail = WorkDetail(
                            title = work.title,
                            description = text?.description.orEmpty(),
                            authorName = work.authorName,
                            authorAvatarUrl = work.authorAvatarUrl.orEmpty(),
                            categoryCd = -1,
                            categoryName = "",
                            imageUrls = inlineImageUrls(list, upgradeToFull = worthUpgradingInline(list, imageRouteController)),
                            tags = text?.tags.orEmpty(),
                            r18 = work.r18,
                        )
                        _ui.update {
                            it.copy(loading = false, detail = detail, pages = list, stats = text?.stats)
                        }
                        // 相关作品单独一路：详情先出来，它后到就补在底部，取不到就算了
                        launch {
                            val related = source.relatedWorks(work).getOrNull().orEmpty()
                            _ui.update {
                                it.copy(related = related.filter { item -> adultEnabled || !item.r18 })
                            }
                        }
                        // 与 poipiku 详情一致：打开即记历史（upsert 去重）
                        recordHistoryUseCase(work)
                        // 与 poipiku 详情一致：开了 AI 翻译就自动译一次（失败静默）
                        if (settingsRepository.aiTranslateEnabled.value) translate()
                    },
                    onFailure = {
                        // 打底的预览一并撤掉：重进时「detail != null」守卫才会放行自动重拉
                        _ui.update { it.copy(loading = false, failed = true, detail = null) }
                    },
                )
            }
        }
    }

    /**
     * 标题行 chip 短按：已有译文 = 整页原/译切换（不含标签）；没有 = 立即翻短字段。
     * 原顶栏翻译图标的职责，图标去掉后由页内这颗 chip 承担。
     */
    fun onTopBarTranslateClick() {
        val state = _ui.value
        if (state.detail?.translated?.hasAny == true) {
            _ui.update { it.copy(showTranslationAll = !it.showTranslationAll) }
            return
        }
        translate()
    }

    /** chip 长按：换模型重翻（pixiv 详情页没有模型选择器，直接按当前模型重翻一次） */
    fun onRetranslate() {
        translate()
    }

    /**
     * 标签区「译」：显示态与正文分离（正文那颗统一切换不碰标签）。
     * 编排在 [tagsTranslation]，两个源的详情页共用同一份。
     */
    fun onToggleTagsTranslation() = tagsTranslation.toggle()

    private fun translate() {
        val detail = _ui.value.detail ?: return
        if (_ui.value.translating) return
        if (!translationRepository.hasKey()) {
            Log.d("PikuDiag", "source detail translate skip work=$loadedWorkId: no api key")
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(translating = true) }
            val outcome = runCatching {
                translationRepository.translate(detail, observeLanguageUseCase().value)
            }.getOrNull()
            _ui.update { state ->
                val current = state.detail ?: return@update state.copy(translating = false)
                val fields = outcome?.takeUnless { it.failed }?.fields
                val merged = mergeTranslatedFields(current.translated, fields)
                // 首次拿到译文翻到译文视图；已有译文后的重跑不动用户的显式选择（同 poipiku）
                val show = merged != null && (current.translated == null || state.showTranslationAll)
                state.copy(
                    translating = false,
                    detail = current.copy(translated = merged),
                    showTranslationAll = show,
                )
            }
        }
    }

    // ---------------- 保存与分享（与 poipiku 详情同款语义，数据换成源的作品页） ----------------

    private val workUrl: String get() = "https://www.pixiv.net/artworks/$loadedWorkId"

    /** 保存单页：有原图存原图，与 poipiku 一致 */
    fun saveImage(page: Int) {
        val pages = _ui.value.pages
        val item = pages.getOrNull(page) ?: return
        if (_ui.value.savingImage) return
        viewModelScope.launch {
            _ui.update { it.copy(savingImage = true) }
            val url = item.originalUrl.ifBlank { item.fullUrl }.ifBlank { item.url }
            val result = runCatching { imageSaver.save(url, "Piku_${loadedWorkId}_${page + 1}") }
            _ui.update { it.copy(savingImage = false) }
            feedback.show(
                if (result.isSuccess) R.string.detail_save_saved else R.string.detail_save_failed,
            )
        }
    }

    private var saveAllRunning = false

    /** 保存全部：逐页保存，结果按成功/失败数报（与 poipiku 同文案）；带重入保护防重复入库 */
    fun saveAllImages() {
        val pages = _ui.value.pages
        if (pages.isEmpty() || saveAllRunning) return
        saveAllRunning = true
        viewModelScope.launch {
            try {
            var ok = 0
            pages.forEachIndexed { index, item ->
                val url = item.originalUrl.ifBlank { item.fullUrl }.ifBlank { item.url }
                val result = runCatching { imageSaver.save(url, "Piku_${loadedWorkId}_${index + 1}") }
                if (result.isSuccess) ok += 1
            }
            val failed = pages.size - ok
            when {
                failed == 0 -> feedback.show(R.string.detail_save_all_success, pages.size)
                ok == 0 -> feedback.show(R.string.detail_save_failed)
                else -> feedback.show(R.string.detail_save_all_partial, ok, failed)
            }
            } finally {
                saveAllRunning = false
            }
        }
    }

    /**
     * 分享第 [page] 张：用页面上当前展示的 regular（与 poipiku 同策略——不等原图）。
     * [targetPackage] 为 null 走系统分享面板；定向不可解析时由 UI 回落系统面板。
     */
    fun shareImage(page: Int, targetPackage: String? = null) {
        val item = _ui.value.pages.getOrNull(page) ?: return
        if (_ui.value.sharingImage) return
        shareJob?.cancel()
        shareJob = viewModelScope.launch {
            _ui.update { it.copy(sharingImage = true, sharingTargetPackage = targetPackage) }
            try {
                val uri = imageShareHelper.getImageUri(item.fullUrl.ifBlank { item.url }, loadedWorkId, page)
                _ui.update { it.copy(sharingImage = false, sharingTargetPackage = null) }
                _shareRequest.value = DetailViewModel.ImageShareRequest(
                    uri = uri,
                    targetPackage = targetPackage,
                    shareText = workUrl,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _ui.update { it.copy(sharingImage = false, sharingTargetPackage = null) }
                throw e
            } catch (e: Exception) {
                Log.d("SourceDetailTranslate", "share fail work=$loadedWorkId page=$page: ${e.message}")
                _ui.update { it.copy(sharingImage = false, sharingTargetPackage = null) }
                // 失败时面板仍开着，先让 UI 收起（否则 snackbar 被面板盖住），与 poipiku 同
                _shareSheetDismiss.tryEmit(Unit)
                feedback.show(R.string.detail_share_failed)
            }
        }
    }

    /** 用户划掉面板时中断分享下载，避免关了面板分享面板又弹出来 */
    fun cancelShare() {
        shareJob?.cancel()
        shareJob = null
        if (_ui.value.sharingImage) {
            _ui.update { it.copy(sharingImage = false, sharingTargetPackage = null) }
        }
    }

    fun clearShareRequest() {
        _shareRequest.value = null
    }

    /**
     * 退出详情页时释放重体量状态：图片翻译位图与在途任务。VM 本身按作品驻留在导航栈里，
     * 不释放的话浏览多个作品会累积位图（poipiku 详情对应 clearImageTranslations 的职责）。
     */
    fun release() {
        imageTranslateJob?.cancel()
        shareJob?.cancel()
        translatedImages.clear()
        _ui.update {
            it.copy(
                translatedImages = emptyMap(),
                imageTranslatingPage = null,
                showTranslatedImage = false,
                sharingImage = false,
                sharingTargetPackage = null,
            )
        }
        _shareRequest.value = null
    }

    // ---------------- 图片翻译（与 poipiku 详情同款：点按钮翻译，再点切原/译） ----------------

    fun onImagePageChanged(page: Int) {
        _ui.update { it.copy(showTranslatedImage = translatedImages.containsKey(page)) }
    }

    fun onImageTranslateClick(page: Int) {
        if (_ui.value.imageTranslatingPage != null) return
        if (translatedImages.containsKey(page)) {
            _ui.update { it.copy(showTranslatedImage = !it.showTranslatedImage) }
            return
        }
        val imageUrl = _ui.value.detail?.imageUrls?.getOrNull(page) ?: return
        _ui.update { it.copy(imageTranslatingPage = page) }
        imageTranslateJob?.cancel()
        imageTranslateJob = viewModelScope.launch {
            val result = translateImageOnce(imageUrl)
            when (result) {
                is ImageTranslateResult.Success -> {
                    translatedImages[page] = result.bitmap
                    _ui.update {
                        it.copy(
                            translatedImages = HashMap(translatedImages),
                            imageTranslatingPage = null,
                            showTranslatedImage = true,
                        )
                    }
                }
                is ImageTranslateResult.Failure -> {
                    // 与 poipiku 的失败重试语义一致：复位按钮，用户再点一次即重试
                    Log.e(
                        "SourceDetailTranslate",
                        "page=$page failed: ${result.error::class.simpleName}: ${result.error.message}",
                    )
                    _ui.update { it.copy(imageTranslatingPage = null) }
                }
            }
        }
    }

    private suspend fun translateImageOnce(imageUrl: String): ImageTranslateResult {
        val bytes = downloadImage(imageUrl)
            ?: return ImageTranslateResult.Failure(
                com.piku.client.data.remote.translation.ImageTranslateError.DownloadFailed(),
            )
        val language = observeLanguageUseCase().value
        val targetLang = TranslationRepository.targetLangName(language)
        val entry = translationRepository.effectiveImageEntry()
            ?: return ImageTranslateResult.Failure(
                com.piku.client.data.remote.translation.ImageTranslateError.NoModel(),
            )
        return imageTranslateEngine.translate(
            imageBytes = bytes,
            prompt = imagePrompt(targetLang),
            targetLang = targetLang,
            proxyBaseUrl = entry.baseUrl,
        )
    }

    /** 走 App 的图片客户端取字节：i.pximg.net 需要它带的 DoH/SNI 与 pixiv Referer */
    private suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            imageClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SourceDetailTranslate", "download failed: ${e.message}")
            null
        }
    }

    /** 提示词来源与 poipiku 详情同序：目录默认 → 模型自带 → 内置兜底 */
    private fun imagePrompt(targetLang: String): String {
        val key = when (targetLang) {
            LlmTranslateEngine.TARGET_ZH -> "zh"
            LlmTranslateEngine.TARGET_JA -> "ja"
            else -> "en"
        }
        val catalogPrompt = modelCatalogRepository.catalogDefaults.value?.prompts?.image?.get(key)
        if (!catalogPrompt.isNullOrBlank()) return catalogPrompt
        val modelPrompt = translationRepository.effectiveImageEntry()?.prompts?.image?.get(key)
        if (!modelPrompt.isNullOrBlank()) return modelPrompt
        return ImageTranslationPrompts.prompt(targetLang)
    }
}

/** 内联图区按档取图：默认打底档，实测放行才升清晰档；档位缺失逐级退，原图只留给保存 */
internal fun inlineImageUrls(pages: List<SourceWorkPage>, upgradeToFull: Boolean): List<String> =
    pages.mapNotNull { page ->
        val first = if (upgradeToFull) page.fullUrl else page.url
        val second = if (upgradeToFull) page.url else page.fullUrl
        first.ifBlank { second }.ifBlank { null }
    }

/** 升不升清晰档由各图自己上游的实测说了算：认不出主机或没读数都停在打底档 */
internal fun worthUpgradingInline(pages: List<SourceWorkPage>, controller: ImageRouteController): Boolean =
    pages.isNotEmpty() && pages.all { page ->
        val upstream = page.fullUrl.toHttpUrlOrNull()?.host?.let(ImageUpstream::of)
        upstream != null && controller.worthFullImageInline(upstream)
    }
