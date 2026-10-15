package com.piku.client.core.ui.feature.search

/** 搜索页结果区：三 Tab 的作品瀑布流 / 用户列表 / 标签卡片与空错态 */


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.core.model.FollowUser
import com.piku.client.core.model.TagCard
import com.piku.client.core.model.WorkKey
import com.piku.client.core.source.SourceListLayout
import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.key
import com.piku.client.core.model.Work
import com.piku.client.core.ui.common.FollowPillButton
import com.piku.client.core.ui.common.GlassCard
import com.piku.client.core.ui.common.LoaderDots
import com.piku.client.core.ui.common.PikuBackButton
import com.piku.client.core.ui.common.PikuSegmented
import com.piku.client.core.ui.common.LoginPrompt
import com.piku.client.core.ui.common.UserAvatar
import com.piku.client.core.ui.common.formatCount
import com.piku.client.core.ui.common.labelRes
import com.piku.client.core.ui.common.TextWorkBlurb
import com.piku.client.core.ui.common.TextWorkCard
import com.piku.client.core.ui.common.WorkCard
import com.piku.client.source.pixiv.ui.novel.NovelWorkCard
import com.piku.client.core.ui.shell.ProportionalWorkCard
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.core.ui.theme.ShadowAmbient
import com.piku.client.core.ui.theme.ShadowSpot
import com.piku.client.core.ui.theme.SoftBorderLight
import com.piku.client.core.ui.theme.WorkCardBgDark
import com.piku.client.core.ui.theme.WorkCardBorderDark
import com.piku.client.core.ui.theme.WorkCardInfoBgDark
import com.piku.client.core.ui.theme.WorkCardPlaceholderDark
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun SearchTabRow(
    selected: SearchTab,
    onSelect: (SearchTab) -> Unit,
) {
    val tabs = SearchTab.entries
    PikuSegmented(
        labels = listOf(
            stringResource(R.string.search_tab_works),
            stringResource(R.string.search_tab_users),
            stringResource(R.string.search_tab_tags),
        ),
        selectedIndex = tabs.indexOf(selected),
        onSelect = { onSelect(tabs[it]) },
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
    )
}
@Composable
internal fun WorksTabContent(
    state: SearchUiState,
    isTablet: Boolean,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onWorkClick: (Work) -> Unit,
    dark: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        when {
            state.worksNeedLogin -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoginPrompt(
                        message = stringResource(R.string.search_works_login),
                        onLogin = onLoginClick,
                        dark = dark,
                    )
                }
            }
            state.worksLoading && state.works.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoaderDots(dark = dark)
                }
            }
            state.worksErrorRes != null && state.works.isEmpty() -> {
                SearchErrorState(errorRes = state.worksErrorRes, onRetry = onRetry, dark = dark)
            }
            state.works.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = PikuColors.textFaint,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.search_empty),
                            color = PikuColors.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            else -> {
                SearchWorkGrid(
                    works = state.works,
                    favoriteIds = state.favoriteIds,
                    isTablet = isTablet,
                    loadingMore = state.worksLoadingMore,
                    loadMoreErrorRes = state.worksLoadMoreErrorRes,
                    endReached = state.worksEndReached,
                    onLoadMore = onLoadMore,
                    onRetryLoadMore = onRetryLoadMore,
                    onToggleFavorite = onToggleFavorite,
                    onWorkClick = onWorkClick,
                    dark = dark,
                    proportional = state.proportional,
                    singleColumn = state.listLayout == SourceListLayout.List,
                )
            }
        }
    }
}

@Composable
internal fun TagsTabContent(
    state: SearchUiState,
    isTablet: Boolean,
    sourceLabel: String,
    filterEntry: (@Composable () -> Unit)? = null,
    onSourceClick: () -> Unit,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onTagClick: (String) -> Unit,
    onBackToSuggestions: () -> Unit,
    onWorkClick: (Work) -> Unit,
    dark: Boolean,
) {
    val selectedTag = state.selectedTagName
    val canBackToSuggestions = !state.tagNeedLogin &&
        !(state.tagSuggestions.isEmpty() && state.tagSuggestionsEndReached)
    Column(Modifier.fillMaxSize()) {
        if (selectedTag != null) {
            TagWorksHeader(
                tag = selectedTag,
                sourceLabel = sourceLabel,
                filterEntry = filterEntry,
                onBack = onBackToSuggestions,
                onSourceClick = onSourceClick,
                showBack = canBackToSuggestions,
                dark = dark,
            )
        }
        when {
            // 登录引导只覆盖建议模式：作品模式匿名可看
            state.tagNeedLogin && selectedTag == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoginPrompt(
                        message = stringResource(R.string.search_tags_login),
                        onLogin = onLoginClick,
                        dark = dark,
                    )
                }
            }
            selectedTag == null -> {
                // 建议模式：标签卡片（原站行为：始终先展示标签，点击后才出作品）
                when {
                    state.tagSuggestionsLoading && state.tagSuggestions.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoaderDots(dark = dark)
                        }
                    }
                    state.tagSuggestionsErrorRes != null && state.tagSuggestions.isEmpty() -> {
                        SearchErrorState(errorRes = state.tagSuggestionsErrorRes, onRetry = onRetry, dark = dark)
                    }
                    state.tagSuggestions.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Label,
                                    contentDescription = null,
                                    tint = PikuColors.textFaint,
                                    modifier = Modifier.size(32.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.search_tags_not_found),
                                    color = PikuColors.textSecondary,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                    else -> {
                        if (state.listLayout == SourceListLayout.List) {
                            TagEntryList(
                                cards = state.tagSuggestions,
                                loadingMore = state.tagSuggestionsLoadingMore,
                                loadMoreErrorRes = state.tagSuggestionsLoadMoreErrorRes,
                                endReached = state.tagSuggestionsEndReached,
                                onLoadMore = onLoadMore,
                                onRetryLoadMore = onRetryLoadMore,
                                onTagClick = onTagClick,
                                dark = dark,
                            )
                        } else {
                            TagCardGrid(
                                cards = state.tagSuggestions,
                                isTablet = isTablet,
                                loadingMore = state.tagSuggestionsLoadingMore,
                                loadMoreErrorRes = state.tagSuggestionsLoadMoreErrorRes,
                                endReached = state.tagSuggestionsEndReached,
                                onLoadMore = onLoadMore,
                                onRetryLoadMore = onRetryLoadMore,
                                onTagClick = onTagClick,
                                dark = dark,
                            )
                        }
                    }
                }
            }
            else -> {
                // 作品模式：选中精确标签下的作品
                when {
                    state.tagWorksLoading && state.tagWorks.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoaderDots(dark = dark)
                        }
                    }
                    state.tagWorksErrorRes != null && state.tagWorks.isEmpty() -> {
                        SearchErrorState(errorRes = state.tagWorksErrorRes, onRetry = onRetry, dark = dark)
                    }
                    state.tagWorks.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Label,
                                    contentDescription = null,
                                    tint = PikuColors.textFaint,
                                    modifier = Modifier.size(32.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.search_tags_empty),
                                    color = PikuColors.textSecondary,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                    else -> {
                        SearchWorkGrid(
                            works = state.tagWorks,
                            favoriteIds = state.favoriteIds,
                            isTablet = isTablet,
                            loadingMore = state.tagWorksLoadingMore,
                            loadMoreErrorRes = state.tagWorksLoadMoreErrorRes,
                            endReached = state.tagWorksEndReached,
                            onLoadMore = onLoadMore,
                            onRetryLoadMore = onRetryLoadMore,
                            onToggleFavorite = onToggleFavorite,
                            onWorkClick = onWorkClick,
                            dark = dark,
                            proportional = state.proportional,
                    singleColumn = state.listLayout == SourceListLayout.List,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 作品模式头部：返回标签建议 + 当前精确标签名 + 源 chip，行尾可选挂筛选入口。
 * 标签名与搜索框内容同源，仅作结果区上下文；源 chip 在本模式顶替筛选行，换源入口不缺席。
 * 筛选入口只给声明了标签档筛选的源（pixiv）：带条件数角标、点开底部面板——
 * 不内联生效条件 chips，行不因条件多而溢出，标签名拿足宽度。
 */
@Composable
private fun TagWorksHeader(
    tag: String,
    sourceLabel: String,
    filterEntry: (@Composable () -> Unit)?,
    onBack: () -> Unit,
    onSourceClick: () -> Unit,
    dark: Boolean,
    showBack: Boolean = true,
) {
    val primary = PikuColors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            PikuBackButton(
                onClick = onBack,
                dark = dark,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            text = "#$tag",
            color = primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 无返回按钮时（poipiku 匿名标签作品模式）补齐到 tab 行的 20dp 起始位
            modifier = Modifier
                .weight(1f)
                .padding(start = if (showBack) 4.dp else 16.dp),
        )
        SourceChip(label = sourceLabel, onClick = onSourceClick)
        if (filterEntry != null) {
            Spacer(Modifier.width(8.dp))
            filterEntry()
        }
    }
}

/** 文字源的标签行：整行 `#标签名` + 站点作品数；纯文字源摆图墙只会是一片占位格 */
@Composable
internal fun TagEntryRow(
    name: String,
    count: Int?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$name",
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatCount(it),
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

/** 文字源的标签建议列表：与图源卡片网格同一条链路，只换排布 */
@Composable
private fun TagEntryList(
    cards: List<TagCard>,
    loadingMore: Boolean,
    loadMoreErrorRes: Int?,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onTagClick: (String) -> Unit,
    dark: Boolean,
) {
    val listState = rememberLazyListState()
    val currentEndReached by rememberUpdatedState(endReached)
    val currentLoadingMore by rememberUpdatedState(loadingMore)
    val currentLoadMoreErrorRes by rememberUpdatedState(loadMoreErrorRes)

    LaunchedEffect(listState, cards.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !currentEndReached && !currentLoadingMore && currentLoadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
    ) {
        items(cards, key = { it.name }) { card ->
            TagEntryRow(name = card.name, count = card.count, onClick = { onTagClick(card.name) })
        }
        when {
            loadMoreErrorRes != null -> {
                item {
                    SearchLoadMoreError(errorRes = loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            loadingMore -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            endReached -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.home_no_more),
                            color = PikuColors.textFaint,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagCardGrid(
    cards: List<TagCard>,
    isTablet: Boolean,
    loadingMore: Boolean,
    loadMoreErrorRes: Int?,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onTagClick: (String) -> Unit,
    dark: Boolean,
) {
    val gridState = rememberLazyStaggeredGridState()
    val currentEndReached by rememberUpdatedState(endReached)
    val currentLoadingMore by rememberUpdatedState(loadingMore)
    val currentLoadMoreErrorRes by rememberUpdatedState(loadMoreErrorRes)

    LaunchedEffect(gridState, cards.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !currentEndReached && !currentLoadingMore && currentLoadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = if (isTablet) StaggeredGridCells.Adaptive(220.dp) else StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        items(cards, key = { it.name }) { card ->
            TagCardItem(
                card = card,
                onClick = { onTagClick(card.name) },
                dark = dark,
            )
        }
        when {
            loadMoreErrorRes != null -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SearchLoadMoreError(errorRes = loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            loadingMore -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            endReached -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_more),
                            color = PikuColors.textFaint,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagCardItem(
    card: TagCard,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .shadow(
                elevation = if (dark) 6.dp else 10.dp,
                shape = shape,
                ambientColor = ShadowAmbient,
                spotColor = ShadowSpot,
            )
            .clip(shape)
            .background(if (dark) WorkCardBgDark else Color(0xCCFFFFFF))
            .border(
                BorderStroke(1.dp, if (dark) WorkCardBorderDark else SoftBorderLight),
                shape,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            if (card.thumbnailUrl.isNullOrBlank()) {
                // 无示例图（默认占位图）的标签：显示中性占位而非空白
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (dark) WorkCardPlaceholderDark else PikuColors.surfaceSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Label,
                        contentDescription = "#${card.name}",
                        tint = PikuColors.textFaint,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = card.thumbnailUrl,
                    contentDescription = "#${card.name}",
                    colorFilter = PikuColors.tameWhiteFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (dark) WorkCardPlaceholderDark else PikuColors.surfaceSoft),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (dark) WorkCardInfoBgDark else Color(0xF2FFFFFF))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Text(
                text = "#${card.name}",
                color = PikuColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchWorkGrid(
    works: List<Work>,
    favoriteIds: Set<WorkKey>,
    isTablet: Boolean,
    loadingMore: Boolean,
    loadMoreErrorRes: Int?,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onWorkClick: (Work) -> Unit,
    dark: Boolean,
    proportional: Boolean = false,
    /** 文字源单列 blurb：整行一张，简介/标签有地方放 */
    singleColumn: Boolean = false,
) {
    val gridState = rememberLazyStaggeredGridState()
    val currentEndReached by rememberUpdatedState(endReached)
    val currentLoadingMore by rememberUpdatedState(loadingMore)
    val currentLoadMoreErrorRes by rememberUpdatedState(loadMoreErrorRes)

    LaunchedEffect(gridState, works.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !currentEndReached && !currentLoadingMore && currentLoadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = when {
            singleColumn -> StaggeredGridCells.Fixed(1)
            isTablet -> StaggeredGridCells.Adaptive(220.dp)
            else -> StaggeredGridCells.Fixed(2)
        },
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        items(works, key = { it.key.toString() }) { work ->
            if (singleColumn) {
                TextWorkBlurb(
                    work = work,
                    onToggleFavorite = onToggleFavorite,
                    onClick = onWorkClick,
                    dark = dark,
                )
            } else if (work.kind == WorkKind.NOVEL) {
                // 小说档整个列表都是小说：有封面走竖版封面卡，纯文字站（AO3）没有封面走文字卡
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
            } else if (proportional) {
                ProportionalWorkCard(
                    work = work,
                    onToggleFavorite = onToggleFavorite,
                    onClick = onWorkClick,
                    dark = dark,
                )
            } else {
                WorkCard(
                    work = work,
                    isFavorite = work.key in favoriteIds,
                    onToggleFavorite = onToggleFavorite,
                    onClick = onWorkClick,
                    dark = dark,
                )
            }
        }
        when {
            loadMoreErrorRes != null -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SearchLoadMoreError(errorRes = loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            loadingMore -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            endReached -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_more),
                            color = PikuColors.textFaint,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UsersTabContent(
    state: SearchUiState,
    dark: Boolean,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onUserClick: (FollowUser) -> Unit,
    onToggleFollow: (Long) -> Unit,
) {
    when {
        state.usersNeedLogin -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoginPrompt(
                    message = stringResource(R.string.search_users_login),
                    onLogin = onLoginClick,
                    dark = dark,
                )
            }
        }
        state.usersLoading && state.users.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoaderDots(dark = dark)
            }
        }
        state.usersErrorRes != null && state.users.isEmpty() -> {
            SearchErrorState(errorRes = state.usersErrorRes, onRetry = onRetry, dark = dark)
        }
        // 本源不提供用户检索：说清"不支持"，别用通用空态说成"没找到"
        state.usersUnsupported -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.People,
                        contentDescription = null,
                        tint = PikuColors.textFaint,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.search_users_unsupported,
                            stringResource(state.source.labelRes()),
                        ),
                        color = PikuColors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
        state.users.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.People,
                        contentDescription = null,
                        tint = PikuColors.textFaint,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.search_users_empty),
                        color = PikuColors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
        else -> {
            SearchUserList(
                state = state,
                dark = dark,
                onUserClick = onUserClick,
                onToggleFollow = onToggleFollow,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
            )
        }
    }
}

@Composable
private fun SearchUserList(
    state: SearchUiState,
    dark: Boolean,
    onUserClick: (FollowUser) -> Unit,
    onToggleFollow: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val currentUsersEndReached by rememberUpdatedState(state.usersEndReached)
    val currentUsersLoadingMore by rememberUpdatedState(state.usersLoadingMore)
    val currentUsersLoadMoreErrorRes by rememberUpdatedState(state.usersLoadMoreErrorRes)

    LaunchedEffect(listState, state.users.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !currentUsersEndReached && !currentUsersLoadingMore && currentUsersLoadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.users, key = { it.userId }) { user ->
            val followed = state.followOverrides[user.userId] ?: user.followed
            SearchUserRow(
                user = user,
                followed = followed,
                followSending = user.userId in state.followPendingIds,
                dark = dark,
                onClick = { onUserClick(user) },
                onToggleFollow = { onToggleFollow(user.userId) },
                modifier = Modifier.animateItem(),
            )
        }
        when {
            state.usersLoadMoreErrorRes != null -> {
                item {
                    SearchLoadMoreError(errorRes = state.usersLoadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            state.usersLoadingMore -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            state.usersEndReached && state.users.size >= 30 -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.home_no_more),
                            color = PikuColors.textFaint,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchUserRow(
    user: FollowUser,
    followed: Boolean,
    followSending: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    GlassCard(
        dark = dark,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(avatarUrl = user.avatarUrl, onClick = onClick, dark = dark, size = 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "ID: ${user.userId}",
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            FollowPillButton(
                followed = followed,
                refollow = false,
                sending = followSending,
                dark = dark,
                onClick = onToggleFollow,
            )
        }
    }
}

@Composable
private fun SearchErrorState(
    errorRes: Int?,
    onRetry: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(errorRes ?: R.string.home_error_parse),
            color = PikuColors.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(shape)
                .background(PikuColors.surface)
                .border(
                    BorderStroke(0.5.dp, PikuColors.border),
                    shape,
                )
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_retry),
                color = PikuColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SearchLoadMoreError(
    errorRes: Int?,
    onRetry: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .clip(shape)
            .background(PikuColors.surface)
            .border(
                BorderStroke(0.5.dp, PikuColors.border),
                shape,
            )
            .clickable(onClick = onRetry)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(errorRes ?: R.string.home_error_parse),
            color = PikuColors.textFaint,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.home_retry),
            color = PikuColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
