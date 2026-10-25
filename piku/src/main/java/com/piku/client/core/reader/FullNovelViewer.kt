package com.piku.client.core.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.piku.client.R
import com.piku.client.core.ui.common.LinkSegment
import com.piku.client.core.ui.common.LinkText
import com.piku.client.core.storage.prefs.NovelReaderSettings
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.source.pixiv.data.repository.NovelBlock
import com.piku.client.source.pixiv.data.repository.splitNovelBlocks
import com.piku.client.core.ui.theme.LocalDarkTheme
import com.piku.client.core.ui.theme.NovelReaderTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val NOVEL_FONT_MIN = SettingsRepository.NOVEL_FONT_MIN
internal const val NOVEL_FONT_MAX = SettingsRepository.NOVEL_FONT_MAX
internal const val NOVEL_FONT_DEFAULT = SettingsRepository.NOVEL_FONT_DEFAULT
internal const val NOVEL_LINE_HEIGHT_MIN = SettingsRepository.NOVEL_LINE_HEIGHT_MIN
internal const val NOVEL_LINE_HEIGHT_MAX = SettingsRepository.NOVEL_LINE_HEIGHT_MAX
internal const val NOVEL_BRIGHTNESS_MIN = SettingsRepository.NOVEL_BRIGHTNESS_MIN

private const val AUTO_HIDE_DELAY_MS = 2500L
private val POIPIKU_WORK_REGEX = Regex("""https?://poipiku\.com/(\d+)/(\d+)\.html""")

/** 内嵌图占位比例（宽/高）：pixiv 插画多是竖图，先按 2:3 估位，出真尺寸后按真比例校正 */
private const val IMAGE_ASPECT_DEFAULT = 0.7f
/** 取图失败时留的高度：只留一条痕迹，别让一张废图占满一屏 */
private val IMAGE_FAILED_HEIGHT = 120.dp
/**
 * 同时在途的图块请求上限。放行窗口是按视口算的，一屏多高就能塞进多少张（约 7 张），
 * 一次全放出去等于 7 张 8MB 级解码 + 7 次重排一起压主线程 —— 每帧放行 1 张只压了启动速率，
 * 压不住在途总量，所以再加这道闸。
 */
private const val IMAGE_MAX_IN_FLIGHT = 2

/** 正文左右边距 */
private val CONTENT_PADDING_HORIZONTAL = 20.dp
/** 正文顶部/底部留白（浮动栏之外），也让正文在栏下淡出 */
private val CONTENT_PADDING_TOP = 84.dp
private val CONTENT_PADDING_BOTTOM = 112.dp
/** 章节导航条的高度：底衬渐变要跟着变高，正文才会在栏下淡出 */
private val CHAPTER_BAR_HEIGHT = 56.dp
/** 章末「下一章」按钮的圆角 */
private val ReaderFooterShape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)

/**
 * 全屏小说阅读器：
 * - 配色五档可选（米黄/纸白/护眼/深色/纯黑），独立于系统主题，持久化
 * - 浮动玻璃栏承载进度、原/译切换与字号，Aa 展开字号/行距/亮度/字体/底色设置面板
 * - 字号与行距可调且改完停在原处；亮度与常亮直接作用于当前窗口
 * - 长按文本可选中复制（SelectionContainer），点击正文收起/唤出控制栏
 */
@Composable
fun FullNovelViewer(
    text: String,
    title: String,
    settings: NovelReaderSettings,
    initialPercent: Int,
    /** 第二参为是否落盘：拖动中只更新内存（实时预览），松手/点选才写盘 */
    onSettingsChange: (NovelReaderSettings, Boolean) -> Unit,
    onProgressSave: (Int) -> Unit,
    onClose: () -> Unit,
    onWorkClick: (Long, Long, String) -> Unit,
    /** 正文有原文且有可用正文模型或已有缓存译文时显示原/译切换，未翻译时点击触发拉取 */
    translationAvailable: Boolean = false,
    /** 展示的正文译文是否为历史缓存，模型已下线；为真且显示译文时正文上方标注 */
    novelStale: Boolean = false,
    showTranslation: Boolean = false,
    /** 拉取中：切换钮禁用并显示进行中，防连点重复扣额度 */
    translating: Boolean = false,
    /** 任何翻译请求在途（含元数据）时禁用点击，避免触发被吞 */
    busy: Boolean = false,
    /** 小说分块流式翻译进度（百分比）；非 null 时进度行的百分比位置改为显示翻译进度 */
    novelStreamProgress: Int? = null,
    onToggleTranslation: () -> Unit = {},
    /**
     * 分章正文（≥2 章时按章渲染并出章节导航：上一章/目录/下一章 + 章末下一章入口）。
     * 空或单章走 [text] 的单篇路径，与从前一致；进度始终按整部百分比落盘。
     */
    chapters: List<com.piku.client.core.model.NovelChapter> = emptyList(),
    /** 设置面板底部的只读缓存状态；null = 不显示（图片作品/未缓存） */
    cacheStatus: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val theme = remember(settings.themeId) { NovelReaderTheme.fromId(settings.themeId) }
    val currentOnProgressSave by rememberUpdatedState(onProgressSave)
    // 正文链接化的稳定入口：宿主每重组一次都会给出新的 lambda 实例，直接把它当 remember key
    // 会让整章链接重解析；这里只在点击时读最新回调
    val workClickState = rememberUpdatedState(onWorkClick)
    val workClickHandler = remember {
        { authorId: Long, workId: Long, title: String ->
            workClickState.value(authorId, workId, title)
        }
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    // 视口高度（px）：图块放行窗口要用
    var viewportPx by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val scrollState = remember { ScrollState(0) }
    // 读到的百分比：改字号/行距会重排，靠它把读者送回原处
    val position = remember { ReaderPosition() }

    // 章节模式：≥2 章按章渲染，进度仍是整部百分比（与不分章共用进度键）
    val chaptered = chapters.size > 1
    val chapterLengths = remember(chapters) { chapters.map { it.length } }
    // 首放的落点：整部百分比 → 章 + 章内百分比
    val startPosition = remember { NovelChapterProgress.locate(chapterLengths, initialPercent) }
    var chapterIndex by remember { mutableIntStateOf(if (chaptered) startPosition.chapterIndex else 0) }
    // 待恢复的章内百分比：首放/目录跳转时非零，定位完清零
    var chapterStartPercent by remember { mutableIntStateOf(if (chaptered) startPosition.localPercent else 0) }
    var chapterSheetOpen by remember { mutableStateOf(false) }
    val bodyText = if (chaptered) chapters[chapterIndex].text else text

    fun refreshAutoHide() {
        controlsVisible = true
        autoHideJob?.cancel()
        // 设置面板展开时不自动收起，否则面板会自己消失
        if (panelOpen) return
        autoHideJob = scope.launch {
            delay(AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    fun togglePanel() {
        panelOpen = !panelOpen
        // 展开时把在跑的自动收起掐掉；收起时重新计时
        if (panelOpen) {
            autoHideJob?.cancel()
            controlsVisible = true
        } else {
            refreshAutoHide()
        }
    }

    fun goToChapter(target: Int) {
        if (!chaptered) return
        val index = target.coerceIn(0, chapters.lastIndex)
        if (index == chapterIndex) {
            scope.launch { scrollState.scrollTo(0) }
            return
        }
        chapterStartPercent = 0
        chapterIndex = index
        refreshAutoHide()
    }

    /** 当前落点换算成整部百分比（章节模式）或章内百分比（单篇） */
    fun progressToSave(): Int {
        val local = readerProgressPercent(scrollState.value, scrollState.maxValue)
        return if (chaptered) NovelChapterProgress.overall(chapterLengths, chapterIndex, local) else local
    }

    LaunchedEffect(Unit) { refreshAutoHide() }

    // 恢复进度：内容可滚动后按保存的百分比跳转。百分比与字号/内容长度无关，
    // 字号调整后也不会错位；maxValue 为 0（不足一屏）则保持顶部。
    // 章节模式的落点由下面的章节定位接管（整部百分比先换算成章 + 章内百分比）
    LaunchedEffect(scrollState, initialPercent) {
        if (chaptered) return@LaunchedEffect
        if (initialPercent <= 0) return@LaunchedEffect
        val max = snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .first()
        scrollState.scrollTo(readerScrollTarget(initialPercent, max))
    }

    // 章节定位：首放/换章后按待恢复的章内百分比滚动；换章先回章首。
    // 正文重排后 maxValue 才有效，等它 > 0 再滚。
    LaunchedEffect(chaptered, chapterIndex, scrollState) {
        if (!chaptered) return@LaunchedEffect
        val local = chapterStartPercent
        chapterStartPercent = 0
        // 字号/行距重排的回位逻辑读 position.percent：换章后要对齐本章的落点
        position.percent = local
        if (local <= 0) {
            scrollState.scrollTo(0)
            return@LaunchedEffect
        }
        val max = snapshotFlow { scrollState.maxValue }.filter { it > 0 }.first()
        scrollState.scrollTo(readerScrollTarget(local, max))
    }

    // 排版一变（字号、行距）就按百分比回到原处：像素偏移会在重排后落到别的段落上
    LaunchedEffect(settings.fontSize, settings.lineHeight) {
        val target = position.percent
        if (target <= 0) return@LaunchedEffect
        withFrameNanos { }
        val max = scrollState.maxValue
        if (max > 0) scrollState.scrollTo(readerScrollTarget(target, max))
    }

    // 阅读进度：滚动停止约 1s 保存一次百分比（杀进程也不丢），退出时兜底再保存
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect {
            position.percent = readerProgressPercent(scrollState.value, scrollState.maxValue)
        }
    }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .debounce(800)
            .collect {
                if (scrollState.maxValue > 0) {
                    currentOnProgressSave(progressToSave())
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (scrollState.maxValue > 0) {
                currentOnProgressSave(progressToSave())
            }
        }
    }

    // 阅读亮度：只改当前窗口，退出时还给系统
    LaunchedEffect(activity, settings.brightness) {
        activity?.window?.let { applyWindowBrightness(it, settings.brightness) }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let {
                applyWindowBrightness(it, NovelReaderSettings.BRIGHTNESS_SYSTEM)
            }
        }
    }
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // 阅读底色独立于 app 主题：状态栏图标按正文底取反，退出时恢复成 app 主题那一套
    // （app 主题可能在阅读中被系统切走，恢复值要读最新的那次）
    val appDark by rememberUpdatedState(LocalDarkTheme.current)
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = theme.light
                isAppearanceLightNavigationBars = theme.light
            }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !appDark
                    isAppearanceLightNavigationBars = !appDark
                }
            }
        }
    }

    // 沉浸模式：阅读时隐藏系统栏，从屏幕边缘滑入可临时唤出，退出阅读器时恢复
    DisposableEffect(activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val bg = theme.bg
    val fg = theme.fg
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportPx = it.height }
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    when {
                        // 面板开着：先收面板，控制栏留着
                        panelOpen -> {
                            panelOpen = false
                            refreshAutoHide()
                        }
                        controlsVisible -> {
                            controlsVisible = false
                            autoHideJob?.cancel()
                        }
                        else -> refreshAutoHide()
                    }
                })
            },
    ) {
        BackHandler(onBack = onClose)

        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = CONTENT_PADDING_HORIZONTAL)
                .padding(top = CONTENT_PADDING_TOP, bottom = CONTENT_PADDING_BOTTOM)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column {
                // 历史译文标注：正文模型已下线时展示的缓存译文须明示来源，避免误当现译
                if (novelStale && showTranslation) {
                    Text(
                        text = stringResource(R.string.detail_novel_translation_stale),
                        color = fg.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                // 内嵌图切块：pixiv 正文的 [nimg:URL] token 切成图块独立渲染；
                // 无 token 的正文（poipiku 全部、纯文字 pixiv 小说、AO3 每章）仍是单一文本块
                val blocks = remember(bodyText) { splitNovelBlocks(bodyText) }
                // 图块按视口窗口放行：整章图一次全请求 + 全解码会打满内存与主线程（卡顿根源）
                val imageTracker = remember(bodyText) { NovelImageTracker(blocks.count { it is NovelBlock.Image }) }
                LaunchedEffect(scrollState, imageTracker) {
                    imageTracker.pump(scrollState, viewport = { viewportPx })
                }
                // 章节模式：章名放在正文开头（页面顶部被浮动栏盖住，标题跟着正文走）
                if (chaptered && chapters[chapterIndex].title.isNotBlank()) {
                    Text(
                        text = chapters[chapterIndex].title,
                        color = fg,
                        fontSize = (settings.fontSize + 2).sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = ((settings.fontSize + 2) * settings.lineHeight).sp,
                        fontFamily = if (settings.serif) FontFamily.Serif else FontFamily.Default,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
                var imageIndex = 0
                blocks.forEach { block ->
                    when (block) {
                        is NovelBlock.Text -> Text(
                            text = remember(block.text, theme.link, context) {
                                linkifyNovel(block.text, theme.link, context, workClickHandler)
                            },
                            color = fg,
                            fontSize = settings.fontSize.sp,
                            lineHeight = (settings.fontSize * settings.lineHeight).sp,
                            fontFamily = if (settings.serif) FontFamily.Serif else FontFamily.Default,
                        )

                        is NovelBlock.Image -> {
                            // 放行状态在图块内部按 index 自读自写：谁的状态变了只有谁重组
                            NovelInlineImage(
                                url = block.url,
                                index = imageIndex++,
                                tracker = imageTracker,
                                placeholderColor = fg.copy(alpha = 0.05f),
                            )
                        }
                    }
                }
                // 章末落点：还有下一章就放「下一章」入口（AO3 网页同款动线），末章标注全文完
                if (chaptered) {
                    if (chapterIndex < chapters.lastIndex) {
                        Text(
                            text = stringResource(R.string.reader_chapter_next),
                            color = theme.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(top = 32.dp)
                                .clip(ReaderFooterShape)
                                .border(BorderStroke(0.5.dp, theme.accent.copy(alpha = 0.5f)), ReaderFooterShape)
                                .clickable { goToChapter(chapterIndex + 1) }
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.reader_chapter_end),
                            color = fg.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }
                }
            }
        }

        // 控制栏：底衬是与正文同色的渐变，正文滚到栏下淡出而不是从栏边漏出来
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarInset + CONTENT_PADDING_TOP)
                        .background(scrimTop(bg)),
                )
                ReaderTopBar(title = title, theme = theme, onClose = onClose)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(navBarInset + CONTENT_PADDING_BOTTOM + if (chaptered) CHAPTER_BAR_HEIGHT else 0.dp)
                        .background(scrimBottom(bg)),
                )
                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                    if (chaptered) {
                        NovelChapterBar(
                            theme = theme,
                            index = chapterIndex,
                            count = chapters.size,
                            chapterTitle = readerChapterLabel(chapters[chapterIndex].title),
                            onPrev = { goToChapter(chapterIndex - 1) },
                            onNext = { goToChapter(chapterIndex + 1) },
                            onOpenList = { chapterSheetOpen = true },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    NovelReaderControls(
                        settings = settings,
                        theme = theme,
                        scrollState = scrollState,
                        panelOpen = panelOpen,
                        onTogglePanel = { togglePanel() },
                        onSettingsChange = onSettingsChange,
                        onInteract = { refreshAutoHide() },
                        onSeekStart = { autoHideJob?.cancel() },
                        onSeekEnd = { refreshAutoHide() },
                        translationAvailable = translationAvailable,
                        translating = translating,
                        busy = busy,
                        showTranslation = showTranslation,
                        streamProgress = novelStreamProgress,
                        onToggleTranslation = onToggleTranslation,
                        cacheStatus = cacheStatus,
                    )
                }
            }
        }

        if (chapterSheetOpen && chaptered) {
            NovelChapterSheet(
                theme = theme,
                chapters = chapters,
                currentIndex = chapterIndex,
                onSelect = { goToChapter(it) },
                onDismiss = { chapterSheetOpen = false },
            )
        }
    }
}

/** 读者读到哪儿（百分比）。挂在 remember 上，重组不会把它清掉 */
private class ReaderPosition {
    var percent: Int = 0
}

/** 正文底色向透明收边：栏下正文淡出，栏外不留硬边 */
private fun scrimTop(bg: Color): Brush = Brush.verticalGradient(
    0f to bg,
    0.62f to bg,
    1f to bg.copy(alpha = 0f),
)

private fun scrimBottom(bg: Color): Brush = Brush.verticalGradient(
    0f to bg.copy(alpha = 0f),
    0.38f to bg,
    1f to bg,
)

/** 阅读亮度（0~1）；负值 = 跟随系统 */
internal fun applyWindowBrightness(window: Window, brightness: Float) {
    val attrs = window.attributes
    attrs.screenBrightness =
        if (brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else brightness
    window.attributes = attrs
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 阅读进度百分比（0~100；正文不足一屏无需滚动时视为已读完，显示 100） */
internal fun readerProgressPercent(value: Int, max: Int): Int {
    if (max <= 0) return 100
    return ((value.toFloat() / max) * 100).toInt().coerceIn(0, 100)
}

/** 百分比 → 滚动像素：进度按百分比落盘，字号/行距重排后靠它把读者送回原处 */
internal fun readerScrollTarget(percent: Int, max: Int): Int {
    if (max <= 0) return 0
    return (percent / 100f * max).toInt().coerceIn(0, max)
}


private class NovelImageTracker(imageCount: Int) {
    private val positions = FloatArray(imageCount) { Float.POSITIVE_INFINITY }
    private val activated = Array(imageCount) { mutableStateOf(false) }
    private val settled = BooleanArray(imageCount)
    private var inFlight by mutableIntStateOf(0)
    private var revision by mutableIntStateOf(0)

    fun mark(index: Int, contentY: Float) {
        if (positions[index] == contentY) return
        positions[index] = contentY
        revision++
    }

    fun isActivated(index: Int): Boolean = activated[index].value

    /** 图块加载收尾（成功/失败都算）：腾出在途名额，放行泵会被唤醒接着放下一张 */
    fun onSettled(index: Int) {
        if (settled[index]) return
        settled[index] = true
        inFlight--
    }

    /**
     * 帧驱动放行：每帧最多放行 [perFrame] 张（由近及远），且在途不超过 [IMAGE_MAX_IN_FLIGHT]。
     * 没得放行就挂起等状态变化（滚动 / 布局位移 / 有图收尾），不空转。
     */
    suspend fun pump(scrollState: ScrollState, viewport: () -> Int, perFrame: Int = 1) {
        while (true) {
            withFrameNanos { }
            if (inFlight >= IMAGE_MAX_IN_FLIGHT || !release(scrollState.value, viewport(), perFrame)) {
                awaitChange(scrollState, viewport)
            }
        }
    }

    /** 放行窗口内离视口中心最近的图，最多 [budget] 张；返回这一轮是否有图被放行 */
    private fun release(scrollPx: Int, viewportPx: Int, budget: Int): Boolean {
        if (viewportPx <= 0) return false
        // 视口上方留 1.5 屏、下方留 2.5 屏余量：图在读者到达前就已完成解码，滚动时不再等它
        val top = scrollPx - viewportPx * 1.5f
        val bottom = scrollPx + viewportPx * 2.5f
        val center = scrollPx + viewportPx / 2f
        var left = budget
        while (left > 0) {
            var next = -1
            var nearest = Float.MAX_VALUE
            positions.forEachIndexed { index, y ->
                if (activated[index].value || y !in top..bottom) return@forEachIndexed
                val distance = abs(y - center)
                if (distance < nearest) {
                    nearest = distance
                    next = index
                }
            }
            if (next < 0) break
            activated[next].value = true
            inFlight++
            left--
        }
        return left < budget
    }

    private suspend fun awaitChange(scrollState: ScrollState, viewport: () -> Int) {
        val key = listOf(scrollState.value, viewport(), revision, inFlight)
        snapshotFlow { listOf(scrollState.value, viewport(), revision, inFlight) }
            .first { it != key }
    }
}

/**
 * 正文内嵌图：先按估的比例占位（放行前是灰块，放行后原地填报），出真尺寸后按真比例定死。
 * 位置回调照常挂，放行泵靠它算窗口。
 */
@Composable
private fun NovelInlineImage(
    url: String,
    index: Int,
    tracker: NovelImageTracker,
    placeholderColor: Color,
) {
    val activated = tracker.isActivated(index)
    // 占位比例先按竖图估，出图后校正成真值。校正时图通常还是"下面一张"，读者看不到这次重排
    var aspect by remember(url) { mutableFloatStateOf(IMAGE_ASPECT_DEFAULT) }
    var failed by remember(url) { mutableStateOf(false) }
    val positioned = Modifier
        .onGloballyPositioned { coords ->
            // positionInParent 是滚动不变量（相对滚动内容），比 boundsInRoot 便宜且不随滚动抖动
            tracker.mark(index, coords.positionInParent().y)
        }
        .fillMaxWidth()
        .padding(vertical = 10.dp)
    // 高度由比例算定、不看位图固有尺寸：占位与出图占同一块地方，正文不会被顶动
    val sized = if (failed) positioned.height(IMAGE_FAILED_HEIGHT) else positioned.aspectRatio(aspect)
    if (!activated) {
        Box(modifier = sized.background(placeholderColor))
        return
    }
    val context = LocalContext.current
    AsyncImage(
        // 关掉淡入：大图淡入要在渲染线程逐帧混合整张位图，几张一起淡就是连掉帧。
        // 尺寸不写死，交给 Coil 按布局约束取（宽度铺满、高度保原图比例）
        model = remember(url, context) {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(false)
                .build()
        },
        contentDescription = null,
        placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) },
        error = remember(placeholderColor) { ColorPainter(placeholderColor) },
        onSuccess = { state ->
            val image = state.result.image
            if (image.width > 0 && image.height > 0) {
                aspect = image.width.toFloat() / image.height.toFloat()
            }
            tracker.onSettled(index)
        },
        onError = {
            failed = true
            tracker.onSettled(index)
        },
        modifier = sized,
    )
}

/** 小说正文链接化：poipiku 作品内链点击跳转详情，外链用系统浏览器打开 */
private fun linkifyNovel(
    raw: String,
    linkColor: Color,
    context: Context,
    onWorkClick: (Long, Long, String) -> Unit,
): AnnotatedString {
    val style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    return buildAnnotatedString {
        for (segment in LinkText.parse(raw)) {
            when (segment) {
                is LinkSegment.Plain -> append(segment.text)
                is LinkSegment.Link -> {
                    val workMatch = POIPIKU_WORK_REGEX.matchEntire(segment.url)
                    if (workMatch != null) {
                        val authorId = workMatch.groupValues[1].toLongOrNull()
                        val workId = workMatch.groupValues[2].toLongOrNull()
                        withLink(
                            LinkAnnotation.Clickable(tag = segment.url) {
                                if (authorId != null && workId != null) {
                                    onWorkClick(authorId, workId, "")
                                }
                            },
                        ) {
                            pushStyle(style)
                            append(segment.text)
                            pop()
                        }
                    } else {
                        withLink(
                            LinkAnnotation.Clickable(tag = segment.url) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(segment.url)),
                                )
                            },
                        ) {
                            pushStyle(style)
                            append(segment.text)
                            pop()
                        }
                    }
                }
            }
        }
    }
}
