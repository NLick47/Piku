package com.piku.client.core.ui.shell
import com.piku.client.source.pixiv.ui.novel.NovelWorkCard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import com.piku.client.R
import com.piku.client.core.data.GitHubRelease
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.key
import com.piku.client.core.source.SourceFacetGroup
import com.piku.client.core.source.SourceFacetStyle
import com.piku.client.core.source.isVisibleWith
import com.piku.client.core.source.SourceFeed
import com.piku.client.core.source.SourceListLayout
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceWorkOpen
import coil3.compose.AsyncImage
import com.piku.client.core.ui.common.FavoriteHeartBurst
import com.piku.client.core.ui.common.EffectOnChange
import com.piku.client.core.ui.common.LoaderDots
import com.piku.client.core.ui.common.MenuPopup
import com.piku.client.core.ui.common.MenuPopupItem
import com.piku.client.core.ui.common.RankMedal
import com.piku.client.core.ui.common.TextWorkBlurb
import com.piku.client.core.ui.common.TextWorkCard
import com.piku.client.core.ui.common.heroFeedThumbUrl
import com.piku.client.core.ui.common.WorkCard
import com.piku.client.core.ui.shell.BackToTopFab
import com.piku.client.core.ui.shell.CategoryEntry
import com.piku.client.core.ui.shell.FAB_SHOW_AFTER_ITEMS
import com.piku.client.core.ui.shell.FeedTabColors
import com.piku.client.core.ui.shell.FeedTabItem
import com.piku.client.core.ui.shell.GlassHeaderTopPadding
import com.piku.client.core.ui.shell.LOAD_MORE_NEAR_END
import com.piku.client.core.ui.background.LiquidGlassBackdrop
import com.piku.client.core.ui.shell.RefreshNoticeBar
import com.piku.client.core.ui.shell.SearchMenuButton
import com.piku.client.core.ui.shell.SkeletonGrid
import com.piku.client.core.ui.background.TabBand
import com.piku.client.core.ui.shell.ThumbnailPrefetchEffect
import com.piku.client.core.ui.shell.UpdateBannerBar
import com.piku.client.core.ui.shell.UserMenuButton
import com.piku.client.core.ui.shell.feedScrollProgress
import com.piku.client.core.ui.shell.reportTabBand
import com.piku.client.core.ui.shell.scrollToTopSmart
import com.piku.client.app.navigation.sharedWorkBounds
import com.piku.client.app.navigation.workSharedKey
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.ui.text.style.TextAlign
import com.piku.client.core.ui.theme.GlassBarBgDark
import com.piku.client.core.ui.theme.GlassBarBgLight
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.core.ui.theme.PikuLayout
import com.piku.client.core.ui.theme.RankGoldBorder
import com.piku.client.core.ui.theme.ShadowAmbient
import com.piku.client.core.ui.theme.ShadowSpot
import com.piku.client.core.ui.theme.SoftBorderDark
import com.piku.client.core.ui.theme.SoftBorderLight
import com.piku.client.core.ui.theme.WorkCardBgDark
import com.piku.client.core.ui.theme.WorkCardPlaceholderDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceFeedContent(
    dark: Boolean,
    isScrolling: MutableState<Boolean>,
    /** 由宿主持有的网格状态：页头的头图视差读的就是它，页面自己建一颗的话宿主的视差源读不到滚动 */
    gridState: LazyStaggeredGridState,
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
    /** 圈层增删的唯一入口：宿主把它接到圈层管理页（路由），本壳不再自带选择面板 */
    onManageScopes: (WorkSource) -> Unit = {},
    viewModel: SourceFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
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
    // 换流/换维度/换源后内容是整批替换的：滚动位置必须归零，否则停在旧列表的 offset 上，
    // 新榜单从中间开始看（首屏加载中时 scrollToItem 也无副作用）
    EffectOnChange(keyOf = { Triple(state.source, state.feedId, state.facetChoices) }) {
        if (!atTop) gridState.scrollToItem(0)
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
    // 折叠式头部（源声明）：排序与筛选收成固定头里的一颗摘要控件。
    // 首屏可见（改设定先看得见），下滑阅读时收起让位，任意上滑即滑回——
    // 改排序/筛选几乎总发生在往回翻的时候，控件在用户打算操作的那一刻出现。
    val compact = state.compactControls
    val chipGroups = currentFacets.filter { it.style == SourceFacetStyle.Chips }
    val collapsibleControls = compact || chipGroups.isNotEmpty()
    var summaryVisible by remember { mutableStateOf(true) }
    if (collapsibleControls) {
        // 键带上控件组成：换流/换筛选后这一行重新出现时必须先摆回可见（此时列表已归零）
        LaunchedEffect(gridState, collapsibleControls) {
            summaryVisible = true
            var lastFirstIndex = 0
            var lastFirstOffset = 0
            var downRun = 0
            snapshotFlow {
                val info = gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                info
            }
                .distinctUntilChanged()
                .collect { (index, offset) ->
                    val scrolledDown = index > lastFirstIndex || (index == lastFirstIndex && offset > lastFirstOffset + 4)
                    val scrolledUp = index < lastFirstIndex || (index == lastFirstIndex && offset < lastFirstOffset - 4)
                    if (scrolledDown) {
                        // 顶部附近不触发：一进列表就收会让首屏的改筛选变两步
                        downRun += if (index == 0) 0 else 1
                        if (downRun >= 3 && index > 0) summaryVisible = false
                    } else if (scrolledUp) {
                        downRun = 0
                        summaryVisible = true
                    }
                    lastFirstIndex = index
                    lastFirstOffset = offset
                }
        }
    }
    // 只列非默认的筛选：默认档不占摘要，摘要才有信息量
    val activeFilters = currentFacets.mapNotNull { group ->
        val chosen = state.facetChoices[group.id]
        val defaultId = group.options.firstOrNull { it.selectedByDefault }?.id ?: group.options.firstOrNull()?.id
        if (chosen == null || chosen == defaultId) null
        else group to chosen
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { gridState.scrollToTopSmart(scope) })
                },
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = GlassHeaderTopPadding),
            ) {
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
                if (compact) {
                    // 摘要行在固定头里：高度动画收放，列表视口随之变化但不重排内容。
                    // 空态/失败态也在这个头里，筛出空结果时用户仍能改回来，不会被困住
                    AnimatedVisibility(
                        visible = summaryVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        SummaryRow(
                            feeds = state.feeds,
                            selectedFeedId = state.feedId,
                            filters = activeFilters,
                            // 圈层段：有订阅才显示折叠条；空 = 还没订阅（引导态另给入口）
                            scopes = state.scopes,
                            scopeLabels = state.scopeLabels,
                            activeScope = state.activeScope,
                            onPickScope = viewModel::selectScope,
                            onManageScopes = { onManageScopes(state.source) },
                            onPickSort = viewModel::selectFeed,
                            onPickFilter = viewModel::selectFacet,
                            dark = dark,
                            onClearFilter = { groupId ->
                                val group = currentFacets.firstOrNull { it.id == groupId } ?: return@SummaryRow
                                val defaultId = group.options.firstOrNull { it.selectedByDefault }?.id
                                    ?: group.options.firstOrNull()?.id
                                if (defaultId != null) viewModel.selectFacet(group.id, defaultId)
                            },
                        )
                    }
                }
                if (!compact) {
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
        }

        // 维度行：剩下的片选不进 tab 行（tab 多了会挤掉它），只随所属流出现；
        // 带片选的流（pixiv 榜单/关注）这一行下滑时收起，任意上滑滑回
        if (!compact && chipGroups.isNotEmpty()) {
            AnimatedVisibility(
                visible = summaryVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                FacetRow(
                    groups = currentFacets,
                    choices = state.facetChoices,
                    onSelect = viewModel::selectFacet,
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                // 圈层式首页还没订阅：引导去选圈层，而不是给一屏全站作品
                state.needsScopeSetup -> ScopeSetupGuide(onAdd = { onManageScopes(state.source) })
                // 占位流（如登录后的能力未到）：明说能力未到，而不是给个空态让人以为没内容
                state.comingSoon -> CenteredMessage(text = stringResource(R.string.home_coming_soon))
                state.needLogin -> CenteredMessage(
                    text = stringResource(state.loginPromptRes),
                    // 该源声明了登录页才给按钮；没声明的源（暂未接入登录）只说明情况
                    action = state.loginRoute?.let { stringResource(R.string.login_button) },
                    onAction = state.loginRoute?.let { route -> { onLoginClick(route) } },
                )
                state.loading && state.items.isEmpty() -> SkeletonGrid(
                    dark = dark,
                    singleColumn = state.listLayout == SourceListLayout.List,
                )
                state.failed && state.items.isEmpty() -> CenteredMessage(
                    text = stringResource(R.string.home_error_network),
                    action = stringResource(R.string.common_retry),
                    onAction = viewModel::retry,
                )
                state.items.isEmpty() -> CenteredMessage(text = stringResource(R.string.home_empty))
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

@Composable
private fun SummaryRow(
    feeds: List<SourceFeed>,
    selectedFeedId: String,
    filters: List<Pair<SourceFacetGroup, String>>,
    scopes: List<SourceScope>,
    scopeLabels: Map<String, String>,
    activeScope: SourceScope?,
    onPickScope: (SourceScope) -> Unit,
    onManageScopes: () -> Unit,
    onPickSort: (String) -> Unit,
    onPickFilter: (groupId: String, optionId: String) -> Unit,
    dark: Boolean,
    onClearFilter: (groupId: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PikuLayout.ScreenInset, end = PikuLayout.ScreenInset, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (scopes.isNotEmpty()) {
            ScopeFoldPill(
                scopes = scopes,
                labels = scopeLabels,
                active = activeScope,
                onPick = onPickScope,
                onManage = onManageScopes,
                dark = dark,
            )
        }
        Spacer(Modifier.weight(1f))
        SortPill(
            feeds = feeds.filter { !it.comingSoon },
            selectedId = selectedFeedId,
            label = feeds.firstOrNull { it.id == selectedFeedId }?.let { stringResource(it.labelRes) }.orEmpty(),
            onPick = onPickSort,
        )
        filters.forEach { entry ->
            FilterPill(
                group = entry.first,
                chosenId = entry.second,
                onPick = onPickFilter,
                onClear = onClearFilter,
            )
        }
    }
}

/**
 * 圈层折叠条：当前圈层名 + 订阅计数，点开弹出全部圈层（当前项打勾）与管理入口。
 * 头部主控件，走玻璃胶囊那套材质，与右侧纯文字的排序分主次；
 * 名字先让位（weight + 省略），计数与箭头永远在——名字长到把箭头挤没，这条就不像能点的了
 */
@Composable
private fun ScopeFoldPill(
    scopes: List<SourceScope>,
    labels: Map<String, String>,
    active: SourceScope?,
    onPick: (SourceScope) -> Unit,
    onManage: () -> Unit,
    dark: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    val activeLabel = active?.let { labels[it.id] ?: it.name }.orEmpty()
    val shape = RoundedCornerShape(999.dp)
    Box(Modifier.onSizeChanged { anchorHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .shadow(4.dp, shape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                .clip(shape)
                .background(if (dark) GlassBarBgDark else GlassBarBgLight)
                .border(
                    BorderStroke(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight),
                    shape,
                )
                .clickable { menuOpen = true }
                .padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = activeLabel,
                color = PikuColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.ao3_scope_fold_count, scopes.size),
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.ao3_scope_title),
                tint = PikuColors.textFaint,
                modifier = Modifier.size(14.dp),
            )
        }
        if (menuOpen) {
            MenuPopup(
                groups = listOf(
                    scopes.map { scope ->
                        MenuPopupItem(
                            label = labels[scope.id] ?: scope.name,
                            selected = scope.id == active?.id,
                            onClick = {
                                menuOpen = false
                                onPick(scope)
                            },
                        )
                    } + MenuPopupItem(
                        label = stringResource(R.string.ao3_scope_manage),
                        onClick = {
                            menuOpen = false
                            onManage()
                        },
                    ),
                ),
                dark = false,
                anchorHeightPx = anchorHeightPx,
                // 圈层名比排序项长得多（配对名尤甚）：默认 150dp 会折行，加宽到一行放得下
                width = 240.dp,
                onDismiss = { menuOpen = false },
            )
        }
    }
}

/** 排序入口：文字 + ▾，点开锚定在正下方的三项菜单，当前项打勾。无底色，主控件让给左侧圈层 */
@Composable
private fun SortPill(
    feeds: List<SourceFeed>,
    selectedId: String,
    label: String,
    onPick: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    Box(Modifier.onSizeChanged { anchorHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { menuOpen = true }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = PikuColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.source_view_sort),
                tint = PikuColors.textFaint,
                modifier = Modifier.size(14.dp),
            )
        }
        if (menuOpen) {
            MenuPopup(
                groups = listOf(
                    feeds.map { feed ->
                        MenuPopupItem(
                            label = stringResource(feed.labelRes),
                            selected = feed.id == selectedId,
                            onClick = {
                                menuOpen = false
                                onPick(feed.id)
                            },
                        )
                    },
                ),
                dark = false,
                anchorHeightPx = anchorHeightPx,
                onDismiss = { menuOpen = false },
            )
        }
    }
}

/** 筛选生效中才出现的胶囊：描边（临时意图，地位低于排序），✕ 原地清除不进菜单 */
@Composable
private fun FilterPill(
    group: SourceFacetGroup,
    chosenId: String,
    onPick: (groupId: String, optionId: String) -> Unit,
    onClear: (groupId: String) -> Unit,
) {
    val chosenLabel = group.options.firstOrNull { it.id == chosenId }?.let { stringResource(it.labelRes) } ?: chosenId
    var menuOpen by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    Box(Modifier.onSizeChanged { anchorHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, PikuColors.controlAccent, RoundedCornerShape(999.dp))
                .clickable { menuOpen = true }
                .padding(start = 11.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chosenLabel,
                color = PikuColors.controlAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.ao3_scope_remove),
                tint = PikuColors.textFaint,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onClear(group.id) },
            )
        }
        if (menuOpen) {
            FacetMenuPopup(
                options = group.options,
                selectedId = chosenId,
                dark = false,
                anchorHeightPx = anchorHeightPx,
                onSelect = { optionId ->
                    menuOpen = false
                    onPick(group.id, optionId)
                },
                onDismiss = { menuOpen = false },
            )
        }
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
    /** 折叠式头部的视图摘要：列表首行，随内容滚走 */
    summary: (@Composable () -> Unit)? = null,
    onRetryLoadMore: () -> Unit,
    onWorkClick: (Work) -> Unit,
    onToggleFavorite: (Work) -> Unit,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    // 榜单流：rank 1 给通栏聚光卡；第 2 名起在卡片上挂名次（前三奖章、4 名起小片）
    val heroCount = if (state.ranked) minOf(HERO_COUNT, state.items.size) else 0
    val gridItems = if (heroCount > 0) state.items.drop(heroCount) else state.items
    // 纯文字源声明单列：走 blurb（简介/标签/分级），格子数不参与（每项都是通栏）
    val singleColumn = state.listLayout == SourceListLayout.List
    ThumbnailPrefetchEffect(
        works = gridItems,
        gridState = gridState,
        // 摘要占一格，名次卡的位置随之+1
        itemOffset = heroCount + if (summary != null) 1 else 0,
    )
    LazyVerticalStaggeredGrid(
        columns = when {
            singleColumn -> StaggeredGridCells.Fixed(1)
            isTablet -> StaggeredGridCells.Adaptive(220.dp)
            else -> StaggeredGridCells.Fixed(2)
        },
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
        if (summary != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "view-summary") { summary() }
        }
        if (heroCount > 0) {
            item(span = StaggeredGridItemSpan.FullLine, key = "ranking-hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    RankingSpotlight(
                        work = state.items.first(),
                        dark = dark,
                        onClick = onWorkClick,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
        }
        itemsIndexed(gridItems, key = { _, work -> work.key.toString() }) { index, work ->
            if (singleColumn) {
                TextWorkBlurb(
                    work = work,
                    onToggleFavorite = onToggleFavorite,
                    onClick = onWorkClick,
                    dark = dark,
                )
            } else if (work.kind == WorkKind.NOVEL) {
                if (work.thumbnailUrl.isBlank()) {
                    TextWorkCard(
                        work = work,
                        onToggleFavorite = onToggleFavorite,
                        onClick = onWorkClick,
                        dark = dark,
                    )
                } else {
                    NovelWorkCard(
                        work = work,
                        onToggleFavorite = onToggleFavorite,
                        onClick = onWorkClick,
                        dark = dark,
                    )
                }
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


/**
 * 榜单 rank 1 的通栏聚光卡：整批内容里只有第一名值得这块版面。图用原作比例取景
 * （[heroAspect] 夹好上下界，竖图也不会无限拉高），底部压暗条放标题与作者——
 * 压暗条方案而不是脚注，是因为浅色图上白字读不清，压暗条任何图底都稳。
 */
@Composable
private fun RankingSpotlight(
    work: Work,
    dark: Boolean,
    onClick: (Work) -> Unit,
    onToggleFavorite: (Work) -> Unit,
) {
    var heartVisible by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(PikuLayout.CardCorner)
    val heroWidth = minOf(
        LocalConfiguration.current.screenWidthDp.dp - PikuLayout.ScreenInset * 2,
        HERO_MAX_WIDTH,
    )
    val heroHeight = (heroWidth / heroAspect(work)).coerceIn(HERO_MIN_HEIGHT, HERO_MAX_HEIGHT)
    Column(
        modifier = Modifier
            .width(heroWidth)
            .shadow(
                elevation = if (dark) 0.dp else 6.dp,
                shape = shape,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x33000000),
            )
            .clip(shape)
            .background(if (dark) WorkCardBgDark else Color(0xE6FFFFFF))
            // 与网格里的 2、3 名同一套名次描边：第 1 名也是这一组的成员，不该只有它没有边界
            .border(BorderStroke(1.dp, RankGoldBorder), shape)
            .combinedClickable(
                onClick = { onClick(work) },
                // 与网格卡同款：双击收藏，心形爆裂作即时反馈
                onDoubleClick = {
                    heartVisible = true
                    onToggleFavorite(work)
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight),
        ) {
            AsyncImage(
                model = heroFeedThumbUrl(work.thumbnailUrl),
                contentDescription = work.title,
                colorFilter = PikuColors.tameWhiteFilter,
                modifier = Modifier
                    .sharedWorkBounds(workSharedKey(work.authorId, work.id))
                    .fillMaxSize()
                    .background(if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
                contentScale = ContentScale.Crop,
            )
            FavoriteHeartBurst(
                visible = heartVisible,
                onFinished = { heartVisible = false },
                modifier = Modifier.align(Alignment.Center),
            )
            RankMedal(
                rank = 1,
                large = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
            if (work.imageCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_image_count, work.imageCount),
                        color = Color.White,
                        fontSize = 10.sp,
                    )
                }
            }
            // 底部压暗条：图上千奇百怪，标题永远要读得清。三段而不是两段——
            // 白衬衫、白蕾丝那类图渐变到一半就没了，15sp 的白字压不住
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x73000000), Color(0xE6000000)),
                        ),
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 30.dp, bottom = 10.dp),
            ) {
                Text(
                    text = work.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = work.authorAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = work.authorName,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val HERO_COUNT = 1

private val HERO_MIN_HEIGHT = 170.dp
private val HERO_MAX_HEIGHT = 240.dp

private val HERO_MAX_WIDTH = 560.dp

private const val HERO_MIN_ASPECT = 1.05f
private const val HERO_MAX_ASPECT = 1.45f

private const val HERO_DEFAULT_ASPECT = 1.3f

private fun heroAspect(work: Work): Float =
    if (work.thumbWidth > 0 && work.thumbHeight > 0) {
        (work.thumbWidth.toFloat() / work.thumbHeight).coerceIn(HERO_MIN_ASPECT, HERO_MAX_ASPECT)
    } else {
        HERO_DEFAULT_ASPECT
    }

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
    /** 折叠式头部的摘要控件：空态/失败态下它仍要在，否则用户被筛出空结果后没法改回来 */
    header: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        header?.invoke()
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
}

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
            .padding(
                start = PikuLayout.ScreenInset,
                end = PikuLayout.ScreenInset,
                top = 4.dp,
                bottom = 2.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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

@Composable
private fun FacetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val textColor by animateColorAsState(
        targetValue = if (selected) PikuColors.accent else PikuColors.textSecondary,
        animationSpec = tween(durationMillis = 200),
        label = "facetChipColor",
    )
    var labelWidthPx by remember { mutableIntStateOf(0) }
    val indicatorWidth by animateIntAsState(
        targetValue = if (selected) labelWidthPx else 0,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "facetIndicatorWidth",
    )
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            onTextLayout = { labelWidthPx = it.size.width },
        )
        Spacer(Modifier.height(1.dp))
        Box(
            modifier = Modifier
                .height(FacetIndicatorHeight)
                .width(with(density) { indicatorWidth.toDp() })
                .clip(RoundedCornerShape(FacetIndicatorHeight / 2))
                .background(PikuColors.accent),
        )
    }
}

private val FacetIndicatorHeight = 2.dp


@Composable
private fun ScopeSetupGuide(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PikuColors.controlAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalOffer,
                contentDescription = null,
                tint = PikuColors.controlAccent,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.ao3_scope_title),
            color = PikuColors.textPrimary,
            fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ao3_scope_hint),
            color = PikuColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 44.dp)
                .widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PikuColors.controlAccent)
                .clickable(onClick = onAdd)
                .padding(horizontal = 24.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = PikuColors.surface,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.ao3_scope_add),
                color = PikuColors.surface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
