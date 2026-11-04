package com.piku.client.core.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowLeft
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.BuildConfig
import com.piku.client.R
import com.piku.client.core.storage.prefs.MangaReaderSettings
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.ui.common.PikuBackButton
import com.piku.client.core.ui.common.rememberAnimatedImage
import com.piku.client.core.ui.theme.ControlAccentDark
import com.piku.client.core.ui.theme.ErrorRedDark
import com.piku.client.core.ui.theme.InsetSurfacePressedDark
import com.piku.client.core.ui.theme.LoginBackgroundDark
import com.piku.client.core.ui.theme.LoginCardDark
import com.piku.client.core.ui.theme.LoginTextPrimaryDark
import com.piku.client.core.ui.theme.LoginTextSecondaryDark
import com.piku.client.core.ui.theme.NovelReaderTheme
import com.piku.client.core.ui.theme.OverlayScrimFaint
import com.piku.client.core.ui.theme.SoftBorderDark
import com.piku.client.core.ui.theme.TranslateActiveBlue
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MANGA_HINT_MILLIS = 6_000L

private val MangaBar = LoginBackgroundDark
private val MangaCellBg = LoginCardDark
private val MangaFg = NovelReaderTheme.Ink.fg
private val MangaFgMuted = LoginTextSecondaryDark
private val MangaAccent = ControlAccentDark
private val MangaBlue = TranslateActiveBlue
private val MangaError = ErrorRedDark
private val MangaLine = LoginTextPrimaryDark.copy(alpha = 0.12f)
private val MangaTrack = NovelReaderTheme.Ink.track
private val MangaSegOn = InsetSurfacePressedDark
private val MangaZoneFill = OverlayScrimFaint
private val MangaZoneEdge = SoftBorderDark

private val MangaTagShape = RoundedCornerShape(4.dp)
private val MangaSettingLabelWidth = 62.dp
private val MangaSettingValueWidth = 60.dp
private val MangaSettingRowHeight = 46.dp
private val MangaSegRowWidth = 168.dp

private fun Modifier.mangaTopEdge(): Modifier = drawBehind {
    val stroke = 0.5.dp.toPx()
    drawLine(MangaLine, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
}

private fun Modifier.mangaBottomEdge(): Modifier = drawBehind {
    val stroke = 0.5.dp.toPx()
    val y = size.height - stroke / 2f
    drawLine(MangaLine, Offset(0f, y), Offset(size.width, y), stroke)
}

/** 控件空白处吃掉点击，否则点进度条旁边会把控制栏点没 */
private fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

private enum class MangaToolState { OFF, ON, BUSY, ERROR }

@Composable
internal fun MangaReaderTopBar(
    title: String,
    onClose: () -> Unit,
    onOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MangaBar)
            .mangaBottomEdge()
            .consumeTaps()
            .padding(top = topInset + 10.dp)
            .padding(start = 2.dp, end = 8.dp, bottom = 10.dp),
    ) {
        // 打孔屏摄像头在顶部正中，这一行中间不放任何东西
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PikuBackButton(
                onClick = onClose,
                dark = true,
                contentDescription = stringResource(R.string.detail_fullscreen_close),
                tint = MangaFg,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOverview, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = stringResource(R.string.manga_overview),
                    tint = MangaFg,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Text(
            text = title,
            color = MangaFgMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 48.dp),
        )
    }
}

/** HD、译、设置各写明当前值；状态靠文字 + 主次色 + 底部短横线，不单靠颜色 */
@Composable
internal fun MangaReaderBottomBar(
    page: Int,
    total: Int,
    hdAvailable: Boolean,
    hdOn: Boolean,
    translateAvailable: Boolean,
    translating: Boolean,
    translated: Boolean,
    onSeek: (Int) -> Unit,
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    onHdToggle: () -> Unit,
    onTranslate: () -> Unit,
    onOpenSettings: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MangaBar)
            .mangaTopEdge()
            .navigationBarsPadding()
            .consumeTaps(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (total > 1) {
                ReaderSlider(
                    fraction = (page - 1).toFloat() / (total - 1),
                    onValueChange = { fraction ->
                        onSeek(1 + (fraction * (total - 1)).roundToInt())
                    },
                    onValueChangeFinished = { onInteract() },
                    onDragStart = onSeekStart,
                    onDragEnd = onSeekEnd,
                    active = MangaAccent,
                    trackColor = MangaTrack,
                    modifier = Modifier.weight(1f).height(22.dp),
                )
            } else {
                // 单页没有可拖的进度，给一条静态细线占位
                Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .padding(vertical = 9.dp)
                        .background(MangaAccent),
                )
            }
            Text(
                text = "$page / $total",
                color = MangaFgMuted,
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.widthIn(min = 58.dp).padding(start = 10.dp),
            )
        }
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hdAvailable) {
                MangaToolButton(
                    glyph = "HD",
                    label = stringResource(
                        if (hdOn) R.string.manga_hd_original else R.string.manga_hd_standard,
                    ),
                    state = if (hdOn) MangaToolState.ON else MangaToolState.OFF,
                    onClick = { onHdToggle(); onInteract() },
                )
            }
            if (translateAvailable) {
                MangaToolButton(
                    glyph = stringResource(R.string.manga_translate),
                    label = stringResource(
                        when {
                            translating -> R.string.manga_translate_busy
                            translated -> R.string.manga_translate_done
                            else -> R.string.manga_translate_idle
                        },
                    ),
                    state = when {
                        translating -> MangaToolState.BUSY
                        translated -> MangaToolState.ON
                        else -> MangaToolState.OFF
                    },
                    enabled = !translating,
                    onClick = { onTranslate(); onInteract() },
                )
            }
            Spacer(Modifier.weight(1f))
            MangaToolButton(
                icon = Icons.Outlined.Tune,
                label = stringResource(R.string.manga_tools_settings),
                onClick = { onOpenSettings(); onInteract() },
            )
        }
    }
}

@Composable
private fun MangaToolButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    icon: ImageVector? = null,
    state: MangaToolState = MangaToolState.OFF,
    enabled: Boolean = true,
) {
    val color = when (state) {
        MangaToolState.ON -> MangaAccent
        MangaToolState.BUSY -> MangaBlue
        MangaToolState.ERROR -> MangaError
        MangaToolState.OFF -> MangaFgMuted
    }
    Column(
        modifier = modifier
            .widthIn(min = 52.dp)
            .height(52.dp)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (glyph != null) {
                Text(
                    text = glyph,
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            } else if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Text(text = label, color = color, fontSize = 11.sp, maxLines = 1)
        if (state == MangaToolState.ON || state == MangaToolState.BUSY) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(width = 14.dp, height = 2.dp)
                    .background(color),
            )
        }
    }
}

/** 设置全部归到一处：形态、方向 / 适配 / 间隙、亮度、常亮、音量键 */
@Composable
internal fun MangaReaderSettingsPanel(
    settings: MangaReaderSettings,
    onChange: (MangaReaderSettings, Boolean) -> Unit,
    onClose: () -> Unit,
    onReplayHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MangaBar)
            .mangaTopEdge()
            .navigationBarsPadding()
            .consumeTaps(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MangaSettingRowHeight)
                .padding(start = 14.dp, end = 8.dp)
                .mangaBottomEdge(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.manga_settings),
                color = MangaFg,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.detail_fullscreen_close),
                    tint = MangaFgMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        MangaGroupLabel(stringResource(R.string.manga_group_reading))
        MangaSettingRow(label = stringResource(R.string.manga_mode)) {
            MangaValueChoice(
                firstText = stringResource(R.string.manga_mode_paged),
                firstSelected = !settings.scrollMode,
                onFirst = { onChange(settings.copy(scrollMode = false), true) },
                secondText = stringResource(R.string.manga_mode_scroll),
                secondSelected = settings.scrollMode,
                onSecond = { onChange(settings.copy(scrollMode = true), true) },
            )
        }
        if (settings.scrollMode) {
            MangaSettingRow(label = stringResource(R.string.manga_gap)) {
                MangaValueChoice(
                    firstText = stringResource(R.string.manga_gap_none),
                    firstSelected = !settings.pageGap,
                    onFirst = { onChange(settings.copy(pageGap = false), true) },
                    secondText = stringResource(R.string.manga_gap_small),
                    secondSelected = settings.pageGap,
                    onSecond = { onChange(settings.copy(pageGap = true), true) },
                )
            }
        } else {
            MangaSettingRow(label = stringResource(R.string.manga_direction)) {
                MangaValueChoice(
                    firstText = stringResource(R.string.manga_direction_ltr),
                    firstSelected = !settings.rightToLeft,
                    onFirst = { onChange(settings.copy(rightToLeft = false), true) },
                    secondText = stringResource(R.string.manga_direction_rtl),
                    secondSelected = settings.rightToLeft,
                    onSecond = { onChange(settings.copy(rightToLeft = true), true) },
                )
            }
            MangaSettingRow(label = stringResource(R.string.manga_fit)) {
                MangaValueChoice(
                    firstText = stringResource(R.string.manga_fit_page),
                    firstSelected = !settings.fillWidth,
                    onFirst = { onChange(settings.copy(fillWidth = false), true) },
                    secondText = stringResource(R.string.manga_fit_width),
                    secondSelected = settings.fillWidth,
                    onSecond = { onChange(settings.copy(fillWidth = true), true) },
                )
            }
        }
        MangaGroupLabel(stringResource(R.string.manga_group_display))
        val followSystem = settings.brightness < 0f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MangaSettingRowHeight)
                .padding(horizontal = 14.dp)
                .mangaBottomEdge(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.manga_brightness),
                color = MangaFgMuted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.width(MangaSettingLabelWidth),
            )
            ReaderSlider(
                fraction = if (followSystem) {
                    1f
                } else {
                    readerFraction(settings.brightness, SettingsRepository.MANGA_BRIGHTNESS_MIN, 1f)
                },
                onValueChange = { fraction ->
                    onChange(
                        settings.copy(
                            brightness = readerStep(fraction, SettingsRepository.MANGA_BRIGHTNESS_MIN, 1f, 0.05f),
                        ),
                        false,
                    )
                },
                onValueChangeFinished = { fraction ->
                    onChange(
                        settings.copy(
                            brightness = readerStep(fraction, SettingsRepository.MANGA_BRIGHTNESS_MIN, 1f, 0.05f),
                        ),
                        true,
                    )
                },
                active = MangaAccent,
                trackColor = MangaTrack,
                modifier = Modifier.weight(1f).height(22.dp),
            )
            Text(
                text = if (followSystem) "—" else "${(settings.brightness * 100).roundToInt()}%",
                color = MangaFgMuted,
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.width(MangaSettingValueWidth),
            )
        }
        MangaCheckRow(
            label = stringResource(R.string.manga_brightness_auto),
            on = followSystem,
            onClick = {
                onChange(
                    settings.copy(
                        brightness = if (followSystem) 1f else MangaReaderSettings.BRIGHTNESS_SYSTEM,
                    ),
                    true,
                )
            },
        )
        MangaGroupLabel(stringResource(R.string.manga_group_other))
        MangaCheckRow(
            label = stringResource(R.string.manga_keep_screen_on),
            on = settings.keepScreenOn,
            onClick = { onChange(settings.copy(keepScreenOn = !settings.keepScreenOn), true) },
        )
        MangaCheckRow(
            label = stringResource(R.string.manga_volume_flip),
            on = settings.volumeFlip,
            onClick = { onChange(settings.copy(volumeFlip = !settings.volumeFlip), true) },
        )
        if (BuildConfig.DEBUG) {
            Text(
                text = stringResource(R.string.manga_hint_replay),
                color = MangaFgMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReplayHint)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun MangaGroupLabel(text: String) {
    Text(
        text = text,
        color = MangaFgMuted,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun MangaCheckRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MangaSettingRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .mangaBottomEdge(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MangaFg, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (on) MangaAccent else Color.Transparent)
                .border(0.5.dp, if (on) MangaAccent else MangaFgMuted, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MangaBar,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun MangaSettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MangaSettingRowHeight)
            .padding(horizontal = 14.dp)
            .mangaBottomEdge(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MangaFgMuted,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(MangaSettingLabelWidth),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) { content() }
    }
}

/** 二选一：两段文字加一条竖线，选中项靠字号和颜色区分，不做轨道与胶囊 */
@Composable
private fun MangaValueChoice(
    firstText: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    secondText: String,
    secondSelected: Boolean,
    onSecond: () -> Unit,
) {
    Row(
        modifier = Modifier.width(MangaSegRowWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        MangaValueOption(firstText, firstSelected, onFirst)
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .size(width = 1.dp, height = 12.dp)
                .background(MangaFgMuted.copy(alpha = 0.3f)),
        )
        MangaValueOption(secondText, secondSelected, onSecond)
    }
}

@Composable
private fun MangaValueOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) MangaFg else MangaFgMuted,
        fontSize = if (selected) 13.sp else 12.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/** 重新打开时告诉用户从哪里续读，不打断阅读 */
@Composable
internal fun MangaResumeHint(
    page: Int,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(MangaBar)
            .mangaTopEdge()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.manga_resume, page),
            color = MangaFg,
            fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.manga_restart),
            color = MangaBlue,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onRestart).padding(4.dp),
        )
    }
}

@Composable
internal fun MangaZoneHint(
    settings: MangaReaderSettings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "mangaHintAlpha",
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(MANGA_HINT_MILLIS)
        visible = false
        delay(220)
        onDismiss()
    }
    val forwardOnLeft = settings.rightToLeft
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        val third = Modifier.fillMaxHeight().fillMaxWidth(1f / TAP_ZONE_DIVISOR)
        if (settings.scrollMode) {
            HintZone(
                label = stringResource(R.string.manga_hint_zone_menu),
                icon = Icons.Outlined.TouchApp,
                filled = true,
                modifier = third.align(Alignment.Center),
            )
        } else {
            HintZone(
                label = stringResource(
                    if (forwardOnLeft) R.string.manga_hint_zone_next else R.string.manga_hint_zone_prev,
                ),
                icon = Icons.AutoMirrored.Outlined.ArrowLeft,
                filled = true,
                edge = HintZoneEdge.RIGHT,
                modifier = third.align(Alignment.CenterStart),
            )
            HintZone(
                label = stringResource(R.string.manga_hint_zone_menu),
                icon = Icons.Outlined.TouchApp,
                filled = false,
                modifier = third.align(Alignment.Center),
            )
            HintZone(
                label = stringResource(
                    if (forwardOnLeft) R.string.manga_hint_zone_prev else R.string.manga_hint_zone_next,
                ),
                icon = Icons.AutoMirrored.Outlined.ArrowRight,
                filled = true,
                edge = HintZoneEdge.LEFT,
                modifier = third.align(Alignment.CenterEnd),
            )
        }
        HintCaption(
            text = stringResource(R.string.manga_hint_long_press),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
    }
}

private enum class HintZoneEdge { NONE, LEFT, RIGHT }

@Composable
private fun HintZone(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    edge: HintZoneEdge = HintZoneEdge.NONE,
) {
    Box(
        modifier = modifier
            .then(if (filled) Modifier.background(MangaZoneFill) else Modifier)
            .zoneEdge(edge),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(MangaTagShape)
                .background(MangaBar)
                .border(0.5.dp, MangaLine, MangaTagShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MangaFg,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                color = MangaFg,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

private fun Modifier.zoneEdge(edge: HintZoneEdge): Modifier =
    if (edge == HintZoneEdge.NONE) {
        this
    } else {
        drawBehind {
            val stroke = 1.5.dp.toPx()
            val x = if (edge == HintZoneEdge.RIGHT) size.width - stroke / 2f else stroke / 2f
            drawLine(MangaZoneEdge, Offset(x, 0f), Offset(x, size.height), stroke)
        }
    }

@Composable
private fun HintCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MangaFg,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = modifier
            .clip(MangaTagShape)
            .background(MangaBar)
            .border(0.5.dp, MangaLine, MangaTagShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 缩略图完整显示不裁切，当前页用左侧竖条标出 */
@Composable
internal fun MangaOverviewPanel(
    images: List<ViewerImage>,
    currentPage: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MangaBar),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset + 10.dp)
                .height(48.dp)
                .mangaBottomEdge()
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.manga_overview_title, images.size),
                color = MangaFg,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.detail_fullscreen_close),
                color = MangaFgMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(MangaTagShape)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(images) { index, image ->
                val selected = index == currentPage - 1
                val cellShape = RoundedCornerShape(2.dp)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f / 1.42f)
                        .clip(cellShape)
                        .background(if (selected) MangaSegOn else MangaCellBg)
                        .then(
                            if (selected) {
                                Modifier.drawBehind {
                                    drawLine(
                                        MangaAccent,
                                        Offset(0f, 0f),
                                        Offset(0f, size.height),
                                        3.dp.toPx(),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onPick(index + 1) },
                ) {
                    AsyncImage(
                        model = rememberAnimatedImage(image.thumbnailUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = "${index + 1}",
                        color = if (selected) MangaFg else MangaFgMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(MangaBar)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
