package com.piku.client.ui.source

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import com.piku.client.R
import com.piku.client.data.remote.GitHubRelease
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.model.key
import com.piku.client.domain.source.SourceFacetGroup
import com.piku.client.domain.source.SourceFacetStyle
import com.piku.client.domain.source.isVisibleWith
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourceWorkOpen
import coil3.compose.AsyncImage
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.RankBadge
import com.piku.client.ui.common.feedThumbUrl
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.home.BackToTopFab
import com.piku.client.ui.home.CategoryEntry
import com.piku.client.ui.home.FAB_SHOW_AFTER_ITEMS
import com.piku.client.ui.home.FeedTabColors
import com.piku.client.ui.home.FeedTabItem
import com.piku.client.ui.home.GlassHeaderTopPadding
import com.piku.client.ui.home.LOAD_MORE_NEAR_END
import com.piku.client.ui.home.background.LiquidGlassBackdrop
import com.piku.client.ui.home.RefreshNoticeBar
import com.piku.client.ui.home.SearchMenuButton
import com.piku.client.ui.home.SkeletonGrid
import com.piku.client.ui.home.background.TabBand
import com.piku.client.ui.home.ThumbnailPrefetchEffect
import com.piku.client.ui.home.UpdateBannerBar
import com.piku.client.ui.home.UserMenuButton
import com.piku.client.ui.home.feedScrollProgress
import com.piku.client.ui.home.reportTabBand
import com.piku.client.ui.home.scrollToTopSmart
import com.piku.client.ui.navigation.sharedWorkBounds
import com.piku.client.ui.navigation.workSharedKey
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardPlaceholderDark

@OptIn(ExperimentalMaterial3Api::class)
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
    updateBanner: GitHubRelease?,
    onOpenUpdate: () -> Unit,
    onDismissUpdateBanner: () -> Unit,
    /** 作品点击的通用分流去向：NativeDetail/External 直接交它，InAppViewer 过完 R-18 门也交它 */
    onOpenWork: (Work) -> Unit,
    onLoginClick: (String) -> Unit = {},
    viewModel: SourceFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val gridState = rememberLazyStaggeredGridState()
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
                if (total > 0 && last >= total - LOAD_MORE_NEAR_END) viewModel.loadMore()
            }
    }

    // 头部底衬的退场与进度与 poipiku 的 GlassHeader 同款算法，只是数据来自本页网格
    val atTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
    }
    val scrollProgress = remember(gridState) { { gridState.feedScrollProgress() } }

    val showFab = remember {
        derivedStateOf { gridState.firstVisibleItemIndex > FAB_SHOW_AFTER_ITEMS }
    }
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        snapshotFlow { isScrolling.value to currentState.refreshNotice }
            .distinctUntilChanged()
            .collect { (scrolling, notice) ->
                if (scrolling && notice == 0) viewModel.dismissRefreshNotice()
            }
    }

    val showFeedTabs = state.feeds.count { !it.comingSoon } > 1
    // 当前流生效的维度组：tab 行最右挂下拉，下方维度行放片选；带显示条件的组按当前选中态整组隐藏
    val currentFacets = state.facets.filter {
        (it.feedId == null || it.feedId == state.feedId) && it.isVisibleWith(state.facetChoices)
    }

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
                        showFeedTabs = showFeedTabs,
                        selectedFeedId = state.feedId,
                        onSelectFeed = viewModel::selectFeed,
                        tabColors = tabColors,
                        dark = dark,
                        dropdownFacets = currentFacets.filter { it.style == SourceFacetStyle.Dropdown },
                        facetChoices = state.facetChoices,
                        onSelectFacet = viewModel::selectFacet,
                    )
                }
            }
        }

        // 维度行：剩下的片选不进 tab 行（tab 多了会挤掉它），只随所属流出现
        FacetRow(
            groups = currentFacets,
            choices = state.facetChoices,
            onSelect = viewModel::selectFacet,
        )

        Box(Modifier.fillMaxSize()) {
            when {
                // 占位流（如登录后的推荐）：明说能力未到，而不是给个空态让人以为没内容
                state.comingSoon -> CenteredMessage(text = stringResource(R.string.home_coming_soon))
                state.needLogin -> CenteredMessage(
                    text = stringResource(state.loginPromptRes),
                    // 该源声明了登录页才给按钮；没声明的源（暂未接入登录）只说明情况
                    action = state.loginRoute?.let { stringResource(R.string.login_button) },
                    onAction = state.loginRoute?.let { route -> { onLoginClick(route) } },
                )
                state.loading && state.items.isEmpty() -> SkeletonGrid(dark = dark)
                state.failed && state.items.isEmpty() -> CenteredMessage(
                    text = stringResource(R.string.home_error_network),
                    action = stringResource(R.string.common_retry),
                    onAction = viewModel::retry,
                )
                state.items.isEmpty() -> CenteredMessage(stringResource(R.string.home_empty))
                else -> Box(Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = state.loading,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SourceGrid(
                            state = state,
                            gridState = gridState,
                            dark = dark,
                            onRetryLoadMore = viewModel::retryLoadMore,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onWorkClick = { work ->
                                // InAppViewer 两段式：先过宿主的 R-18 门，过门后仍经 onOpenWork 进共用壳；
                                // 其余去向（NativeDetail/External）直接交给通用分流
                                if (viewModel.open(work) == SourceWorkOpen.InAppViewer) {
                                    detailWork = work
                                } else {
                                    onOpenWork(work)
                                }
                            },
                        )
                    }
                    RefreshNoticeOverlay(
                        notice = state.refreshNotice,
                        dark = dark,
                        onDismiss = viewModel::dismissRefreshNotice,
                        onGoTop = { gridState.scrollToTopSmart(scope) },
                    )
                    BackToTopFab(
                        showFab = showFab,
                        isScrolling = isScrolling,
                        onGoTop = { gridState.scrollToTopSmart(scope) },
                        dark = dark,
                    )
                }
            }
            updateBanner?.let { release ->
                UpdateBannerBar(
                    release = release,
                    onOpen = onOpenUpdate,
                    onDismiss = onDismissUpdateBanner,
                    dark = dark,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }

    // InAppViewer 的第二段：R-18 门判定在 [SourceWorkOpenHost] 里，过门后导航进共用详情壳
    detailWork?.let { work ->
        SourceWorkOpenHost(
            work = work,
            dark = dark,
            onDismiss = { detailWork = null },
            onOpenInApp = onOpenWork,
        )
    }
}

/** tab 行：流靠左，内容类型筛选挂行尾最右；周期片选在下方维度行，tab 再多也不会挤掉它 */
@Composable
private fun SourceTabBand(
    feeds: List<SourceFeed>,
    showFeedTabs: Boolean,
    selectedFeedId: String,
    onSelectFeed: (String) -> Unit,
    tabColors: FeedTabColors?,
    dark: Boolean,
    dropdownFacets: List<SourceFacetGroup>,
    facetChoices: Map<String, String>,
    onSelectFacet: (groupId: String, optionId: String) -> Unit,
) {
    val colors = tabColors ?: FeedTabColors.default()
    // 占位流（能力未到）不上 tab 行：首页不放假东西；只剩一条流时 tab 项整个收起
    val visibleFeeds = if (showFeedTabs) feeds.filterNot { it.comingSoon } else emptyList()
    if (visibleFeeds.isEmpty() && dropdownFacets.isEmpty()) return
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
        visibleFeeds.forEach { feed ->
            FeedTabItem(
                text = stringResource(feed.labelRes),
                active = feed.id == selectedFeedId,
                onClick = { onSelectFeed(feed.id) },
                colors = colors,
            )
        }
        if (dropdownFacets.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            dropdownFacets.forEach { group ->
                FacetEntry(
                    group = group,
                    selectedId = facetChoices[group.id],
                    dark = dark,
                    onSelect = onSelectFacet,
                    colors = colors,
                )
            }
        }
    }
}

/** 内容类型筛选：外观与 tab 同排同款，点开是主题化的浮层面板 */
@Composable
private fun FacetEntry(
    group: SourceFacetGroup,
    selectedId: String?,
    dark: Boolean,
    onSelect: (groupId: String, optionId: String) -> Unit,
    colors: FeedTabColors,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // 入口自己的高度给面板当落点偏移：面板 top = 入口 bottom + 6dp
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    val selected = group.options.firstOrNull { it.id == selectedId } ?: group.options.first()
    Box(Modifier.onSizeChanged { anchorHeightPx = it.height }) {
        Box(Modifier.onSizeChanged { anchorHeightPx = it.height }) {
            CategoryEntry(
                label = stringResource(selected.labelRes),
                active = !selected.selectedByDefault,
                onClick = { menuOpen = true },
                colors = colors,
            )
            if (menuOpen) {
                FacetMenuPopup(
                    options = group.options,
                    selectedId = selected.id,
                    dark = dark,
                    anchorHeightPx = anchorHeightPx,
                    onSelect = { optionId ->
                        menuOpen = false
                        onSelect(group.id, optionId)
                    },
                    onDismiss = { menuOpen = false },
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
    // 榜单流：前三名给 hero 位；第 4 名起在卡片上挂名次角标
    val heroCount = if (state.ranked) minOf(RANK_HERO_COUNT, state.items.size) else 0
    val gridItems = if (heroCount > 0) state.items.drop(heroCount) else state.items
    ThumbnailPrefetchEffect(works = gridItems, gridState = gridState, itemOffset = heroCount)
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
        if (heroCount > 0) {
            item(span = StaggeredGridItemSpan.FullLine, key = "ranking-hero") {
                RankingHero(works = state.items.take(heroCount), dark = dark, onClick = onWorkClick)
            }
        }
        itemsIndexed(gridItems, key = { _, work -> work.key.toString() }) { index, work ->
            // 小说是另一种作品：竖版封面 + 字数，没有页数与真实比例
            if (work.kind == WorkKind.NOVEL) {
                NovelWorkCard(
                    work = work,
                    onToggleFavorite = onToggleFavorite,
                    onClick = onWorkClick,
                    dark = dark,
                )
            } else if (state.proportional) {
                // 源给了原作宽高：按真实比例排版，竖图不再被裁成方图
                ProportionalWorkCard(
                    work = work,
                    onToggleFavorite = onToggleFavorite,
                    onClick = onWorkClick,
                    dark = dark,
                )
            } else {
                WorkCard(
                    work = work,
                    isFavorite = work.key in state.favoriteIds,
                    onToggleFavorite = onToggleFavorite,
                    onClick = onWorkClick,
                    dark = dark,
                    rank = if (heroCount > 0) index + heroCount + 1 else null,
                )
            }
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
                .sharedWorkBounds(workSharedKey(work.authorId, work.id))
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

/** 刷新提示条：与 poipiku 壳同款，挂在内容区顶部；独立成函数避免外层 Column 的作用域劫持 AnimatedVisibility */
@Composable
private fun BoxScope.RefreshNoticeOverlay(
    notice: Int?,
    dark: Boolean,
    onDismiss: () -> Unit,
    onGoTop: () -> Unit,
) {
    AnimatedVisibility(
        visible = notice != null,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
    ) {
        RefreshNoticeBar(
            count = notice ?: 0,
            onDismiss = onDismiss,
            onGoTop = onGoTop,
            dark = dark,
        )
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

/** 维度行：只剩周期这类片选一击直达；下拉筛选已挂到 tab 行最右 */
@Composable
private fun FacetRow(
    groups: List<SourceFacetGroup>,
    choices: Map<String, String>,
    onSelect: (groupId: String, optionId: String) -> Unit,
) {
    val chips = groups.filter { it.style == SourceFacetStyle.Chips }
    if (chips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PikuLayout.ScreenInset, end = PikuLayout.ScreenInset, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { group ->
            group.options.forEach { option ->
                FacetChip(
                    label = stringResource(option.labelRes),
                    selected = choices[group.id] == option.id,
                    onClick = { onSelect(group.id, option.id) },
                )
            }
        }
    }
}

/** 维度片选：药丸样式，与 tab 的下划线样式区分层级 */
@Composable
private fun FacetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) PikuColors.accent else PikuColors.textFaint,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PikuColors.accent.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
