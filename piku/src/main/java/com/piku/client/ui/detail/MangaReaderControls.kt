package com.piku.client.ui.detail

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
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.data.local.MangaReaderSettings
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.rememberAnimatedImage
import kotlin.math.roundToInt

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

private val MangaPillShape = RoundedCornerShape(12.dp)
private val MangaCardShape = RoundedCornerShape(12.dp)
private val MangaChipShape = RoundedCornerShape(999.dp)
private val MangaSettingLabelWidth = 68.dp
private val MangaSettingValueWidth = 44.dp

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .mangaCard()
            .consumeTaps()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        MangaSettingRow(label = stringResource(R.string.manga_mode)) {
            MangaChoiceChip(
                text = stringResource(R.string.manga_mode_paged),
                selected = !settings.scrollMode,
                onClick = { onChange(settings.copy(scrollMode = false), true) },
            )
            Spacer(Modifier.width(6.dp))
            MangaChoiceChip(
                text = stringResource(R.string.manga_mode_scroll),
                selected = settings.scrollMode,
                onClick = { onChange(settings.copy(scrollMode = true), true) },
            )
        }
        MangaSettingRow(label = stringResource(R.string.manga_direction)) {
            MangaChoiceChip(
                text = stringResource(R.string.manga_direction_ltr),
                selected = !settings.rightToLeft,
                onClick = { onChange(settings.copy(rightToLeft = false), true) },
            )
            Spacer(Modifier.width(6.dp))
            MangaChoiceChip(
                text = stringResource(R.string.manga_direction_rtl),
                selected = settings.rightToLeft,
                onClick = { onChange(settings.copy(rightToLeft = true), true) },
            )
        }
        MangaSettingRow(label = stringResource(R.string.manga_fit)) {
            MangaChoiceChip(
                text = stringResource(R.string.manga_fit_page),
                selected = !settings.fillWidth,
                onClick = { onChange(settings.copy(fillWidth = false), true) },
            )
            Spacer(Modifier.width(6.dp))
            MangaChoiceChip(
                text = stringResource(R.string.manga_fit_width),
                selected = settings.fillWidth,
                onClick = { onChange(settings.copy(fillWidth = true), true) },
            )
        }
        MangaSettingRow(label = stringResource(R.string.manga_gap)) {
            MangaChoiceChip(
                text = stringResource(R.string.manga_gap_none),
                selected = !settings.pageGap,
                onClick = { onChange(settings.copy(pageGap = false), true) },
            )
            Spacer(Modifier.width(6.dp))
            MangaChoiceChip(
                text = stringResource(R.string.manga_gap_small),
                selected = settings.pageGap,
                onClick = { onChange(settings.copy(pageGap = true), true) },
            )
        }
        val followSystem = settings.brightness < 0f
        Row(
            modifier = Modifier.fillMaxWidth().height(38.dp),
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
                onValueChange = { fraction ->
                    if (!followSystem) {
                        onChange(settings.copy(brightness = fraction.coerceIn(0.05f, 1f)), false)
                    }
                },
                onValueChangeFinished = { fraction ->
                    if (!followSystem) {
                        onChange(settings.copy(brightness = fraction.coerceIn(0.05f, 1f)), true)
                    }
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
            MangaChoiceChip(
                text = stringResource(R.string.manga_brightness_auto),
                selected = followSystem,
                onClick = {
                    onChange(
                        settings.copy(brightness = MangaReaderSettings.BRIGHTNESS_SYSTEM),
                        true,
                    )
                },
            )
        }
        MangaSettingRow(label = "") {
            MangaChoiceChip(
                text = stringResource(R.string.manga_keep_screen_on),
                selected = settings.keepScreenOn,
                onClick = { onChange(settings.copy(keepScreenOn = !settings.keepScreenOn), true) },
            )
            Spacer(Modifier.width(6.dp))
            MangaChoiceChip(
                text = stringResource(R.string.manga_volume_flip),
                selected = settings.volumeFlip,
                onClick = { onChange(settings.copy(volumeFlip = !settings.volumeFlip), true) },
            )
        }
    }
}

@Composable
private fun MangaSettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp),
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

@Composable
private fun MangaChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) MangaOn else MangaFgMuted,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(MangaChipShape)
            .background(if (selected) MangaSegOn else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
