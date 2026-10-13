package com.piku.client.core.reader
import com.piku.client.core.reader.ViewerImage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowLeft
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MANGA_HINT_MILLIS = 6_000L

/**
 * 阅读器控件的配色：看的是图，控件不跟 app 主题走，一律深底浅字。
 * 底 78% 黑压在图上是"浮起但不实心"的那档，比纯色面板轻。
 */
private val MangaBarBg = Color(0xC7141312)
private val MangaBarBorder = Color(0x14FFFFFF)
private val MangaFg = Color(0xFFF2EFEA)
private val MangaFgMuted = Color(0x9EFFFFFF)
private val MangaOn = Color(0xFFE0E0E0)
private val MangaTranslateOn = Color(0xFF4FC3F7)
private val MangaTrack = Color(0x38FFFFFF)
private val MangaSegBg = Color(0x14FFFFFF)
private val MangaSegOn = Color(0x29FFFFFF)
private val MangaCellBg = Color(0xFF262421)
private val MangaCellBorder = Color(0xFF46405A)
// 色带用压暗：漫画页多是白底，白色填充看不见；压暗在白底出灰、边界亮线在暗底看得见
private val MangaZoneFill = Color(0x3D000000)
private val MangaZoneEdge = Color(0x73FFFFFF)

private val MangaPillShape = RoundedCornerShape(12.dp)
private val MangaCardShape = RoundedCornerShape(12.dp)
private val MangaChipShape = RoundedCornerShape(999.dp)
private val MangaSettingLabelWidth = 68.dp
private val MangaSettingValueWidth = 44.dp
private val MangaSettingRowHeight = 40.dp

/** 设置面板的两段式控件与开关芯片：轨道 + 统一圆角，不用大椭圆胶囊 */
private val MangaSegRowWidth = 168.dp
private val MangaSegShape = RoundedCornerShape(8.dp)

private fun Modifier.mangaCard(shape: Shape = MangaCardShape): Modifier =
    this.clip(shape).background(MangaBarBg).border(BorderStroke(0.5.dp, MangaBarBorder), shape)

/** 控件自己的空白处吃掉点击：否则点面板/进度条旁边的空隙会穿透到图片，把控制栏点没了 */
private fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** 顶栏：返回 + 标题 + 总览 + 设置；与控制栏同寿，静止时整条淡出 */
@Composable
internal fun MangaReaderTopBar(
    title: String,
    panelOpen: Boolean,
    onClose: () -> Unit,
    onOverview: () -> Unit,
    onTogglePanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .mangaCard(MangaPillShape)
            .consumeTaps()
            .padding(start = 2.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PikuBackButton(
            onClick = onClose,
            dark = true,
            contentDescription = stringResource(R.string.detail_fullscreen_close),
            tint = MangaFg,
        )
        Text(
            text = title,
            color = MangaFg,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOverview, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Outlined.GridView,
                contentDescription = stringResource(R.string.manga_overview),
                tint = MangaFg,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = onTogglePanel, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.manga_settings),
                tint = if (panelOpen) MangaOn else MangaFg,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * 底栏两行：进度条 + 等宽页码；HD · 译 与模式分段。
 * 方向、适配、间隙不在这儿——低频项占着常驻位置只会把屏幕做满。
 */
@Composable
internal fun MangaReaderBottomBar(
    page: Int,
    total: Int,
    scrollMode: Boolean,
    hdAvailable: Boolean,
    hdOn: Boolean,
    translateAvailable: Boolean,
    translating: Boolean,
    translated: Boolean,
    onSeek: (Int) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onHdToggle: () -> Unit,
    onTranslate: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .mangaCard()
            .consumeTaps()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReaderSlider(
                fraction = if (total <= 1) 1f else (page - 1).toFloat() / (total - 1),
                onValueChange = { fraction ->
                    if (total > 1) onSeek(1 + (fraction * (total - 1)).roundToInt())
                },
                onValueChangeFinished = { onInteract() },
                active = MangaOn,
                trackColor = MangaTrack,
                modifier = Modifier.weight(1f).height(22.dp),
            )
            Text(
                text = "$page / $total",
                color = MangaFgMuted,
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
                // 等宽数字：翻页时数字位宽不变，页码不会左右抖；
                // 用 minWidth 而不是定宽，四位数页码也放得下
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.widthIn(min = 58.dp).padding(start = 10.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hdAvailable) {
                MangaTextAction(
                    text = "HD",
                    on = hdOn,
                    onClick = { onHdToggle(); onInteract() },
                )
                Box(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .size(width = 1.dp, height = 12.dp)
                        .background(MangaBarBorder),
                )
            }
            if (translateAvailable) {
                MangaTextAction(
                    text = stringResource(R.string.manga_translate),
                    on = translated,
                    enabled = !translating,
                    accent = MangaTranslateOn,
                    onClick = { onTranslate(); onInteract() },
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(MangaSegBg)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MangaSegButton(
                    text = stringResource(R.string.manga_mode_paged),
                    selected = !scrollMode,
                    onClick = { onModeChange(false); onInteract() },
                )
                MangaSegButton(
                    text = stringResource(R.string.manga_mode_scroll),
                    selected = scrollMode,
                    onClick = { onModeChange(true); onInteract() },
                )
            }
        }
    }
}

/** 不点亮时是低调的灰白文字，点亮才上色——不做实心底，否则两个按钮的视觉重量跟主功能一样 */
@Composable
private fun MangaTextAction(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Color = MangaOn,
) {
    Text(
        text = text,
        color = if (on && enabled) accent else MangaFgMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(MangaChipShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun MangaSegButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) MangaFg else MangaFgMuted,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) MangaSegOn else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 阅读设置面板，从底栏上方展开 */
@Composable
internal fun MangaReaderSettingsPanel(
    settings: MangaReaderSettings,
    onChange: (MangaReaderSettings, Boolean) -> Unit,
    onReplayHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .mangaCard()
            .consumeTaps()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // 方向与适配只作用于翻页形态、间隙只作用于滚动形态，当前形态用不到的项不显示。
        // 形态切换不在这里：底栏那颗分段就在面板下方，同时可见时不该出现两份
        if (settings.scrollMode) {
            MangaSettingRow(label = stringResource(R.string.manga_gap)) {
                MangaSegChoice(
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
                MangaSegChoice(
                    firstText = stringResource(R.string.manga_direction_ltr),
                    firstSelected = !settings.rightToLeft,
                    onFirst = { onChange(settings.copy(rightToLeft = false), true) },
                    secondText = stringResource(R.string.manga_direction_rtl),
                    secondSelected = settings.rightToLeft,
                    onSecond = { onChange(settings.copy(rightToLeft = true), true) },
                )
            }
            MangaSettingRow(label = stringResource(R.string.manga_fit)) {
                MangaSegChoice(
                    firstText = stringResource(R.string.manga_fit_page),
                    firstSelected = !settings.fillWidth,
                    onFirst = { onChange(settings.copy(fillWidth = false), true) },
                    secondText = stringResource(R.string.manga_fit_width),
                    secondSelected = settings.fillWidth,
                    onSecond = { onChange(settings.copy(fillWidth = true), true) },
                )
            }
        }
        val followSystem = settings.brightness < 0f
        Row(
            modifier = Modifier.fillMaxWidth().height(MangaSettingRowHeight),
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
                fraction = if (followSystem) 1f else settings.brightness,
                // 不做「跟随系统就不能拖」的拦截：拖滑块本身就是离开跟随系统的动作，
                // 拦掉之后在跟随系统下会没有任何出口，亮度永远调不了
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
                active = MangaOn,
                trackColor = MangaTrack,
                modifier = Modifier.weight(1f).height(22.dp),
            )
            Text(
                text = if (followSystem) "" else "${(settings.brightness * 100).roundToInt()}%",
                color = MangaFgMuted,
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(MangaSettingValueWidth),
            )
            MangaToggleChip(
                text = stringResource(R.string.manga_brightness_auto),
                on = followSystem,
                onClick = {
                    onChange(
                        settings.copy(brightness = MangaReaderSettings.BRIGHTNESS_SYSTEM),
                        true,
                    )
                },
            )
        }
        MangaSettingRow(label = "") {
            MangaToggleChip(
                text = stringResource(R.string.manga_keep_screen_on),
                on = settings.keepScreenOn,
                onClick = { onChange(settings.copy(keepScreenOn = !settings.keepScreenOn), true) },
            )
            Spacer(Modifier.width(6.dp))
            MangaToggleChip(
                text = stringResource(R.string.manga_volume_flip),
                on = settings.volumeFlip,
                onClick = { onChange(settings.copy(volumeFlip = !settings.volumeFlip), true) },
            )
        }
        if (BuildConfig.DEBUG) {
            // 调试专用：分隔线下面一行低调的文字入口，不做成又一颗开关
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MangaBarBorder))
            Text(
                text = stringResource(R.string.manga_hint_replay),
                color = MangaFgMuted,
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MangaSegShape)
                    .clickable(onClick = onReplayHint)
                    .padding(vertical = 9.dp),
            )
        }
    }
}

/**
 * 一次性操作提示：按屏幕三等分画出真实点按热区——左右各 1/3 翻页、中间 1/3 显隐菜单。
 * 只在图上画，不吃事件：点哪块就真的执行哪块，配合"首次操作即退场"。
 */
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
    // 右→左时左侧是"下一页"，切成左→右后互换：标签永远跟当前方向一致
    val forwardOnLeft = settings.rightToLeft
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        val third = Modifier.fillMaxHeight().fillMaxWidth(1f / TAP_ZONE_DIVISOR)
        if (settings.scrollMode) {
            // 滚动形态左右点按无操作，只画中间那条
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

/** 分区线只画在本区靠中心的那条边上，和相邻区共享一条缝 */
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
                .clip(MangaChipShape)
                .background(MangaBarBg)
                .border(BorderStroke(0.5.dp, MangaBarBorder), MangaChipShape)
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

/** 副手势说明：压在图上，靠投影保证亮底也能读清 */
@Composable
private fun HintCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MangaFg,
        fontSize = 12.sp,
        maxLines = 1,
        style = LocalTextStyle.current.copy(shadow = Shadow(Color(0xCC000000), blurRadius = 6f)),
        modifier = modifier,
    )
}

@Composable
private fun MangaSettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(MangaSettingRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MangaFgMuted,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(MangaSettingLabelWidth),
        )
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** 两段式控件：一条浅底轨道 + 等宽两段，各行控件的位置因此完全对齐 */
@Composable
private fun MangaSegChoice(
    firstText: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    secondText: String,
    secondSelected: Boolean,
    onSecond: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(MangaSegRowWidth)
            .clip(MangaSegShape)
            .background(MangaSegBg)
            .padding(2.dp),
    ) {
        MangaSegSegment(firstText, firstSelected, onFirst, Modifier.weight(1f))
        MangaSegSegment(secondText, secondSelected, onSecond, Modifier.weight(1f))
    }
}

@Composable
private fun MangaSegSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = if (selected) MangaFg else MangaFgMuted,
        fontSize = 12.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MangaSegOn else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

/** 开关芯片：未选中也留一层浅底，否则一整行只剩光秃秃的文字看不出能点 */
@Composable
private fun MangaToggleChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (on) MangaFg else MangaFgMuted,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(MangaSegShape)
            .background(if (on) MangaSegOn else MangaSegBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 缩略图总览：页数多时靠它一步跳到想看的页 */
@Composable
internal fun MangaOverviewPanel(
    images: List<ViewerImage>,
    currentPage: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141312)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
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
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(MangaChipShape)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(images) { index, image ->
                val selected = index == currentPage - 1
                val cellShape = RoundedCornerShape(4.dp)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f / 1.42f)
                        .clip(cellShape)
                        .background(MangaCellBg)
                        .border(
                            BorderStroke(
                                if (selected) 2.dp else 0.5.dp,
                                if (selected) MangaOn else MangaCellBorder,
                            ),
                            cellShape,
                        )
                        .clickable { onPick(index + 1) },
                ) {
                    AsyncImage(
                        model = rememberAnimatedImage(image.thumbnailUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = "${index + 1}",
                        color = if (selected) MangaFg else MangaFgMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}
