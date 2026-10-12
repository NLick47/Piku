package com.piku.client.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.piku.client.data.local.MangaReaderSettings
import com.piku.client.ui.theme.ViewerBackgroundDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val AUTO_HIDE_DELAY_MS = 2600L

/** 未测到真实比例前的占位：竖漫常见宽高比 */
private const val DEFAULT_PAGE_ASPECT = 1f / 1.42f

/**
 * 漫画阅读器。翻页与滚动共用一套控制栏、设置与进度回调。
 * 亮度只改当前窗口、退出还给系统；常亮只在阅读期间生效。
 */
@Composable
fun MangaReader(
    images: List<ViewerImage>,
    /** 起始页（0 基），与 [ViewerImage] 同基准 */
    startPage: Int,
    title: String,
    settings: MangaReaderSettings,
    onSettingsChange: (MangaReaderSettings, Boolean) -> Unit,
    onClose: () -> Unit,
    /** 当前页变化（0 基） */
    onPageChanged: (Int) -> Unit,
    onLongPressImage: (Int) -> Unit,
    hasImageModel: Boolean,
    imageTranslating: Boolean,
    imageTranslated: Boolean,
    translatedImages: Map<Int, android.graphics.Bitmap>,
    onImageTranslateClick: (Int) -> Unit,
    hdPages: Set<Int> = emptySet(),
    onHdToggle: (Int) -> Unit = {},
    previews: Map<Int, Painter> = emptyMap(),
    /** 该形态的操作提示是否已看过；查的是落盘标记，只在打开与切形态时问一次 */
    hintSeen: (scrollMode: Boolean) -> Boolean = { true },
    /** 提示上屏前先落盘，避免提前退出导致反复弹 */
    markHintSeen: (scrollMode: Boolean) -> Unit = {},
    /** 调试入口：清掉标记让提示重新出现 */
    onReplayHints: () -> Unit = {},
) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var controlsVisible by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var overviewOpen by remember { mutableStateOf(false) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    // 1 基页码：界面口径，0 基转换只在这一层做
    var page by remember {
        mutableIntStateOf(startPage.coerceIn(0, images.lastIndex.coerceAtLeast(0)) + 1)
    }
    /**
     * 外部翻页请求（热区、音量键、进度条、总览），一次性、消费即清空。
     * 不能用「page 与 pager 位置是否相等」来驱动：onPageChanged 的回传滞后于 pager 的实际位置，
     * 快速滑动时拿着上上帧的页码去纠正，会把正在滑动的页面拽回去。
     */
    var jumpTarget by remember { mutableStateOf<Int?>(null) }
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)

    // 一次性手势提示：形态各看一次；nonce 只用于让提示重新计时
    var hintVisible by remember { mutableStateOf(false) }
    var hintNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.scrollMode) {
        if (!hintSeen(settings.scrollMode)) {
            markHintSeen(settings.scrollMode)
            hintVisible = true
            hintNonce++
        }
    }
    // 首次操作即退场：翻页、滚动都算（首帧的初始页码不算操作）
    LaunchedEffect(Unit) {
        snapshotFlow { page }.drop(1).collect { hintVisible = false }
    }

    fun refreshAutoHide() {
        controlsVisible = true
        autoHideJob?.cancel()
        if (panelOpen || overviewOpen) return
        autoHideJob = scope.launch {
            delay(AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    fun togglePanel() {
        panelOpen = !panelOpen
        hintVisible = false
        if (panelOpen) {
            autoHideJob?.cancel()
            controlsVisible = true
        } else {
            refreshAutoHide()
        }
    }

    fun openOverview(open: Boolean) {
        overviewOpen = open
        if (open) {
            hintVisible = false
            autoHideJob?.cancel()
            controlsVisible = true
        } else {
            refreshAutoHide()
        }
    }

    /** 调试重播：清标记、收起面板，让提示按"首次打开"的样子再来一遍 */
    fun replayHints() {
        onReplayHints()
        if (panelOpen) togglePanel()
        hintNonce++
        hintVisible = true
    }

    /** 外部翻页统一走这里：改页码 + 发一次性请求给当前形态的载体 */
    fun jumpTo(target: Int) {
        val clamped = target.coerceIn(1, maxOf(images.size, 1))
        page = clamped
        jumpTarget = clamped - 1
    }

    fun step(delta: Int) = jumpTo(page + delta)

    LaunchedEffect(Unit) { refreshAutoHide() }

    // 页码变了只回传进度，不碰控制栏：翻页与滚动每次都让菜单跳出来是纯干扰。
    // 菜单只该有两个来源——用户点出来的，或到点自动收起
    LaunchedEffect(page) {
        currentOnPageChanged(page - 1)
    }

    LaunchedEffect(activity, settings.brightness) {
        activity?.window?.let { applyWindowBrightness(it, settings.brightness) }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let {
                applyWindowBrightness(it, MangaReaderSettings.BRIGHTNESS_SYSTEM)
            }
        }
    }
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // 没拿到焦点的 Composable 收不到按键，音量键翻页要靠它；
    // 总览/面板的开合会把焦点带走，关闭后要重新要回来，否则音量键从此失灵
    LaunchedEffect(settings.volumeFlip, overviewOpen) {
        if (settings.volumeFlip && !overviewOpen) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    BackHandler {
        when {
            panelOpen -> togglePanel()
            overviewOpen -> openOverview(false)
            else -> onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewerBackgroundDark)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (!settings.volumeFlip) return@onKeyEvent false
                if (event.key != Key.VolumeDown && event.key != Key.VolumeUp) return@onKeyEvent false
                // 按下就得吃掉：只消费抬手的话，系统已经在按下时调了音量并弹了音量条；
                // 翻页动作仍留给抬手，按住连发不会连翻好几页
                if (event.type == KeyEventType.KeyUp) {
                    // 往下翻 = 前进，与滚动方向一致
                    if (event.key == Key.VolumeDown) step(1) else step(-1)
                }
                true
            },
    ) {
        val onTapCenter: () -> Unit = {
            hintVisible = false
            if (controlsVisible) {
                controlsVisible = false
                autoHideJob?.cancel()
            } else {
                refreshAutoHide()
            }
        }

        if (settings.scrollMode) {
            ScrollReader(
                images = images,
                initialPage = page - 1,
                jumpTarget = jumpTarget,
                onJumpConsumed = { jumpTarget = null },
                pageGap = settings.pageGap,
                hdPages = hdPages,
                previews = previews,
                imageTranslated = imageTranslated,
                translatedImages = translatedImages,
                onPageChanged = { page = it + 1 },
                onLongPressImage = onLongPressImage,
                onTapCenter = onTapCenter,
            )
        } else {
            PagedReader(
                images = images,
                initialPage = page - 1,
                jumpTarget = jumpTarget,
                onJumpConsumed = { jumpTarget = null },
                settings = settings,
                hdPages = hdPages,
                previews = previews,
                imageTranslated = imageTranslated,
                translatedImages = translatedImages,
                onPageChanged = { page = it + 1 },
                onLongPressImage = onLongPressImage,
                onStep = ::step,
                onTapCenter = onTapCenter,
            )
        }

        // 区域提示画在内容之上、控制栏之下：顶/底栏压着色带，边缘保持干净
        if (hintVisible) {
            key(hintNonce) {
                MangaZoneHint(
                    settings = settings,
                    onDismiss = { hintVisible = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 亮度只走窗口（applyWindowBrightness），不再叠一层遮罩：
        // 两者同时生效是相乘的关系，用户拉到 50% 实际会暗到三成左右
        AnimatedVisibility(
            visible = controlsVisible && !overviewOpen,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(400)),
        ) {
            MangaReaderTopBar(
                title = title,
                panelOpen = panelOpen,
                onClose = onClose,
                onOverview = { openOverview(true) },
                onTogglePanel = ::togglePanel,
                modifier = Modifier.blockInputsWhen(!controlsVisible),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        ) {
            AnimatedVisibility(
                visible = panelOpen && controlsVisible && !overviewOpen,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(120)),
            ) {
                MangaReaderSettingsPanel(
                    settings = settings,
                    onChange = onSettingsChange,
                    onReplayHint = ::replayHints,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                        .blockInputsWhen(!panelOpen || !controlsVisible),
                )
            }
            AnimatedVisibility(
                visible = controlsVisible && !overviewOpen,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(400)),
            ) {
                MangaReaderBottomBar(
                    page = page,
                    total = images.size,
                    scrollMode = settings.scrollMode,
                    hdAvailable = images.getOrNull(page - 1)?.hdUrl != null,
                    hdOn = (page - 1) in hdPages,
                    translateAvailable = hasImageModel,
                    translating = imageTranslating,
                    translated = imageTranslated,
                    onSeek = { jumpTo(it) },
                    onModeChange = { onSettingsChange(settings.copy(scrollMode = it), true) },
                    onHdToggle = { onHdToggle(page - 1) },
                    onTranslate = { onImageTranslateClick(page - 1) },
                    onInteract = ::refreshAutoHide,
                    modifier = Modifier.blockInputsWhen(!controlsVisible),
                )
            }
        }

        if (overviewOpen) {
            MangaOverviewPanel(
                images = images,
                currentPage = page,
                onPick = { picked ->
                    jumpTo(picked)
                    openOverview(false)
                },
                onClose = { openOverview(false) },
            )
        }
    }
}

/** 一屏一页：左右滑动翻页；方向只改 reverseLayout，页码语义不受影响 */
@Composable
private fun PagedReader(
    images: List<ViewerImage>,
    initialPage: Int,
    jumpTarget: Int?,
    onJumpConsumed: () -> Unit,
    settings: MangaReaderSettings,
    hdPages: Set<Int>,
    previews: Map<Int, Painter>,
    imageTranslated: Boolean,
    translatedImages: Map<Int, android.graphics.Bitmap>,
    onPageChanged: (Int) -> Unit,
    onLongPressImage: (Int) -> Unit,
    onStep: (Int) -> Unit,
    onTapCenter: () -> Unit,
) {
    val pagerState = rememberPagerState(
        pageCount = { images.size },
        initialPage = initialPage.coerceIn(0, images.lastIndex.coerceAtLeast(0)),
    )
    // 翻页预热只备垫底那一档（页表小图）：翻过去立刻有图，不再黑屏等网络。
    // 原图档不提前下——漫画几十页，提前下原图就是几十上百兆，仍按打开哪页取哪页
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    rememberPagerWindowPrefetch(
        urls = remember(images) { images.map { it.thumbnailUrl } },
        currentPage = pagerState.currentPage,
        // 页内容就是视口：按实测尺寸预热，翻页才命中内存里那一张
        pageSizePx = viewportSize.takeIf { it.width > 0 && it.height > 0 }
            ?.let { CoilSize(it.width, it.height) },
    )
    // 铺满宽时每页要能上下滚：比例也在这里测，容器高度正好等于图的高度
    val aspects = remember { mutableStateMapOf<Int, Float>() }
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)

    // 外部翻页走一次性请求，消费即清空；pager 自己滑出来的变化走 onPageChanged 回传，
    // 两条路不会互相纠正
    LaunchedEffect(jumpTarget) {
        jumpTarget?.let { target ->
            pagerState.scrollToPage(target.coerceIn(0, images.lastIndex.coerceAtLeast(0)))
            onJumpConsumed()
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(currentOnPageChanged)
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = settings.rightToLeft,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it },
        key = { it },
    ) { index ->
        val pageScroll = rememberScrollState()
        // 铺满宽时页内要能上下滚；整页模式不接管任何拖动，横滑翻页交给 pager
        val pageDragModifier = if (settings.fillWidth) {
            Modifier.verticalScroll(pageScroll)
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(pageDragModifier)
                .mangaTapZones(
                    onLeft = { onStep(if (settings.rightToLeft) 1 else -1) },
                    onRight = { onStep(if (settings.rightToLeft) -1 else 1) },
                ),
        ) {
            PageCell(
                image = images[index],
                pageIndex = index,
                imageTranslated = imageTranslated,
                translatedImages = translatedImages,
                hdUrl = images[index].hdUrl?.takeIf { index in hdPages },
                preview = previews[index],
                contentScale = if (settings.fillWidth) ContentScale.FillWidth else ContentScale.Fit,
                onLongPress = { onLongPressImage(index) },
                onTapCenter = onTapCenter,
                // 只有铺满宽要按比例定高；整页模式传 null，省掉一次必然的重组
                onIntrinsicAspect = if (settings.fillWidth) {
                    { aspects[index] = it }
                } else {
                    null
                },
                // 铺满宽：容器按图的比例定高（比屏幕长时由外层滚），整页：撑满一屏
                modifier = if (settings.fillWidth) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspects[index] ?: DEFAULT_PAGE_ASPECT)
                } else {
                    Modifier.fillMaxSize()
                },
            )
        }
    }
}

/** 视口里的一项（横向偏移 + 高度），给 [centerItemIndex] 算中心页用 */
internal data class VisibleSpan(val index: Int, val offset: Int, val size: Int)

/**
 * 视口中心落在哪一项上。
 * 滚动模式的"当前页"必须用它而不是第一个可见项：一屏能看到一页多时，
 * 滚到最底时第一个可见项是倒数第二页，页码、翻译、HD、进度会一起差最后一页。
 */
internal fun centerItemIndex(
    viewportStart: Int,
    viewportEnd: Int,
    visible: List<VisibleSpan>,
): Int? {
    val center = (viewportStart + viewportEnd) / 2
    return visible.firstOrNull { center >= it.offset && center < it.offset + it.size }?.index
}

/** 视口中心页；layoutInfo 是快照状态，在 snapshotFlow 里读能正确随滚动重算 */
private fun LazyListState.centerVisibleIndex(): Int {
    val info = layoutInfo
    // 已经滚到底就认最后一页：最后一页不满一屏时中心会落在倒数第二页上，
    // 页码、进度、翻译会一起差那一页，"读完了"这个状态也就永远记不下来。
    // 必须带上 canScrollBackward：内容不满一屏时两个方向都不能滚，
    // 否则"当前页"会被锁死在最后一页，第一页反而选不中
    if (!canScrollForward && canScrollBackward && info.totalItemsCount > 0) {
        return info.totalItemsCount - 1
    }
    val visible = info.visibleItemsInfo.map { VisibleSpan(it.index, it.offset, it.size) }
    return centerItemIndex(info.viewportStartOffset, info.viewportEndOffset, visible)
        ?: firstVisibleItemIndex
}

/** 竖向连续：长图与条漫的形态，页与页之间按设置留缝 */
@Composable
private fun ScrollReader(
    images: List<ViewerImage>,
    initialPage: Int,
    jumpTarget: Int?,
    onJumpConsumed: () -> Unit,
    pageGap: Boolean,
    hdPages: Set<Int>,
    previews: Map<Int, Painter>,
    imageTranslated: Boolean,
    translatedImages: Map<Int, android.graphics.Bitmap>,
    onPageChanged: (Int) -> Unit,
    onLongPressImage: (Int) -> Unit,
    onTapCenter: () -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, images.lastIndex.coerceAtLeast(0)),
    )
    // 每页真实宽高比：测到之前先按竖漫常见比例占位，落地时不会先跳一下
    val aspects = remember { mutableStateMapOf<Int, Float>() }
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)

    LaunchedEffect(jumpTarget) {
        jumpTarget?.let { target ->
            listState.scrollToItem(target.coerceIn(0, images.lastIndex.coerceAtLeast(0)), 0)
            onJumpConsumed()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.centerVisibleIndex() }
            .distinctUntilChanged()
            .collect(currentOnPageChanged)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (pageGap) 8.dp else 0.dp),
    ) {
        items(count = images.size, key = { it }) { index ->
            PageCell(
                image = images[index],
                pageIndex = index,
                imageTranslated = imageTranslated,
                translatedImages = translatedImages,
                hdUrl = images[index].hdUrl?.takeIf { index in hdPages },
                preview = previews[index],
                contentScale = ContentScale.Fit,
                onLongPress = { onLongPressImage(index) },
                onTapCenter = onTapCenter,
                onIntrinsicAspect = { aspects[index] = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspects[index] ?: DEFAULT_PAGE_ASPECT),
            )
        }
    }
}

/** 单页：翻译图优先，原图档叠其上，长按出图片菜单 */
@Composable
private fun PageCell(
    image: ViewerImage,
    pageIndex: Int,
    imageTranslated: Boolean,
    translatedImages: Map<Int, android.graphics.Bitmap>,
    hdUrl: String?,
    preview: Painter?,
    contentScale: ContentScale,
    onLongPress: () -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier,
    onIntrinsicAspect: ((Float) -> Unit)? = null,
) {
    val bitmap = if (imageTranslated) translatedImages[pageIndex] else null
    val onTap = { zone: Int -> if (zone == TAP_ZONE_CENTER) onTapCenter() }
    if (bitmap != null) {
        TranslatedZoomableImage(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = contentScale,
            onTap = onTap,
            onLongPress = onLongPress,
            modifier = modifier,
        )
    } else {
        ZoomableImage(
            image = image,
            contentDescription = null,
            contentScale = contentScale,
            hdUrl = hdUrl,
            preview = preview,
            onIntrinsicAspect = onIntrinsicAspect,
            onTap = onTap,
            onLongPress = onLongPress,
            modifier = modifier,
        )
    }
}

/**
 * 左右三分之一的点按：在 Initial 阶段观察事件、不消费，所以不必等双击超时就能翻页。
 * 中间那三分之一留给图片自己的手势（显隐 + 双击缩放）——它本来就要等双击窗口，
 * 三区都走那条路的话每次翻页都会慢半拍。
 *
 * 回调用 rememberUpdatedState 兜住、pointerInput 的 key 固定为 Unit：
 * 拿 lambda 当 key 的话，翻页/控制栏淡出这类重组会让手势循环整体重启，
 * 重启后 origin 重置，正在拖拽的那一次抬手会被误判成"快速点按"，一次滑动翻两页。
 */
@Composable
private fun Modifier.mangaTapZones(
    onLeft: () -> Unit,
    onRight: () -> Unit,
): Modifier {
    val left by rememberUpdatedState(onLeft)
    val right by rememberUpdatedState(onRight)
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val first = awaitPointerEvent(PointerEventPass.Initial)
                val down = first.changes.firstOrNull() ?: continue
                if (!down.pressed) continue
                val origin = down.position
                val startedAt = System.currentTimeMillis()
                var moved = false
                var multiTouch = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) multiTouch = true
                    if (event.changes.any { (it.position - origin).getDistance() > viewConfiguration.touchSlop }) {
                        moved = true
                    }
                    if (event.changes.none { it.pressed }) {
                        val up = event.changes.first()
                        val isQuickTap = !moved && !multiTouch &&
                            System.currentTimeMillis() - startedAt < 250 &&
                            (up.position - origin).getDistance() <= viewConfiguration.touchSlop
                        if (isQuickTap) {
                            when {
                                up.position.x < size.width / TAP_ZONE_DIVISOR -> left()
                                up.position.x > size.width * 2f / TAP_ZONE_DIVISOR -> right()
                            }
                        }
                        break
                    }
                }
            }
        }
    }
}

/**
 * 淡出中的控制栏还挂在组合树上、照样能点：不吃掉事件的话，点左上角会同时翻页并退出阅读器。
 * 在 Initial 阶段消费所有事件，子元素的按钮和下方的图片手势都收不到——这一层暂时等于不存在。
 */
private fun Modifier.blockInputsWhen(blocked: Boolean): Modifier =
    if (!blocked) {
        this
    } else {
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    }

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
