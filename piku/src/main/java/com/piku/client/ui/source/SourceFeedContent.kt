package com.piku.client.ui.source

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.domain.source.SourceFacet
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.home.CategoryEntry
import com.piku.client.ui.home.FeedTabColors
import com.piku.client.ui.home.FeedTabItem
import com.piku.client.ui.home.GlassHeaderTopPadding
import com.piku.client.ui.home.LiquidGlassBackdrop
import com.piku.client.ui.home.SearchMenuButton
import com.piku.client.ui.home.SkeletonGrid
import com.piku.client.ui.home.TabBand
import com.piku.client.ui.home.UserMenuButton
import com.piku.client.ui.home.feedScrollProgress
import com.piku.client.ui.home.reportTabBand
import com.piku.client.ui.home.scrollToTopSmart
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout

@Composable
internal fun SourceFeedContent(
    dark: Boolean,
    isScrolling: MutableState<Boolean>,
    avatarUrl: String?,
    menuEnabled: Boolean,
    hasCustomBackground: Boolean,
    drawerIsOpen: Boolean,
    tabColors: FeedTabColors?,
    onTabBand: (TabBand) -> Unit,
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: SourceFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val gridState = rememberLazyStaggeredGridState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detailWork by remember { mutableStateOf<Work?>(null) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }.collect { isScrolling.value = it }
    }

    // 触底加载靠列表状态判断，不放在 item 里：item 进出组合会反复触发，失败后就成了重试风暴
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (total > 0 && last >= total - 3) viewModel.loadMore()
            }
    }

    // 头部底衬的退场与进度与 poipiku 的 GlassHeader 同款算法，只是数据来自本页网格
    val atTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
    }
    val scrollProgress = remember(gridState) { { gridState.feedScrollProgress() } }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { gridState.scrollToTopSmart(scope) })
                }
                .statusBarsPadding()
                .padding(top = GlassHeaderTopPadding),
        ) {
            LiquidGlassBackdrop(
                dark = dark,
                isScrolling = isScrolling,
                drawerIsOpen = drawerIsOpen,
                modifier = Modifier.matchParentSize(),
                translucent = hasCustomBackground,
                progress = scrollProgress,
                atTop = atTop,
            )
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PikuLayout.NavRowInset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserMenuButton(
                        avatarUrl = avatarUrl,
                        onMenuClick = onOpenDrawer,
                        enabled = menuEnabled,
                        dark = dark,
                    )
                    Spacer(Modifier.weight(1f))
                    SearchMenuButton(onClick = onSearchClick, dark = dark)
                }
                Box(Modifier.reportTabBand(onTabBand)) {
                    SourceTabBand(
                        feeds = state.feeds,
                        selectedFeedId = state.feedId,
                        facets = state.facets,
                        selectedFacetId = state.facetId,
                        onSelectFeed = viewModel::selectFeed,
                        onSelectFacet = viewModel::selectFacet,
                        tabColors = tabColors,
                    )
                }
            }
        }

        when {
            state.needLogin -> CenteredMessage(text = stringResource(R.string.home_follow_login))
            state.loading && state.items.isEmpty() -> SkeletonGrid(dark = dark)
            state.failed && state.items.isEmpty() -> CenteredMessage(
                text = stringResource(R.string.home_error_network),
                action = stringResource(R.string.common_retry),
                onAction = viewModel::retry,
            )
            state.items.isEmpty() -> CenteredMessage(stringResource(R.string.home_empty))
            else -> SourceGrid(
                state = state,
                gridState = gridState,
                dark = dark,
                onRetryLoadMore = viewModel::retryLoadMore,
                onWorkClick = { work ->
                    when (val open = viewModel.open(work)) {
                        is SourceWorkOpen.External -> runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(open.url)),
                            )
                        }
                        SourceWorkOpen.InAppViewer -> detailWork = work
                    }
                },
            )
        }
    }

    // 进详情页前过 R-18 门：判定在这一侧，与 poipiku 详情的门互不相干
    detailWork?.let { work ->
        if (work.r18 && !state.adultEnabled) {
            AlertDialog(
                onDismissRequest = { detailWork = null },
                title = { Text(stringResource(R.string.detail_gate_adult_title)) },
                text = { Text(stringResource(R.string.detail_gate_adult_body)) },
                confirmButton = {
                    Text(
                        text = stringResource(R.string.detail_fullscreen_close),
                        color = PikuColors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { detailWork = null }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                },
            )
        } else {
            SourceWorkDetailDialog(
                work = work,
                dark = dark,
                onDismiss = { detailWork = null },
            )
        }
    }
}

/** tab 行 + 行尾筛选入口，与 poipiku 的 FeedTabRow 同款：字号、选中下划线、间距、取色全一致 */
@Composable
private fun SourceTabBand(
    feeds: List<SourceFeed>,
    selectedFeedId: String,
    facets: List<SourceFacet>,
    selectedFacetId: String?,
    onSelectFeed: (String) -> Unit,
    onSelectFacet: (String) -> Unit,
    tabColors: FeedTabColors?,
) {
    val colors = tabColors ?: FeedTabColors.default()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = PikuLayout.ScreenInset,
                end = PikuLayout.ScreenInset,
                top = 4.dp,
                bottom = 6.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        feeds.forEach { feed ->
            FeedTabItem(
                text = stringResource(feed.labelRes),
                active = feed.id == selectedFeedId,
                onClick = { onSelectFeed(feed.id) },
                colors = colors,
            )
        }
        if (facets.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            FacetEntry(
                facets = facets,
                selectedId = selectedFacetId,
                onSelect = onSelectFacet,
                colors = colors,
            )
        }
    }
}

/** 内容类型筛选：外观与 poipiku 的分类入口一致，点开是下拉 */
@Composable
private fun FacetEntry(
    facets: List<SourceFacet>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    colors: FeedTabColors,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selected = facets.firstOrNull { it.id == selectedId } ?: facets.first()
    Box {
        CategoryEntry(
            label = stringResource(selected.labelRes),
            active = !selected.selectedByDefault,
            onClick = { menuOpen = true },
            colors = colors,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            facets.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        menuOpen = false
                        onSelect(option.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceGrid(
    state: SourceFeedViewModel.UiState,
    gridState: LazyStaggeredGridState,
    dark: Boolean,
    onRetryLoadMore: () -> Unit,
    onWorkClick: (Work) -> Unit,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    LazyVerticalStaggeredGrid(
        columns = if (isTablet) StaggeredGridCells.Adaptive(220.dp) else StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = PikuLayout.ScreenInset,
            end = PikuLayout.ScreenInset,
            top = 10.dp,
            bottom = 80.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(PikuLayout.GridGap),
        verticalItemSpacing = PikuLayout.GridGap,
    ) {
        items(state.items, key = { it.id }) { work ->
            WorkCard(
                work = work,
                isFavorite = false,
                // pixiv 收藏要等 ID 命名空间做完，这里刻意留空
                onToggleFavorite = {},
                onClick = onWorkClick,
                dark = dark,
            )
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loadingMore -> LoaderDots(dark = dark)
                    state.loadMoreFailed -> Text(
                        text = stringResource(R.string.home_load_more_failed),
                        color = PikuColors.accent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onRetryLoadMore)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    state.endReached -> Text(
                        text = stringResource(R.string.home_no_more),
                        color = PikuColors.textFaint,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = text, color = PikuColors.textSecondary, fontSize = 13.sp)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = action,
                    color = PikuColors.accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
