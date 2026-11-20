package com.piku.client.core.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.piku.client.core.model.NovelChapter
import com.piku.client.core.storage.prefs.NovelReaderSettings
import com.piku.client.core.ui.common.PikuBackButton
import com.piku.client.core.ui.theme.NovelReaderTheme
import com.piku.client.core.ui.theme.ShadowAmbient
import com.piku.client.core.ui.theme.ShadowSpot
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ReaderChipShape = RoundedCornerShape(999.dp)
private val ReaderSheetShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
private val ReaderHintShape = RoundedCornerShape(10.dp)

private val SettingLabelWidth = 68.dp
private val SettingValueWidth = 40.dp

private fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

private fun Modifier.readerEdge(theme: NovelReaderTheme, top: Boolean): Modifier = drawBehind {
    val stroke = 0.5.dp.toPx()
    val y = if (top) stroke / 2f else size.height - stroke / 2f
    drawLine(theme.border, Offset(0f, y), Offset(size.width, y), stroke)
}

@Composable
internal fun ReaderTopBar(
    title: String,
    theme: NovelReaderTheme,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.card.copy(alpha = 1f))
            .readerEdge(theme, top = false)
            .consumeTaps()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .height(46.dp),
    ) {
        PikuBackButton(
            onClick = onClose,
            dark = !theme.light,
            modifier = Modifier.align(Alignment.CenterStart),
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
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 52.dp),
        )
    }
}

@Composable
internal fun NovelReaderControls(
    settings: NovelReaderSettings,
    theme: NovelReaderTheme,
    scrollState: ScrollState,
    chaptered: Boolean,
    chapterIndex: Int,
    chapterCount: Int,
    chapterTitle: String,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenChapterList: () -> Unit,
    onTogglePanel: () -> Unit,
    onSettingsChange: (NovelReaderSettings, Boolean) -> Unit,
    onInteract: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    translationAvailable: Boolean,
    translating: Boolean,
    showTranslation: Boolean,
    streamProgress: Int?,
    onToggleTranslation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.card.copy(alpha = 1f))
            .readerEdge(theme, top = true)
            .consumeTaps()
            .navigationBarsPadding(),
    ) {
        ReaderProgressRow(
            scrollState = scrollState,
            theme = theme,
            streamProgress = streamProgress,
            onSeekStart = onSeekStart,
            onSeekEnd = onSeekEnd,
        )
        if (chaptered) {
            ChapterNavRow(
                theme = theme,
                index = chapterIndex,
                count = chapterCount,
                chapterTitle = chapterTitle,
                onPrev = onPrevChapter,
                onNext = onNextChapter,
                onOpenList = onOpenChapterList,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (translationAvailable) {
                ReaderToolButton(
                    glyph = if (showTranslation) stringResource(R.string.detail_chip_original) else stringResource(R.string.detail_chip_translate),
                    label = when {
                        translating -> stringResource(R.string.novel_tool_translating)
                        showTranslation -> stringResource(R.string.novel_tool_translated)
                        else -> stringResource(R.string.novel_tool_original)
                    },
                    state = if (translating) ReaderToolState.BUSY else ReaderToolState.ON,
                    theme = theme,
                    onClick = {
                        onToggleTranslation()
                        onInteract()
                    },
                )
            }
            ReaderToolButton(
                icon = if (theme.light) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                label = if (theme.light) stringResource(R.string.novel_tool_night) else stringResource(R.string.novel_tool_day),
                theme = theme,
                onClick = {
                    onSettingsChange(settings.copy(themeId = theme.toggleFamily().id), true)
                    onInteract()
                },
            )
            ReaderToolButton(
                icon = Icons.Outlined.Tune,
                label = stringResource(R.string.manga_tools_settings),
                theme = theme,
                onClick = {
                    onTogglePanel()
                    onInteract()
                },
            )
        }
    }
}

private enum class ReaderToolState { OFF, ON, BUSY }

@Composable
private fun ReaderToolButton(
    label: String,
    theme: NovelReaderTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    icon: ImageVector? = null,
    state: ReaderToolState = ReaderToolState.OFF,
) {
    val color = when (state) {
        ReaderToolState.ON -> theme.accent
        ReaderToolState.BUSY -> theme.link
        ReaderToolState.OFF -> theme.fg.copy(alpha = 0.62f)
    }
    Column(
        modifier = modifier
            .widthIn(min = 56.dp)
            .height(48.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
            if (glyph != null) {
                Text(
                    text = glyph,
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(text = label, color = color, fontSize = 10.sp, maxLines = 1)
        if (state == ReaderToolState.ON || state == ReaderToolState.BUSY) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(width = 14.dp, height = 2.dp)
                    .background(color),
            )
        }
    }
}

@Composable
private fun ChapterNavRow(
    theme: NovelReaderTheme,
    index: Int,
    count: Int,
    chapterTitle: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenList: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .readerEdge(theme, top = false)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = index > 0, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.reader_chapter_prev),
                tint = if (index > 0) theme.fg.copy(alpha = 0.75f) else theme.fg.copy(alpha = 0.25f),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenList)
                .padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.reader_chapter_position, index + 1, count),
                color = theme.fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (chapterTitle.isNotBlank()) {
                Text(
                    text = chapterTitle,
                    color = theme.fg.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onNext, enabled = index < count - 1, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.reader_chapter_next),
                tint = if (index < count - 1) theme.fg.copy(alpha = 0.75f) else theme.fg.copy(alpha = 0.25f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ReaderProgressRow(
    scrollState: ScrollState,
    theme: NovelReaderTheme,
    streamProgress: Int?,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
) {
    val max = scrollState.maxValue
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

@Composable
internal fun ReaderSettingsPanel(
    settings: NovelReaderSettings,
    theme: NovelReaderTheme,
    onSettingsChange: (NovelReaderSettings, Boolean) -> Unit,
    onInteract: () -> Unit,
    cacheStatus: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderSheetShape)
            .background(theme.card.copy(alpha = 1f))
            .consumeTaps()
            .navigationBarsPadding()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(theme.fg.copy(alpha = 0.3f)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderFontButton(
                label = "A−",
                enabled = settings.fontSize > NOVEL_FONT_MIN,
                theme = theme,
                onClick = {
                    onSettingsChange(settings.copy(fontSize = settings.fontSize - 1f), true)
                    onInteract()
                },
            )
            Text(
                text = settings.fontSize.roundToInt().toString(),
                color = theme.fg,
                fontSize = 14.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            ReaderFontButton(
                label = "A+",
                enabled = settings.fontSize < NOVEL_FONT_MAX,
                theme = theme,
                onClick = {
                    onSettingsChange(settings.copy(fontSize = settings.fontSize + 1f), true)
                    onInteract()
                },
            )
        }
        ReaderHairline(theme)
        ReaderLabelRow(label = stringResource(R.string.detail_novel_background), theme = theme) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        ReaderSettingSlider(
            label = stringResource(R.string.detail_novel_brightness),
            valueText = if (settings.brightness < 0f) "" else "${(settings.brightness * 100).roundToInt()}%",
            fraction = if (settings.brightness < 0f) 1f else settings.brightness,
            theme = theme,
            onValueChange = { fraction ->
                onSettingsChange(
                    settings.copy(brightness = readerStep(fraction, NOVEL_BRIGHTNESS_MIN, 1f, 0.05f)),
                    false,
                )
            },
            onValueChangeFinished = { fraction ->
                onSettingsChange(
                    settings.copy(brightness = readerStep(fraction, NOVEL_BRIGHTNESS_MIN, 1f, 0.05f)),
                    true,
                )
                onInteract()
            },
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        ReaderHairline(theme)
        ReaderLabelRow(label = stringResource(R.string.detail_novel_layout), theme = theme) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderChoiceChip(
                    text = stringResource(R.string.novel_indent),
                    selected = settings.indentFirstLine,
                    theme = theme,
                    toggle = true,
                    onClick = {
                        onSettingsChange(settings.copy(indentFirstLine = !settings.indentFirstLine), true)
                        onInteract()
                    },
                )
                ReaderChoiceChip(
                    text = stringResource(R.string.novel_paragraph_gap),
                    selected = settings.paragraphGap,
                    theme = theme,
                    toggle = true,
                    onClick = {
                        onSettingsChange(settings.copy(paragraphGap = !settings.paragraphGap), true)
                        onInteract()
                    },
                )
                ReaderChoiceChip(
                    text = stringResource(R.string.novel_justify),
                    selected = settings.justify,
                    theme = theme,
                    toggle = true,
                    onClick = {
                        onSettingsChange(settings.copy(justify = !settings.justify), true)
                        onInteract()
                    },
                )
            }
        }
        ReaderLabelRow(label = stringResource(R.string.detail_novel_font), theme = theme) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderChoiceChip(
                    text = stringResource(R.string.detail_novel_font_sans),
                    selected = !settings.serif,
                    theme = theme,
                    onClick = {
                        onSettingsChange(settings.copy(serif = false), true)
                        onInteract()
                    },
                )
                ReaderChoiceChip(
                    text = stringResource(R.string.detail_novel_font_serif),
                    selected = settings.serif,
                    theme = theme,
                    onClick = {
                        onSettingsChange(settings.copy(serif = true), true)
                        onInteract()
                    },
                )
            }
        }
        ReaderSettingSlider(
            label = stringResource(R.string.detail_novel_line_spacing),
            valueText = lineHeightText(settings.lineHeight),
            fraction = readerFraction(settings.lineHeight, NOVEL_LINE_HEIGHT_MIN, NOVEL_LINE_HEIGHT_MAX),
            theme = theme,
            onValueChange = { fraction ->
                onSettingsChange(
                    settings.copy(
                        lineHeight = readerStep(fraction, NOVEL_LINE_HEIGHT_MIN, NOVEL_LINE_HEIGHT_MAX, 0.1f),
                    ),
                    false,
                )
            },
            onValueChangeFinished = { fraction ->
                onSettingsChange(
                    settings.copy(
                        lineHeight = readerStep(fraction, NOVEL_LINE_HEIGHT_MIN, NOVEL_LINE_HEIGHT_MAX, 0.1f),
                    ),
                    true,
                )
                onInteract()
            },
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        ReaderSettingSlider(
            label = stringResource(R.string.detail_novel_padding),
            valueText = settings.contentPadding.roundToInt().toString(),
            fraction = readerFraction(settings.contentPadding, NOVEL_PADDING_MIN, NOVEL_PADDING_MAX),
            theme = theme,
            onValueChange = { fraction ->
                onSettingsChange(
                    settings.copy(contentPadding = readerStep(fraction, NOVEL_PADDING_MIN, NOVEL_PADDING_MAX, 1f)),
                    false,
                )
            },
            onValueChangeFinished = { fraction ->
                onSettingsChange(
                    settings.copy(contentPadding = readerStep(fraction, NOVEL_PADDING_MIN, NOVEL_PADDING_MAX, 1f)),
                    true,
                )
                onInteract()
            },
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        ReaderHairline(theme)
        ReaderToggleRow(
            label = stringResource(R.string.detail_novel_brightness_auto),
            on = settings.brightness < 0f,
            theme = theme,
            onClick = {
                onSettingsChange(
                    settings.copy(
                        brightness = if (settings.brightness < 0f) 1f else NovelReaderSettings.BRIGHTNESS_SYSTEM,
                    ),
                    true,
                )
                onInteract()
            },
        )
        ReaderToggleRow(
            label = stringResource(R.string.detail_novel_keep_screen_on),
            on = settings.keepScreenOn,
            theme = theme,
            onClick = {
                onSettingsChange(settings.copy(keepScreenOn = !settings.keepScreenOn), true)
                onInteract()
            },
        )
        ReaderToggleRow(
            label = stringResource(R.string.novel_volume_scroll),
            on = settings.volumeScroll,
            theme = theme,
            onClick = {
                onSettingsChange(settings.copy(volumeScroll = !settings.volumeScroll), true)
                onInteract()
            },
        )
        if (cacheStatus != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.novel_cache_reader_label),
                    color = theme.fg.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = cacheStatus,
                    color = theme.fg.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ReaderHairline(theme: NovelReaderTheme) {
    Box(
        Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(theme.border),
    )
}

@Composable
private fun ReaderLabelRow(
    label: String,
    theme: NovelReaderTheme,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = theme.fg.copy(alpha = 0.6f),
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(SettingLabelWidth),
        )
        content()
    }
}

@Composable
internal fun NovelResumeHint(
    theme: NovelReaderTheme,
    percent: Int,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(ReaderHintShape)
            .background(theme.card.copy(alpha = 1f))
            .border(BorderStroke(0.5.dp, theme.border), ReaderHintShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.novel_resume, percent),
            color = theme.fg,
            fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.manga_restart),
            color = theme.link,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(onClick = onRestart)
                .padding(start = 12.dp),
        )
    }
}

@Composable
private fun ReaderToggleRow(
    label: String,
    on: Boolean,
    theme: NovelReaderTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = theme.fg,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(if (on) R.string.reader_switch_on else R.string.reader_switch_off),
            color = if (on) theme.accent else theme.fg.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ReaderSettingSlider(
    label: String,
    valueText: String,
    fraction: Float,
    theme: NovelReaderTheme,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = theme.fg.copy(alpha = 0.6f),
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
    }
}

@Composable
internal fun ReaderSlider(
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

@Composable
private fun ReaderChoiceChip(
    text: String,
    selected: Boolean,
    theme: NovelReaderTheme,
    onClick: () -> Unit,
    toggle: Boolean = false,
) {
    Text(
        text = text,
        color = if (selected) theme.accent else theme.fg.copy(alpha = 0.7f),
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(ReaderChipShape)
            .background(if (selected) theme.accent.copy(alpha = 0.15f) else Color.Transparent)
            .then(
                if (toggle) {
                    Modifier.toggleable(value = selected, role = Role.Switch, onValueChange = { onClick() })
                } else {
                    Modifier.clickable(role = Role.RadioButton, onClick = onClick)
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

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
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovelChapterSheet(
    theme: NovelReaderTheme,
    chapters: List<NovelChapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.card,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.fg.copy(alpha = 0.3f)),
                )
            }
            Text(
                text = stringResource(R.string.reader_chapter_list),
                color = theme.fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.reader_chapter_position, currentIndex + 1, chapters.size),
                color = theme.fg.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .navigationBarsPadding(),
        ) {
            itemsIndexed(chapters) { index, chapter ->
                val current = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(index)
                            onDismiss()
                        }
                        .background(
                            if (current) theme.accent.copy(alpha = 0.14f) else Color.Transparent,
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.reader_chapter_number, index + 1),
                        color = if (current) theme.accent else theme.fg.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        text = readerChapterLabel(
                            title = chapter.title,
                            fallback = stringResource(R.string.reader_chapter_number, index + 1),
                        ),
                        color = if (current) theme.accent else theme.fg,
                        fontSize = 14.sp,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun readerChapterLabel(title: String, fallback: String = ""): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return fallback
    if (trimmed.startsWith("Chapter ", ignoreCase = true)) {
        val colon = trimmed.indexOf(':')
        if (colon in 1 until trimmed.length) {
            return trimmed.substring(colon + 1).trim().ifEmpty { fallback }
        }
    }
    return trimmed
}

internal fun readerFraction(value: Float, min: Float, max: Float): Float =
    if (max <= min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)

internal fun readerStep(fraction: Float, min: Float, max: Float, step: Float): Float {
    if (max <= min || step <= 0f) return min
    val count = ((max - min) / step).roundToInt().coerceAtLeast(1)
    val index = (fraction.coerceIn(0f, 1f) * count).roundToInt()
    return min + (max - min) * index / count
}

private fun lineHeightText(lineHeight: Float): String {
    val tenths = (lineHeight * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
