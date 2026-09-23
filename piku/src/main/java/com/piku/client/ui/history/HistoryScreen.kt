package com.piku.client.ui.history

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import com.piku.client.ui.theme.LocalDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.domain.model.HistoryItem
import com.piku.client.domain.model.HistoryTimeRange
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.PikuBottomSheet
import com.piku.client.ui.common.PikuSegmented
import com.piku.client.ui.common.PikuSheetSubtitle
import com.piku.client.ui.common.PikuSheetTitle
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.MenuPopupBgDark
import com.piku.client.ui.theme.MenuPopupBgLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val SameYearDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val FullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private val GridPadding = 16.dp
/** 有跳转轴时右侧多让出 12dp，让热区落在卡片外，不抢卡片点击 */
private val GridPaddingWithScrubber = 28.dp
private val ScrubberHitWidth = 26.dp
private val ScrubberTrackInset = 14.dp

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onWorkClick: (Work) -> Unit,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val gridState = rememberLazyStaggeredGridState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var armedWorkId by remember { mutableStateOf<Long?>(null) }
    val today = remember { LocalDate.now() }
    val filtered = state.selectedRange != HistoryTimeRange.ALL
    // 一滚动就收起待操作态，避免删除按钮赖在屏幕上
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) armedWorkId = null
    }

    // 每个日期分组在网格里的扁平下标，供跳转轴定位（分组头之后紧跟该组的卡片）
    val headerIndices = remember(state.sections) {
        val indices = ArrayList<Int>(state.sections.size)
        var cursor = 0
        state.sections.forEach { section ->
            indices.add(cursor)
            cursor += 1 + section.items.size
        }
        indices
    }
    val showScrubber = headerIndices.size >= 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            HistoryTopBar(
                count = state.count,
                onBack = onBack,
                onFilter = { showFilterSheet = true },
                dark = dark,
                filtered = filtered,
            )
            when {
                !state.loaded -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
                state.sections.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HistoryEmptyState(
                            filtered = filtered,
                            onShowAll = { viewModel.selectRange(HistoryTimeRange.ALL) },
                        )
                    }
                }
                else -> {
                    // LazyStaggeredGrid 的 DSL 没有 stickyHeader，自己叠一层吸顶日期条：
                    // 与列表内的分组头样式完全一致，重合时看不出接缝
                    val stickyIndex = remember(headerIndices, gridState.firstVisibleItemIndex) {
                        headerIndices
                            .indexOfLast { it <= gridState.firstVisibleItemIndex }
                            .coerceAtLeast(0)
                    }
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalStaggeredGrid(
                            columns = if (isTablet) {
                                StaggeredGridCells.Adaptive(220.dp)
                            } else {
                                StaggeredGridCells.Fixed(2)
                            },
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = GridPadding,
                                end = if (showScrubber) GridPaddingWithScrubber else GridPadding,
                                bottom = 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            state.sections.forEach { section ->
                                item(
                                    key = "date-${section.date}",
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    HistoryDateHeader(
                                        date = section.date,
                                        count = section.items.size,
                                        today = today,
                                        screenWidth = screenWidth,
                                    )
                                }
                                items(section.items, key = { it.work.id }) { entry ->
                                    RemovableHistoryCard(
                                        entry = entry,
                                        armed = entry.work.id == armedWorkId,
                                        isFavorite = entry.work.id in state.favoriteIds,
                                        dark = dark,
                                        onArm = { armedWorkId = entry.work.id },
                                        onDisarm = { armedWorkId = null },
                                        onRemove = {
                                            armedWorkId = null
                                            viewModel.remove(entry)
                                        },
                                        onToggleFavorite = viewModel::toggleFavorite,
                                        onClick = onWorkClick,
                                    )
                                }
                            }
                        }
                        HistoryDateHeader(
                            date = state.sections[stickyIndex].date,
                            count = state.sections[stickyIndex].items.size,
                            today = today,
                            screenWidth = screenWidth,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                        if (showScrubber) {
                            HistoryDateScrubber(
                                sections = state.sections,
                                headerIndices = headerIndices,
                                gridState = gridState,
                                today = today,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                }
            }
        }
        HistoryUndoBar(
            visible = state.pendingRemovedCount > 0,
            count = state.pendingRemovedCount,
            dark = dark,
            onUndo = viewModel::undoRemove,
            onDismiss = viewModel::dismissRemovedNotice,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }

    if (showFilterSheet) {
        HistoryFilterSheet(
            selected = state.selectedRange,
            count = state.count,
            dark = dark,
            onSelect = { range ->
                armedWorkId = null
                viewModel.selectRange(range)
            },
            onClear = {
                showFilterSheet = false
                showClearConfirm = true
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = PikuColors.surface,
            title = {
                Text(
                    text = stringResource(R.string.history_clear_confirm_title),
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.history_clear_confirm_message),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clear()
                }) {
                    Text(
                        text = stringResource(R.string.history_clear),
                        color = PikuColors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(
                        text = stringResource(R.string.search_cancel),
                        color = PikuColors.textSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun HistoryTopBar(
    count: Int,
    onBack: () -> Unit,
    onFilter: () -> Unit,
    dark: Boolean,
    filtered: Boolean,
) {
    // drawBehind 不是 Composable 作用域，PikuColors 取值必须先提到外面
    val dividerColor = PikuColors.border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与详情页 / 收藏页 / 发布页头部一致：半透明玻璃底 + 0.5dp 底分隔线
            .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height - 0.5.dp.toPx()),
                    end = Offset(size.width, size.height - 0.5.dp.toPx()),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PikuBackButton(
            onClick = onBack,
            dark = dark,
            contentDescription = stringResource(R.string.back),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.history_title),
                color = PikuColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val total = stringResource(R.string.history_count, count)
            Text(
                text = total,
                color = PikuColors.textFaint,
                fontSize = 11.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    if (filtered) PikuColors.accent.copy(alpha = 0.14f) else Color.Transparent,
                )
                .clickable(onClick = onFilter),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = stringResource(R.string.history_filter_title),
                tint = if (filtered) PikuColors.accent else PikuColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * 右侧日期跳转轴：按下/拖动即跳到对应日期分组。
 * 轨道常驻但极淡，拖动时才加亮并弹出日期气泡。
 */
@Composable
private fun HistoryDateScrubber(
    sections: List<HistorySection>,
    headerIndices: List<Int>,
    gridState: LazyStaggeredGridState,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val lastIndex = sections.lastIndex
    val currentIndex = remember(headerIndices, gridState.firstVisibleItemIndex) {
        headerIndices
            .indexOfLast { it <= gridState.firstVisibleItemIndex }
            .coerceAtLeast(0)
    }
    val shownIndex = (previewIndex ?: currentIndex).coerceIn(0, lastIndex)
    val fraction = if (lastIndex <= 0) 0f else shownIndex.toFloat() / lastIndex
    val section = sections[shownIndex]
    val groupCount = stringResource(R.string.history_group_count, section.items.size)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(ScrubberHitWidth)
            .pointerInput(headerIndices) {
                val seek = { y: Float ->
                    val clamped = (y / size.height).coerceIn(0f, 1f)
                    val index = (clamped * lastIndex).roundToInt()
                    if (index != previewIndex) {
                        previewIndex = index
                        scope.launch { gridState.scrollToItem(headerIndices[index]) }
                    }
                }
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        seek(offset.y)
                    },
                    onDragEnd = {
                        dragging = false
                        previewIndex = null
                    },
                    onDragCancel = {
                        dragging = false
                        previewIndex = null
                    },
                    onVerticalDrag = { change, _ -> seek(change.position.y) },
                )
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        val trackHeight = maxHeight - ScrubberTrackInset * 2
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = ScrubberTrackInset, bottom = ScrubberTrackInset)
                .width(if (dragging) 4.dp else 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    PikuColors.textSecondary.copy(alpha = if (dragging) 0.45f else 0.20f),
                ),
        )
        val knobHeight = if (dragging) 22.dp else 14.dp
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = (-4).dp,
                    y = ScrubberTrackInset + trackHeight * fraction - knobHeight / 2,
                )
                .width(if (dragging) 4.dp else 3.dp)
                .height(knobHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(PikuColors.accent),
        )
        if (dragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = (-84).dp,
                        y = ScrubberTrackInset + trackHeight * fraction - 13.dp,
                    )
                    .width(80.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PikuColors.accent)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "${dateLabel(section.date, today)} · $groupCount",
                        color = PikuColors.surface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 删除后的撤销条：常驻，不自动消失（Snackbar 只显示几秒，来不及点）。
 * 连续删多条时可以一直点撤销，按删除的倒序一条条回来。
 */
@Composable
private fun HistoryUndoBar(
    visible: Boolean,
    count: Int,
    dark: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (dark) MenuPopupBgDark else MenuPopupBgLight)
                .border(BorderStroke(0.5.dp, PikuColors.border), RoundedCornerShape(14.dp))
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (count > 1) {
                    stringResource(R.string.history_removed_multiple, count)
                } else {
                    stringResource(R.string.history_removed)
                },
                color = PikuColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_undo),
                    color = PikuColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.search_cancel),
                    tint = PikuColors.textFaint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 长按进入待操作态：卡片缩小压暗，右上角浮出「删除」，点它才真删。
 * 不弹确认框——点了删除就是明确意图，误删交给撤销 Snackbar 兜底。
 */
@Composable
private fun RemovableHistoryCard(
    entry: HistoryItem,
    armed: Boolean,
    isFavorite: Boolean,
    dark: Boolean,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onRemove: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onClick: (Work) -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (armed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "armedCardScale",
    )
    val hapticView = LocalView.current
    val cardShape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(cardShape)
            // 压暗层画在内容之上：卡片被盖住，删除按钮仍在上面
            .drawWithContent {
                drawContent()
                if (armed) drawRect(Color.Black.copy(alpha = 0.45f))
            },
    ) {
        WorkCard(
            work = entry.work,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onClick = { if (armed) onDisarm() else onClick(it) },
            dark = dark,
            onLongClick = {
                hapticView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onArm()
            },
        )
        if (armed) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x99000000))
                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f)), RoundedCornerShape(999.dp))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.history_remove_action),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun HistoryDateHeader(
    date: LocalDate,
    count: Int,
    today: LocalDate,
    screenWidth: Dp,
    modifier: Modifier = Modifier,
) {
    // 吸顶时要盖住下方卡片，所以铺满整屏宽：网格有 contentPadding，
    // 光靠 fillMaxWidth 两边会漏出卡片（padding 不接受负值，用 offset 顶出去）
    Box(
        modifier = modifier
            .offset(x = -GridPadding)
            .width(screenWidth)
            .background(PikuColors.surface)
            .padding(start = GridPadding, end = GridPadding, top = 9.dp, bottom = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PikuColors.accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = dateLabel(date, today),
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.history_group_count, count),
                color = PikuColors.textFaint,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
@ReadOnlyComposable
private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.history_group_today)
    today.minusDays(1) -> stringResource(R.string.history_group_yesterday)
    else -> date.format(
        if (date.year == today.year) SameYearDateFormatter else FullDateFormatter,
    )
}

@Composable
private fun HistoryEmptyState(
    filtered: Boolean,
    onShowAll: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (filtered) R.string.history_empty_range else R.string.history_empty,
            ),
            color = PikuColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        if (filtered) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PikuColors.accent)
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_empty_range_action),
                    color = PikuColors.surface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterSheet(
    selected: HistoryTimeRange,
    count: Int,
    dark: Boolean,
    onSelect: (HistoryTimeRange) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark) {
        PikuSheetTitle(text = stringResource(R.string.history_filter_title))
        Spacer(Modifier.height(4.dp))
        PikuSheetSubtitle(text = stringResource(R.string.history_count, count))
        Spacer(Modifier.height(16.dp))
        val ranges = HistoryTimeRange.entries
        PikuSegmented(
            labels = ranges.map { stringResource(it.labelRes()) },
            selectedIndex = ranges.indexOf(selected),
            onSelect = { onSelect(ranges[it]) },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.history_long_press_hint),
            color = PikuColors.textFaint,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PikuColors.surfaceMuted)
                .clickable(onClick = onClear)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = null,
                tint = PikuColors.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.history_filter_clear),
                color = PikuColors.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
