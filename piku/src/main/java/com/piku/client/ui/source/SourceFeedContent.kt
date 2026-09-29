package com.piku.client.ui.source

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.key
import com.piku.client.domain.source.SourceFacetGroup
import com.piku.client.domain.source.SourceFacetStyle
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourceWorkOpen
import coil3.compose.AsyncImage
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.RankBadge
import com.piku.client.ui.common.feedThumbUrl
import com.piku.client.ui.common.labelRes
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.home.CategoryEntry
import com.piku.client.ui.home.FeedTabColors
import com.piku.client.ui.home.FeedTabItem
import com.piku.client.ui.home.GlassHeaderTopPadding
import com.piku.client.ui.home.LiquidGlassBackdrop
import com.piku.client.ui.home.SearchMenuButton
import com.piku.client.ui.home.SourceChip
import com.piku.client.ui.home.SkeletonGrid
import com.piku.client.ui.home.TabBand
import com.piku.client.ui.home.UserMenuButton
import com.piku.client.ui.home.feedScrollProgress
import com.piku.client.ui.home.reportTabBand
import com.piku.client.ui.home.scrollToTopSmart
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardPlaceholderDark

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
    onSourceClick: () -> Unit,
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
                    SourceChip(
                        labelRes = state.source.labelRes(),
                        onClick = onSourceClick,
                    )
                    Spacer(Modifier.weight(1f))
                    SearchMenuButton(onClick = onSearchClick, dark = dark)
                }
                Box(Modifier.reportTabBand(onTabBand)) {
                    SourceTabBand(
                        feeds = state.feeds,
                        selectedFeedId = state.feedId,
                        facetGroups = state.facets,
                        facetChoices = state.facetChoices,
                        onSelectFeed = viewModel::selectFeed,
                        onSelectFacet = viewModel::selectFacet,
                        tabColors = tabColors,
                    )
                }
            }
        }

        // 周期这类高频维度做成内容区标题（「日榜 ▾」）：一行文字 + 箭头，无下划线无玻璃底，
        // 不是第二层 tab；点开菜单换周期，更新节奏写在菜单项里
        state.facets.filter { it.style == SourceFacetStyle.Chips }.forEach { group ->
            FacetTitleRow(
                group = group,
                selectedId = state.facetChoices[group.id],
                onSelect = viewModel::selectFacet,
            )
        }

        when {
            // 占位流（如登录后的推荐）：明说能力未到，而不是给个空态让人以为没内容
            state.comingSoon -> CenteredMessage(text = stringResource(R.string.home_coming_soon))
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
                onToggleFavorite = viewModel::toggleFavorite,
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

    // 进详情页前过 R-18 门：判定在 [SourceWorkOpenHost] 里，与 poipiku 详情的门互不相干
    detailWork?.let { work ->
        SourceWorkOpenHost(work = work, dark = dark, onDismiss = { detailWork = null })
    }
}

/** tab 行 + 维度行，与 poipiku 的 FeedTabRow 同款：字号、选中下划线、间距、取色全一致 */
@Composable
private fun SourceTabBand(
    feeds: List<SourceFeed>,
    selectedFeedId: String,
    facetGroups: List<SourceFacetGroup>,
    facetChoices: Map<String, String>,
    onSelectFeed: (String) -> Unit,
    onSelectFacet: (groupId: String, optionId: String) -> Unit,
    tabColors: FeedTabColors?,
) {
    val colors = tabColors ?: FeedTabColors.default()
    val dropdowns = facetGroups.filter { it.style == SourceFacetStyle.Dropdown }
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
        if (dropdowns.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            dropdowns.forEach { group ->
                FacetEntry(
                    group = group,
                    selectedId = facetChoices[group.id],
                    onSelect = onSelectFacet,
                    colors = colors,
                )
            }
        }
    }

}

/** 内容类型筛选：外观与 poipiku 的分类入口一致，点开是下拉 */
@Composable
private fun FacetEntry(
    group: SourceFacetGroup,
    selectedId: String?,
    onSelect: (groupId: String, optionId: String) -> Unit,
    colors: FeedTabColors,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selected = group.options.firstOrNull { it.id == selectedId } ?: group.options.first()
    Box {
        CategoryEntry(
            label = stringResource(selected.labelRes),
            active = !selected.selectedByDefault,
            onClick = { menuOpen = true },
            colors = colors,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            group.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        menuOpen = false
                        onSelect(group.id, option.id)
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
    onToggleFavorite: (Work) -> Unit,
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
        // 榜单流：前三名给 hero 位；第 4 名起在卡片上挂名次角标
        val heroCount = if (state.ranked) minOf(RANK_HERO_COUNT, state.items.size) else 0
        if (heroCount > 0) {
            item(span = StaggeredGridItemSpan.FullLine, key = "ranking-hero") {
                RankingHero(works = state.items.take(heroCount), dark = dark, onClick = onWorkClick)
            }
        }
        val gridItems = if (heroCount > 0) state.items.drop(heroCount) else state.items
        itemsIndexed(gridItems, key = { _, work -> work.key.toString() }) { index, work ->
            WorkCard(
                work = work,
                isFavorite = work.key in state.favoriteIds,
                onToggleFavorite = onToggleFavorite,
                onClick = onWorkClick,
                dark = dark,
                rank = if (heroCount > 0) index + heroCount + 1 else null,
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


/** 榜单前三的 hero 位：1 大 2 小，名次角标金银铜。点击与网格卡片同路 */
@Composable
private fun RankingHero(
    works: List<Work>,
    dark: Boolean,
    onClick: (Work) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(212.dp)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(PikuLayout.GridGap),
    ) {
        HeroCard(
            work = works[0],
            rank = 1,
            dark = dark,
            onClick = onClick,
            modifier = Modifier.weight(1.35f),
        )
        if (works.size > 1) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PikuLayout.GridGap),
            ) {
                for (index in 1 until works.size) {
                    HeroCard(
                        work = works[index],
                        rank = index + 1,
                        dark = dark,
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    work: Work,
    rank: Int,
    dark: Boolean,
    onClick: (Work) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(PikuLayout.CardCorner))
            .background(if (dark) WorkCardBgDark else Color(0xE6FFFFFF))
            .clickable { onClick(work) },
    ) {
        AsyncImage(
            model = feedThumbUrl(work.thumbnailUrl),
            contentDescription = work.title,
            colorFilter = PikuColors.tameWhiteFilter,
            modifier = Modifier
                .fillMaxSize()
                .background(placeholder),
            contentScale = ContentScale.Crop,
        )
        // 底部压暗条：图上千奇百怪，标题永远要读得清
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = work.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = work.authorName,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RankBadge(
            rank = rank,
            large = true,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
    }
}

private const val RANK_HERO_COUNT = 3

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

/** 高频维度的内容标题行：如「日榜 ▾」。样式是页面标题（无下划线、无玻璃底），不是第二层 tab */
@Composable
private fun FacetTitleRow(
    group: SourceFacetGroup,
    selectedId: String?,
    onSelect: (groupId: String, optionId: String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selected = group.options.firstOrNull { it.id == selectedId } ?: group.options.first()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PikuLayout.ScreenInset, end = PikuLayout.ScreenInset, top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { menuOpen = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(selected.labelRes),
                color = PikuColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = PikuColors.textFaint,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            group.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(option.labelRes))
                            option.hintRes?.let { hint ->
                                Text(
                                    text = stringResource(hint),
                                    color = PikuColors.textFaint,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    },
                    onClick = {
                        menuOpen = false
                        onSelect(group.id, option.id)
                    },
                )
            }
        }
    }
}
