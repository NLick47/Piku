package com.piku.client.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.data.local.NovelReaderSettings
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.theme.NovelReaderTheme
import com.piku.client.ui.theme.ShadowAmbient
import com.piku.client.ui.theme.ShadowSpot
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ReaderCardShape = RoundedCornerShape(22.dp)
private val ReaderPillShape = RoundedCornerShape(18.dp)
private val ReaderChipShape = RoundedCornerShape(999.dp)

/** 设置面板里标签列的宽度：英文 Brightness 也要放得下 */
private val SettingLabelWidth = 68.dp
private val SettingValueWidth = 40.dp

/** 浮动控件的公共外观：投影 + 半透明底 + 细描边（与详情页浮动栏同一套做法） */
private fun Modifier.readerCard(theme: NovelReaderTheme, shape: Shape = ReaderCardShape): Modifier =
    this
        .shadow(10.dp, shape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
        .clip(shape)
        .background(theme.card)
        .border(BorderStroke(0.5.dp, theme.border), shape)

/** 卡片自己吃掉点击：否则点在面板空白处会穿透到"点正文收起控制栏" */
private fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** 顶栏：返回 + 标题，浮动胶囊 */
@Composable
internal fun ReaderTopBar(
    title: String,
    theme: NovelReaderTheme,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .readerCard(theme, ReaderPillShape)
            .padding(start = 2.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PikuBackButton(
            onClick = onClose,
            dark = !theme.light,
            contentDescription = stringResource(R.string.detail_fullscreen_close),
            tint = theme.fg,
        )
        Text(
            text = title,
            color = theme.fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 底部控件：进度行 + 操作行，设置面板从卡片上方展开。
 * [onSettingsChange] 的第二参为是否落盘：拖动过程只更新内存（跟着手指实时预览），松手才写一次盘。
 */
@Composable
internal fun NovelReaderControls(
    settings: NovelReaderSettings,
    theme: NovelReaderTheme,
    scrollState: ScrollState,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
    onSettingsChange: (NovelReaderSettings, Boolean) -> Unit,
    onInteract: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    translationAvailable: Boolean,
    translating: Boolean,
    busy: Boolean,
    showTranslation: Boolean,
    streamProgress: Int?,
    onToggleTranslation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp),
    ) {
        AnimatedVisibility(
            visible = panelOpen,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(120)),
        ) {
            ReaderSettingsPanel(
                settings = settings,
                theme = theme,
                onSettingsChange = onSettingsChange,
                onInteract = onInteract,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().readerCard(theme).consumeTaps()) {
            ReaderProgressRow(
                scrollState = scrollState,
                theme = theme,
                streamProgress = streamProgress,
                onSeekStart = onSeekStart,
                onSeekEnd = onSeekEnd,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderTranslateChip(
                    translationAvailable = translationAvailable,
                    translating = translating,
                    busy = busy,
                    showTranslation = showTranslation,
                    streamProgress = streamProgress,
                    theme = theme,
                    onClick = onToggleTranslation,
                )
                Spacer(Modifier.weight(1f))
                ReaderFontButton(
                    label = "A−",
                    enabled = settings.fontSize > NOVEL_FONT_MIN,
                    theme = theme,
                    onClick = {
                        onSettingsChange(
                            settings.copy(fontSize = settings.fontSize - 1f),
                            true,
                        )
                        onInteract()
                    },
                )
                Text(
                    text = settings.fontSize.roundToInt().toString(),
                    color = theme.fg.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(22.dp),
                )
                ReaderFontButton(
                    label = "A+",
                    enabled = settings.fontSize < NOVEL_FONT_MAX,
                    theme = theme,
                    onClick = {
                        onSettingsChange(
                            settings.copy(fontSize = settings.fontSize + 1f),
                            true,
                        )
                        onInteract()
                    },
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        onSettingsChange(settings.copy(themeId = theme.toggleFamily().id), true)
                        onInteract()
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (theme.light) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = stringResource(
                            if (theme.light) R.string.detail_novel_theme_dark
                            else R.string.detail_novel_theme_light,
                        ),
                        tint = theme.fg,
                        modifier = Modifier.size(20.dp),
                    )
                }
                ReaderGlyphButton(
                    label = "Aa",
                    contentDescription = stringResource(R.string.detail_novel_settings),
                    selected = panelOpen,
                    theme = theme,
                    onClick = {
                        onTogglePanel()
                        onInteract()
                    },
                )
            }
        }
    }
}

/** 进度行：可拖的滑块 + 已读百分比；流式翻译期间这一格改为翻译进度（同旧版中间信息位的位置语义） */
@Composable
private fun ReaderProgressRow(
    scrollState: ScrollState,
    theme: NovelReaderTheme,
    streamProgress: Int?,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
) {
    val max = scrollState.maxValue
    // 滑块跟手用连续比例；读数用落盘/还原的同一口径，避免"显示 51%、存的是 50%"
    val progress = if (max <= 0) 1f else (scrollState.value.toFloat() / max).coerceIn(0f, 1f)
    val percent = readerProgressPercent(scrollState.value, max)
    val scope = rememberCoroutineScope()
    fun seek(fraction: Float) {
        if (max <= 0) return
        scope.launch { scrollState.scrollTo((fraction * max).roundToInt().coerceIn(0, max)) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 14.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderSlider(
            fraction = progress,
            onValueChange = ::seek,
            onValueChangeFinished = ::seek,
            active = theme.accent,
            trackColor = theme.track,
            onDragStart = onSeekStart,
            onDragEnd = onSeekEnd,
            modifier = Modifier.weight(1f).height(24.dp),
        )
        Text(
            // 钳到 99 避免"翻译 100%"闪现，终态由 Completed 事件收尾
            text = if (streamProgress != null) {
                stringResource(R.string.detail_novel_translating_percent, minOf(streamProgress, 99))
            } else {
                "$percent%"
            },
            color = if (streamProgress != null) theme.accent else theme.fg.copy(alpha = 0.7f),
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp).padding(start = 8.dp),
        )
    }
}

/** 阅读设置面板：字号 / 行距 / 亮度 / 字体与常亮 / 背景 */
@Composable
private fun ReaderSettingsPanel(
    settings: NovelReaderSettings,
    theme: NovelReaderTheme,
    onSettingsChange: (NovelReaderSettings, Boolean) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .readerCard(theme)
            .consumeTaps()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        ReaderSettingSlider(
            label = stringResource(R.string.detail_novel_font_size),
            valueText = settings.fontSize.roundToInt().toString(),
            fraction = readerFraction(settings.fontSize, NOVEL_FONT_MIN, NOVEL_FONT_MAX),
            theme = theme,
            onValueChange = { fraction ->
                onSettingsChange(
                    settings.copy(
                        fontSize = readerStep(fraction, NOVEL_FONT_MIN, NOVEL_FONT_MAX, 1f),
                    ),
                    false,
                )
            },
            onValueChangeFinished = { fraction ->
                onSettingsChange(
                    settings.copy(
                        fontSize = readerStep(fraction, NOVEL_FONT_MIN, NOVEL_FONT_MAX, 1f),
                    ),
                    true,
                )
                onInteract()
            },
        )
        ReaderSettingSlider(
            label = stringResource(R.string.detail_novel_line_spacing),
            valueText = lineHeightText(settings.lineHeight),
            fraction = readerFraction(settings.lineHeight, NOVEL_LINE_HEIGHT_MIN, NOVEL_LINE_HEIGHT_MAX),
            theme = theme,
            onValueChange = { fraction ->
                onSettingsChange(
                    settings.copy(
                        lineHeight = readerStep(
                            fraction,
                            NOVEL_LINE_HEIGHT_MIN,
                            NOVEL_LINE_HEIGHT_MAX,
                            0.1f,
                        ),
                    ),
                    false,
                )
            },
            onValueChangeFinished = { fraction ->
                onSettingsChange(
                    settings.copy(
                        lineHeight = readerStep(
                            fraction,
                            NOVEL_LINE_HEIGHT_MIN,
                            NOVEL_LINE_HEIGHT_MAX,
                            0.1f,
                        ),
                    ),
                    true,
                )
                onInteract()
            },
        )
        val followSystem = settings.brightness < 0f
        ReaderSettingSlider(
            label = stringResource(R.string.detail_novel_brightness),
            valueText = if (followSystem) "" else "${(settings.brightness * 100).roundToInt()}%",
            fraction = if (followSystem) 1f else settings.brightness,
            theme = theme,
            onValueChange = { fraction ->
                onSettingsChange(
                    settings.copy(
                        brightness = readerStep(fraction, NOVEL_BRIGHTNESS_MIN, 1f, 0.05f),
                    ),
                    false,
                )
            },
            onValueChangeFinished = { fraction ->
                onSettingsChange(
                    settings.copy(
                        brightness = readerStep(fraction, NOVEL_BRIGHTNESS_MIN, 1f, 0.05f),
                    ),
                    true,
                )
                onInteract()
            },
            trailing = {
                ReaderChoiceChip(
                    text = stringResource(R.string.detail_novel_brightness_auto),
                    selected = followSystem,
                    theme = theme,
                    onClick = {
                        onSettingsChange(
                            settings.copy(brightness = NovelReaderSettings.BRIGHTNESS_SYSTEM),
                            true,
                        )
                        onInteract()
                    },
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_novel_font),
                color = theme.fg.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.width(SettingLabelWidth),
            )
            ReaderChoiceChip(
                text = stringResource(R.string.detail_novel_font_sans),
                selected = !settings.serif,
                theme = theme,
                onClick = {
                    onSettingsChange(settings.copy(serif = false), true)
                    onInteract()
                },
            )
            Spacer(Modifier.width(6.dp))
            ReaderChoiceChip(
                text = stringResource(R.string.detail_novel_font_serif),
                selected = settings.serif,
                theme = theme,
                onClick = {
                    onSettingsChange(settings.copy(serif = true), true)
                    onInteract()
                },
            )
            Spacer(Modifier.weight(1f))
            ReaderChoiceChip(
                text = stringResource(R.string.detail_novel_keep_screen_on),
                selected = settings.keepScreenOn,
                theme = theme,
                onClick = {
                    onSettingsChange(settings.copy(keepScreenOn = !settings.keepScreenOn), true)
                    onInteract()
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_novel_background),
                color = theme.fg.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.width(SettingLabelWidth),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NovelReaderTheme.entries.forEach { entry ->
                    ReaderThemeDot(
                        entry = entry,
                        current = theme,
                        onClick = {
                            onSettingsChange(settings.copy(themeId = entry.id), true)
                            onInteract()
                        },
                    )
                }
            }
        }
    }
}

/** 带标签与数值的一行滑块 */
@Composable
private fun ReaderSettingSlider(
    label: String,
    valueText: String,
    fraction: Float,
    theme: NovelReaderTheme,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = theme.fg.copy(alpha = 0.65f),
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(SettingLabelWidth),
        )
        ReaderSlider(
            fraction = fraction,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            active = theme.accent,
            trackColor = theme.track,
            modifier = Modifier.weight(1f).height(24.dp),
        )
        Text(
            text = valueText,
            color = theme.fg.copy(alpha = 0.65f),
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(SettingValueWidth),
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

/**
 * 通用滑块：拖动实时回调，松手回调终值。
 * 视觉只有 4dp 轨道 + 圆形滑块，配色由每档阅读主题给，与正文底同族。
 */
@Composable
private fun ReaderSlider(
    fraction: Float,
    onValueChange: (Float) -> Unit,
    active: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (Float) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // pointerInput 的 key 是 Unit，块里必须读"最新"的回调：否则一次拖动会一直用首次组合捕获的设置
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val thumbSize by animateDpAsState(
        targetValue = if (dragging) 18.dp else 14.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "readerSliderThumb",
    )
    val value = fraction.coerceIn(0f, 1f)

    fun ratio(x: Float, width: Int): Float =
        if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target ->
                    currentOnValueChange(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    currentOnDragStart()
                    currentOnValueChange(ratio(down.position.x, size.width))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            dragging = false
                            if (change != null) {
                                currentOnValueChangeFinished(ratio(change.position.x, size.width))
                            }
                            currentOnDragEnd()
                            break
                        }
                        if (change.position != change.previousPosition) {
                            currentOnValueChange(ratio(change.position.x, size.width))
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackShape = RoundedCornerShape(2.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(trackShape)
                .background(trackColor),
        )
        Box(
            Modifier
                .fillMaxWidth(value)
                .height(4.dp)
                .clip(trackShape)
                .background(active),
        )
        val thumbPx = with(density) { thumbSize.toPx() }
        val travelPx = (constraints.maxWidth - thumbPx).coerceAtLeast(0f)
        Box(
            Modifier
                .offset { IntOffset((value * travelPx).roundToInt(), 0) }
                .size(thumbSize)
                .shadow(3.dp, CircleShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                .clip(CircleShape)
                .background(active),
        )
    }
}

/** 胶囊选项（字体、常亮、跟随系统）：选中态是强调色淡底 */
@Composable
private fun ReaderChoiceChip(
    text: String,
    selected: Boolean,
    theme: NovelReaderTheme,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) theme.accent else theme.fg.copy(alpha = 0.7f),
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(ReaderChipShape)
            .background(if (selected) theme.accent.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 背景色点：选中档位描一圈强调色 */
@Composable
private fun ReaderThemeDot(
    entry: NovelReaderTheme,
    current: NovelReaderTheme,
    onClick: () -> Unit,
) {
    val selected = entry.id == current.id
    val label = stringResource(entry.labelRes)
    val dotSize by animateDpAsState(
        targetValue = if (selected) 26.dp else 22.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "readerThemeDot",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) current.accent else current.border,
        animationSpec = tween(160),
        label = "readerThemeDotRing",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(entry.bg)
                .border(BorderStroke(if (selected) 2.dp else 0.5.dp, ring), CircleShape),
        )
    }
}

/** 字号档位按钮（A− / A+）：命中区按 Material 最小触控尺寸 */
@Composable
private fun ReaderFontButton(
    label: String,
    enabled: Boolean,
    theme: NovelReaderTheme,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Text(
        text = label,
        color = theme.fg.copy(alpha = alpha),
        fontSize = 16.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 文字按钮（Aa）：选中时同"译"胶囊一样给个强调色淡底 */
@Composable
private fun ReaderGlyphButton(
    label: String,
    contentDescription: String,
    selected: Boolean,
    theme: NovelReaderTheme,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) theme.accent else theme.fg.copy(alpha = 0.75f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(ReaderChipShape)
            .background(if (selected) theme.accent.copy(alpha = 0.15f) else Color.Transparent)
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 底部栏译/原切换chip：无正文不渲染；非流式的在途翻译禁点防重复扣额度；
 *  流式期间保持"原/译"短标签（翻译进度在进度行展示），点按仅切换原/译展示 */
@Composable
private fun ReaderTranslateChip(
    translationAvailable: Boolean,
    translating: Boolean,
    busy: Boolean,
    showTranslation: Boolean,
    streamProgress: Int?,
    theme: NovelReaderTheme,
    onClick: () -> Unit,
) {
    if (!translationAvailable) return
    Text(
        // 流式期间进度显示在进度行，这里保持"原/译"短标签只表模式与点击去向
        text = when {
            translating && !showTranslation -> stringResource(R.string.detail_translating)
            showTranslation -> stringResource(R.string.detail_chip_original)
            else -> stringResource(R.string.detail_chip_translate)
        },
        color = if (showTranslation) theme.link else theme.fg.copy(alpha = 0.7f),
        fontSize = 14.sp,
        modifier = Modifier
            .clip(ReaderChipShape)
            .background(
                if (showTranslation) theme.link.copy(alpha = 0.15f)
                else Color.Transparent,
            )
            // 流式期间切换显示永远可用（无副作用）；仅非流式的在途翻译才禁点防重复扣额度
            .clickable(enabled = !busy || streamProgress != null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 设置值 → 滑块 0~1（上下限相同时固定在起点，避免除零） */
internal fun readerFraction(value: Float, min: Float, max: Float): Float =
    if (max <= min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)

/** 滑块 0~1 → 设置值：按 [step] 取整档，避免字号出现 16.37 这种小数 */
internal fun readerStep(fraction: Float, min: Float, max: Float, step: Float): Float {
    if (max <= min || step <= 0f) return min
    val count = ((max - min) / step).roundToInt().coerceAtLeast(1)
    val index = (fraction.coerceIn(0f, 1f) * count).roundToInt()
    return min + (max - min) * index / count
}

/** 行距读数固定一位小数，不走系统小数分隔符 */
private fun lineHeightText(lineHeight: Float): String {
    val tenths = (lineHeight * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
