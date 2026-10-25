package com.piku.client.core.ui.novelcache

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkSource
import com.piku.client.core.ui.common.CardProgress
import com.piku.client.core.ui.common.CardProgressLine
import com.piku.client.core.ui.common.PikuBackButton
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuSheetSubtitle
import com.piku.client.core.ui.common.PikuSheetTitle
import com.piku.client.core.ui.common.labelRes
import com.piku.client.core.ui.sheets.ConfirmDestructiveDialog
import com.piku.client.core.ui.theme.GlassHeaderTintDark
import com.piku.client.core.ui.theme.GlassHeaderTintLight
import com.piku.client.core.ui.theme.HomeBgBottomDark
import com.piku.client.core.ui.theme.HomeBgBottomLight
import com.piku.client.core.ui.theme.HomeBgTopDark
import com.piku.client.core.ui.theme.HomeBgTopLight
import com.piku.client.core.ui.theme.LocalDarkTheme
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.core.ui.theme.PikuLayout
import com.piku.client.core.ui.theme.SoftBorderLight
import com.piku.client.core.ui.theme.WorkCardBgDark
import com.piku.client.core.ui.theme.WorkCardBorderDark

/** 离线缓存管理页：源筛选 + 搜索 + 排序 + 长按多选，卡片语言与收藏/历史一致 */
@Composable
fun NovelCacheScreen(
    onBack: () -> Unit,
    onOpenReader: (WorkSource, String) -> Unit,
    onOpenWork: (Work) -> Unit,
    viewModel: NovelCacheViewModel = hiltViewModel(),
) {
    val dark = LocalDarkTheme.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val totalBytes by viewModel.totalBytes.collectAsStateWithLifecycle()
    val filterSource by viewModel.filterSource.collectAsStateWithLifecycle()
    val query by viewModel.filterQuery.collectAsStateWithLifecycle()

    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<WorkKey>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var sortSheetOpen by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<NovelCacheItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    val filterActive = filterSource != null || query.isNotBlank()
    val sources = allItems.map { it.summary.key.source }.distinct().sortedBy { it.ordinal }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            )
            .navigationBarsPadding(),
    ) {
        if (selectionMode) {
            SelectionHeader(
                count = selected.size,
                onClose = ::exitSelection,
                onDelete = { confirmDeleteSelected = true },
            )
        } else {
            CacheHeader(
                count = allItems.size,
                totalBytes = totalBytes,
                showClear = allItems.isNotEmpty(),
                dark = dark,
                onBack = onBack,
                onClear = { confirmClear = true },
            )
            FilterBar(
                sources = sources,
                selectedSource = filterSource,
                onSelectSource = viewModel::selectSource,
                onSortClick = { sortSheetOpen = true },
                query = query,
                onQueryChange = viewModel::setQuery,
                searchOpen = searchOpen,
                onSearchOpen = { searchOpen = true },
                onSearchClose = {
                    searchOpen = false
                    viewModel.setQuery("")
                },
            )
        }

        when {
            items.isEmpty() && filterActive -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    FilteredEmpty(onClearFilters = {
                        viewModel.selectSource(null)
                        viewModel.setQuery("")
                    })
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState()
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.summary.key.toString() }) { item ->
                        CacheCard(
                            item = item,
                            dark = dark,
                            inSelection = selectionMode,
                            selectedNow = item.summary.key in selected,
                            onClick = {
                                if (selectionMode) {
                                    selected = if (item.summary.key in selected) {
                                        selected - item.summary.key
                                    } else {
                                        selected + item.summary.key
                                    }
                                } else {
                                    onOpenReader(
                                        item.summary.key.source,
                                        item.summary.key.workId,
                                    )
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selected = setOf(item.summary.key)
                                }
                            },
                            onMore = { actionItem = item },
                        )
                    }
                }
            }
        }
    }

    actionItem?.let { item ->
        CacheActionSheet(
            item = item,
            dark = dark,
            onDismiss = { actionItem = null },
            onOpenWork = {
                actionItem = null
                onOpenWork(item.summary.toWork())
            },
            onUpdate = {
                actionItem = null
                viewModel.update(item)
            },
            onDelete = {
                actionItem = null
                viewModel.delete(item.summary.key)
            },
        )
    }

    if (sortSheetOpen) {
        SortSheet(
            current = viewModel.sort.collectAsStateWithLifecycle().value,
            onSelect = {
                sortSheetOpen = false
                viewModel.selectSort(it)
            },
            onDismiss = { sortSheetOpen = false },
        )
    }

    if (confirmClear) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.novel_cache_clear_all_title),
            message = stringResource(
                R.string.novel_cache_clear_all_message,
                novelCacheSize(totalBytes),
            ),
            confirmLabel = stringResource(R.string.novel_cache_clear_all),
            onConfirm = {
                confirmClear = false
                viewModel.clearAll()
            },
            onDismiss = { confirmClear = false },
            dark = dark,
        )
    }

    if (confirmDeleteSelected) {
        val picked = allItems.filter { it.summary.key in selected }
        ConfirmDestructiveDialog(
            title = stringResource(R.string.novel_cache_delete_selected_title),
            message = stringResource(
                R.string.novel_cache_delete_selected_message,
                picked.size,
                novelCacheSize(picked.sumOf { it.summary.byteSize }),
            ),
            confirmLabel = stringResource(R.string.novel_cache_menu_delete),
            onConfirm = {
                confirmDeleteSelected = false
                viewModel.deleteAll(selected)
                exitSelection()
            },
            onDismiss = { confirmDeleteSelected = false },
            dark = dark,
        )
    }
}

@Composable
private fun CacheHeader(
    count: Int,
    totalBytes: Long,
    showClear: Boolean,
    dark: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    HeaderShell(dark = dark) {
        PikuBackButton(
            onClick = onBack,
            dark = dark,
            contentDescription = stringResource(R.string.back),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.novel_cache_title),
                color = PikuColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = stringResource(
                    R.string.novel_cache_page_subtitle,
                    count,
                    novelCacheSize(totalBytes),
                ),
                color = PikuColors.textFaint,
                fontSize = 11.sp,
            )
        }
        if (showClear) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = stringResource(R.string.novel_cache_clear_all),
                    tint = PikuColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    HeaderShell(dark = dark) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.back),
                tint = PikuColors.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(R.string.novel_cache_selected_count, count),
            color = PikuColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp),
        )
        Text(
            text = stringResource(R.string.novel_cache_delete_selected),
            color = PikuColors.error,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun HeaderShell(
    dark: Boolean,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(PikuColors.border.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun FilterBar(
    sources: List<WorkSource>,
    selectedSource: WorkSource?,
    onSelectSource: (WorkSource?) -> Unit,
    onSortClick: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    Column(Modifier.fillMaxWidth()) {
        if (searchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (dark) WorkCardBgDark else PikuColors.surfaceSoft)
                        .height(36.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = PikuColors.textFaint,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.novel_cache_search_hint),
                                color = PikuColors.textFaint,
                                fontSize = 13.sp,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = PikuColors.textPrimary),
                            cursorBrush = SolidColor(PikuColors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                FilterIcon(Icons.Outlined.Close) {
                    onQueryChange("")
                    onSearchClose()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SourceChip(
                        label = stringResource(R.string.novel_cache_filter_all),
                        selected = selectedSource == null,
                        onClick = { onSelectSource(null) },
                    )
                    sources.forEach { source ->
                        SourceChip(
                            label = stringResource(source.labelRes()),
                            selected = selectedSource == source,
                            onClick = { onSelectSource(source) },
                        )
                    }
                }
                FilterIcon(Icons.Outlined.Search, onClick = onSearchOpen)
                FilterIcon(Icons.Outlined.Sort, onClick = onSortClick)
            }
        }
    }
}

@Composable
private fun FilterIcon(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PikuColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) PikuColors.controlAccent else Color.Transparent)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(0.5.dp, PikuColors.border, CircleShape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) PikuColors.surface else PikuColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CacheCard(
    item: NovelCacheItem,
    dark: Boolean,
    /** null = 不在多选态；true/false = 多选态下的勾选状态 */
    inSelection: Boolean,
    selectedNow: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val shape = RoundedCornerShape(PikuLayout.CardCorner)
    val now = remember { System.currentTimeMillis() }
    val summary = item.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (dark) 0.dp else 4.dp,
                shape = shape,
                ambientColor = Color(0x14000000),
                spotColor = Color(0x24000000),
            )
            .clip(shape)
            .background(if (dark) WorkCardBgDark else Color(0xE6FFFFFF))
            .border(
                BorderStroke(
                    0.5.dp,
                    when {
                        inSelection && selectedNow -> PikuColors.controlAccent.copy(alpha = 0.55f)
                        dark -> WorkCardBorderDark
                        else -> SoftBorderLight
                    },
                ),
                shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(PikuLayout.CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inSelection) {
                SelectionCircle(selected = selectedNow)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(summary.key.source.labelRes()),
                color = PikuColors.textFaint,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (!inSelection) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = stringResource(R.string.novel_cache_more),
                        tint = PikuColors.textFaint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = summary.title,
            color = PikuColors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = summary.authorName,
            color = PikuColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (summary.chapterCount > 1) {
                stringResource(
                    R.string.novel_cache_row_meta_chapters,
                    summary.chapterCount,
                    novelCacheSize(summary.byteSize),
                    novelCacheTime(now, summary.fetchedAt),
                )
            } else {
                stringResource(
                    R.string.novel_cache_row_meta_single,
                    novelCacheSize(summary.byteSize),
                    novelCacheTime(now, summary.fetchedAt),
                )
            },
            color = PikuColors.textFaint,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.progressPercent > 0) {
            Spacer(Modifier.height(8.dp))
            CardProgressLine(
                progress = CardProgress(
                    fraction = item.progressPercent / 100f,
                    label = if (item.progressPercent >= 100) {
                        stringResource(R.string.novel_cache_finished)
                    } else {
                        "${item.progressPercent}%"
                    },
                ),
                dark = dark,
                labelFontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun SelectionCircle(selected: Boolean) {
    val dark = LocalDarkTheme.current
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) PikuColors.controlAccent else Color.Transparent)
            .border(
                0.5.dp,
                if (selected) PikuColors.controlAccent else PikuColors.textFaint,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = if (dark) WorkCardBgDark else Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.DownloadForOffline,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.novel_cache_empty),
            color = PikuColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.novel_cache_empty_hint),
            color = PikuColors.textFaint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun FilteredEmpty(onClearFilters: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.novel_cache_empty_filtered),
            color = PikuColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.novel_cache_clear_filters),
            color = PikuColors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClearFilters)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CacheActionSheet(
    item: NovelCacheItem,
    dark: Boolean,
    onDismiss: () -> Unit,
    onOpenWork: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = item.summary
    val now = remember { System.currentTimeMillis() }
    val source = stringResource(summary.key.source.labelRes())
    val meta = if (summary.chapterCount > 1) {
        stringResource(
            R.string.novel_cache_sheet_item_meta,
            source,
            summary.chapterCount,
            novelCacheSize(summary.byteSize),
            novelCacheTime(now, summary.fetchedAt),
        )
    } else {
        stringResource(
            R.string.novel_cache_sheet_item_meta_single,
            source,
            novelCacheSize(summary.byteSize),
            novelCacheTime(now, summary.fetchedAt),
        )
    }
    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark) {
        PikuSheetTitle(summary.title)
        Spacer(Modifier.height(4.dp))
        PikuSheetSubtitle(meta)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = PikuColors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
        SheetAction(
            icon = Icons.AutoMirrored.Outlined.Article,
            label = stringResource(R.string.novel_cache_menu_open_work),
            onClick = onOpenWork,
        )
        SheetAction(
            icon = Icons.Outlined.Refresh,
            label = stringResource(R.string.novel_cache_menu_update),
            onClick = onUpdate,
        )
        SheetAction(
            icon = Icons.Outlined.DeleteOutline,
            label = stringResource(R.string.novel_cache_menu_delete),
            tint = PikuColors.error,
            onClick = onDelete,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: NovelCacheSort,
    onSelect: (NovelCacheSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark) {
        PikuSheetTitle(stringResource(R.string.novel_cache_sort_label))
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = PikuColors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
        SortOption(
            label = stringResource(R.string.novel_cache_sort_recent),
            selected = current == NovelCacheSort.RECENT,
            onClick = { onSelect(NovelCacheSort.RECENT) },
        )
        SortOption(
            label = stringResource(R.string.novel_cache_sort_name),
            selected = current == NovelCacheSort.TITLE,
            onClick = { onSelect(NovelCacheSort.TITLE) },
        )
        SortOption(
            label = stringResource(R.string.novel_cache_sort_size),
            selected = current == NovelCacheSort.SIZE,
            onClick = { onSelect(NovelCacheSort.SIZE) },
        )
    }
}

@Composable
private fun SortOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = PikuColors.controlAccent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = PikuColors.textPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}
