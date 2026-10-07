package com.piku.client.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.common.LinkSegment
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.common.LinkText
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.repository.NovelBlock
import com.piku.client.data.repository.splitNovelBlocks
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.ControlAccentLight
import com.piku.client.ui.theme.ViewerBackgroundDark
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

/** 阅读器浅色（米色纸）配色：不跟随系统主题，独立切换 */
internal val NovelReaderBgLight = Color(0xFFF3EEDA)
internal val NovelReaderTextLight = Color(0xFF2E2A23)
/** 阅读器深色配色 */
internal val NovelReaderBgDark = ViewerBackgroundDark
internal val NovelReaderTextDark = Color(0xFFD6D0C4)

/** 亮色底部栏底色：比正文更实、更白的暖白，与浅米正文拉开层次 */
internal val NovelReaderControlBgLight = Color(0xFFFAF5EC)
/** 暗色底部栏底色：正文底色加一层透明度 */
internal val NovelReaderControlBgDark = Color(0xCC141312)
/** 亮色底部栏顶部分隔线 */
internal val NovelReaderControlDividerLight = Color(0xFFE7E0D3)
/** 亮色进度条强调色：醒目暖棕（独立于正文链接色 linkColor） */
internal val NovelReaderProgressAccentLight = Color(0xFFB08A52)
/** 亮色进度条轨道色 */
internal val NovelReaderProgressTrackLight = Color(0xFFE6DFD2)

/**
 * 全屏小说阅读器：
 * - 独立配色（浅米底深字 / 深底浅字），与系统主题无关，由用户显式切换并持久化
 * - 字号 A−/A+ 调节（[NOVEL_FONT_MIN]~[NOVEL_FONT_MAX]），持久化
 * - 长按文本可选中复制（SelectionContainer）
 * - 点击文本区域切换顶部/底部控制栏显隐（自动隐藏）
 */
@Composable
fun FullNovelViewer(
    text: String,
    title: String,
    fontSize: Float,
    light: Boolean,
    initialPercent: Int,
    onProgressSave: (Int) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLightChange: (Boolean) -> Unit,
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
    /** 小说分块流式翻译进度（百分比）；非 null 时 chip 显示"翻译中 N%"且保持可点（点按仅翻面） */
    novelStreamProgress: Int? = null,
    onToggleTranslation: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentOnWorkClick by rememberUpdatedState(onWorkClick)
    val currentOnProgressSave by rememberUpdatedState(onProgressSave)
    var controlsVisible by remember { mutableStateOf(true) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    // 视口高度（px）：图块放行窗口要用
    var viewportPx by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val scrollState = remember { ScrollState(0) }

    fun refreshAutoHide() {
        controlsVisible = true
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) { refreshAutoHide() }

    // 恢复进度：内容可滚动后按保存的百分比跳转。百分比与字号/内容长度无关，
    // 字号调整后也不会错位；maxValue 为 0（不足一屏）则保持顶部。
    LaunchedEffect(scrollState, initialPercent) {
        if (initialPercent <= 0) return@LaunchedEffect
        val max = snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .first()
        scrollState.scrollTo((initialPercent / 100f * max).toInt().coerceIn(0, max))
    }

    // 阅读进度：滚动停止约 1s 保存一次百分比（杀进程也不丢），退出时兜底再保存
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .debounce(800)
            .collect {
                if (scrollState.maxValue > 0) currentOnProgressSave(progressPercent(scrollState))
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (scrollState.maxValue > 0) currentOnProgressSave(progressPercent(scrollState))
        }
    }

    val bg = if (light) NovelReaderBgLight else NovelReaderBgDark
    val fg = if (light) NovelReaderTextLight else NovelReaderTextDark
    val linkColor = if (light) ControlAccentLight else ControlAccentDark
    val controlBg = if (light) NovelReaderControlBgLight else NovelReaderControlBgDark
    val progressAccent = if (light) NovelReaderProgressAccentLight else linkColor
    val progressTrack = if (light) NovelReaderProgressTrackLight else fg.copy(alpha = 0.25f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportPx = it.height }
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (controlsVisible) {
                        controlsVisible = false
                        autoHideJob?.cancel()
                    } else {
                        refreshAutoHide()
                    }
                })
            },
    ) {
        BackHandler(onBack = onClose)

        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 88.dp),
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
                // 无 token 的正文（poipiku 全部、纯文字 pixiv 小说）仍是单一文本块，渲染与从前一致
                val blocks = remember(text) { splitNovelBlocks(text) }
                // 图块按视口窗口放行：整章图一次全请求 + 全解码会打满内存与主线程（卡顿根源）
                val imageTracker = remember(text) { NovelImageTracker(blocks.count { it is NovelBlock.Image }) }
                LaunchedEffect(scrollState, imageTracker) {
                    imageTracker.pump(scrollState, viewport = { viewportPx })
                }
                var imageIndex = 0
                blocks.forEach { block ->
                    when (block) {
                        is NovelBlock.Text -> Text(
                            text = remember(block.text, linkColor, context, currentOnWorkClick) {
                                linkifyNovel(block.text, linkColor, context, currentOnWorkClick)
                            },
                            color = fg,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.7f).sp,
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
            }
        }

        // 顶部栏：返回 + 标题
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(controlBg)
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PikuBackButton(
                    onClick = onClose,
                    dark = !light,
                    contentDescription = stringResource(R.string.detail_fullscreen_close),
                    tint = fg,
                )
                Text(
                    text = title,
                    color = fg,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 底部设置栏：进度条 | 原/译切换 · 字号 A− 状态 A+（居中成组） · 配色切换
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(controlBg),
            ) {
                if (light) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(NovelReaderControlDividerLight),
                    )
                }
                ReaderProgressBar(
                    scrollState = scrollState,
                    accent = progressAccent,
                    track = progressTrack,
                    onDragStart = { autoHideJob?.cancel() },
                    onDragEnd = { refreshAutoHide() },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderTranslateChip(
                        translationAvailable = translationAvailable,
                        translating = translating,
                        busy = busy,
                        showTranslation = showTranslation,
                        streamProgress = novelStreamProgress,
                        fg = fg,
                        accent = linkColor,
                        onClick = onToggleTranslation,
                    )
                    Box(Modifier.weight(1f))
                    ReaderFontButton(
                        label = "A−",
                        enabled = fontSize > NOVEL_FONT_MIN,
                        onClick = { onFontSizeChange(fontSize - 1f) },
                        fg = fg,
                    )
                    Text(
                        // 流式期间中间信息位临时切换为翻译进度（宽度与原状态相当，不挤压布局）；
                        // 钳到 99 避免"翻译中 100%"闪现，终态由 Completed 事件收尾
                        text = if (novelStreamProgress != null) {
                            stringResource(
                                R.string.detail_translating_progress,
                                minOf(novelStreamProgress, 99),
                            )
                        } else {
                            stringResource(
                                R.string.detail_novel_status,
                                fontSize.toInt(),
                                progressPercent(scrollState),
                            )
                        },
                        color = fg.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    ReaderFontButton(
                        label = "A+",
                        enabled = fontSize < NOVEL_FONT_MAX,
                        onClick = { onFontSizeChange(fontSize + 1f) },
                        fg = fg,
                    )
                    Box(Modifier.weight(1f))
                    IconButton(onClick = { onLightChange(!light) }) {
                        Icon(
                            imageVector = if (light) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = stringResource(
                                if (light) R.string.detail_novel_theme_dark
                                else R.string.detail_novel_theme_light,
                            ),
                            tint = fg,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 阅读进度百分比（0~100；正文不足一屏无需滚动时视为已读完，显示 100） */
private fun progressPercent(scrollState: ScrollState): Int {
    val max = scrollState.maxValue
    if (max <= 0) return 100
    return ((scrollState.value.toFloat() / max) * 100).toInt().coerceIn(0, 100)
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
                aspect = image.width.toFloat() / image.height
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

/**
 * 阅读进度条：点击或拖动快速定位，正文不足一屏时隐藏。
 * 按下时回调 [onDragStart]（暂停控制栏自动隐藏），松手时回调 [onDragEnd]（重新计时），
 * 避免拖动途中控制栏（连同进度条）自己消失。
 */
@Composable
private fun ReaderProgressBar(
    scrollState: ScrollState,
    accent: Color,
    track: Color,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
) {
    val max = scrollState.maxValue
    if (max <= 0) return
    val scope = rememberCoroutineScope()
    val progress = (scrollState.value.toFloat() / max).coerceIn(0f, 1f)

    fun seek(ratio: Float) {
        scope.launch { scrollState.scrollTo((ratio * max).toInt().coerceIn(0, max)) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(max) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onDragStart()
                    seek(down.position.x / size.width.toFloat())
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            onDragEnd()
                            break
                        }
                        if (change.position != change.previousPosition) {
                            seek(change.position.x / size.width.toFloat())
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(track),
        )
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent),
        )
    }
}

@Composable
private fun ReaderFontButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    fg: Color,
) {
    val alpha = if (enabled) 1f else 0.35f
    Text(
        text = label,
        color = fg.copy(alpha = alpha),
        fontSize = 16.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 底部栏译/原切换chip：无正文不渲染；非流式的在途翻译禁点防重复扣额度；
 *  流式期间保持"原/译"短标签（进度在中间信息位展示），点按仅切换原/译展示 */
@Composable
private fun ReaderTranslateChip(
    translationAvailable: Boolean,
    translating: Boolean,
    busy: Boolean,
    showTranslation: Boolean,
    streamProgress: Int? = null,
    fg: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    if (!translationAvailable) return
    Text(
        // 流式期间进度显示在中间信息位，这里保持"原/译"短标签只表模式与点击去向，
        // 避免长文案挤压底栏布局
        text = when {
            translating && !showTranslation -> stringResource(R.string.detail_translating)
            showTranslation -> stringResource(R.string.detail_chip_original)
            else -> stringResource(R.string.detail_chip_translate)
        },
        color = if (showTranslation) accent else fg.copy(alpha = 0.7f),
        fontSize = 14.sp,
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (showTranslation) accent.copy(alpha = 0.15f)
                else Color.Transparent,
            )
            // 流式期间切换显示永远可用（无副作用）；仅非流式的在途翻译才禁点防重复扣额度
            .clickable(enabled = !busy || streamProgress != null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
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