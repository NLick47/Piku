package com.piku.client.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.piku.client.R
import com.piku.client.data.remote.GitHubRelease
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.common.feedThumbUrl
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginCardLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.LoginButtonDark
import com.piku.client.ui.theme.LoginButtonLight
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardBorderDark
import com.piku.client.ui.theme.WorkCardPlaceholderDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 首屏可见 item 数超过该值（约 6 行）时显示"回到顶部"悬浮按钮 */
internal const val FAB_SHOW_AFTER_ITEMS = 12

internal const val PREFETCH_IMAGE_COUNT = 12

internal const val GO_TOP_ANIMATE_MAX_ITEMS = 50

/** 回顶：深位置直跳、近距离保留下滑动画 */
internal fun LazyStaggeredGridState.scrollToTopSmart(scope: CoroutineScope) {
    scope.launch {
        if (firstVisibleItemIndex > GO_TOP_ANIMATE_MAX_ITEMS) {
            scrollToItem(0)
        } else {
            animateScrollToItem(0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onWorkClick: (Work) -> Unit,
    onLoginClick: () -> Unit,
    onDismissRefreshNotice: () -> Unit,
    onGoTop: () -> Unit,
    onOpenUpdate: () -> Unit,
    onDismissUpdateBanner: () -> Unit,
    dark: Boolean,
    isScrolling: MutableState<Boolean>,
    gridState: LazyStaggeredGridState,
) {
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        snapshotFlow { isScrolling.value to currentState.refreshNotice }
            .distinctUntilChanged()
            .collect { (scrolling, notice) ->
                if (scrolling && notice == 0) onDismissRefreshNotice()
            }
    }
    Box(Modifier.fillMaxSize()) {
        when {
        state.loading && state.works.isEmpty() -> {
            SkeletonGrid(dark = dark)
        }
        state.errorRes != null && state.works.isEmpty() -> {
            ErrorState(errorRes = state.errorRes, onRetry = onRetry, dark = dark)
        }
        state.works.isEmpty() && !state.loading -> {
            val emptyRes = when {
                state.feedTab == FeedTab.FOLLOW && state.followNeedLogin -> R.string.home_follow_login
                state.feedTab == FeedTab.FOLLOW -> R.string.home_follow_empty
                else -> R.string.home_empty
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(emptyRes),
                        color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    if (state.feedTab == FeedTab.FOLLOW && state.followNeedLogin) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (dark) LoginButtonDark else LoginButtonLight)
                                .clickable(onClick = onLoginClick)
                                .padding(horizontal = 24.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.login_button),
                                color = if (dark) LoginBackgroundDark else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        else -> {
            Box(Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = onRetry,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    WorkWaterfall(
                        works = state.works,
                        favoriteIds = state.favoriteIds,
                        loadingMore = state.loadingMore,
                        loadMoreErrorRes = state.loadMoreErrorRes,
                        endReached = state.endReached,
                        showShuffle = state.feedTab == FeedTab.RANDOM,
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onRetryLoadMore,
                        onShuffle = onShuffle,
                        onGoTop = onGoTop,
                        onToggleFavorite = onToggleFavorite,
                        onWorkClick = onWorkClick,
                        dark = dark,
                        isScrolling = isScrolling,
                        gridState = gridState,
                    )
                }
                AnimatedVisibility(
                    visible = state.refreshNotice != null,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
                ) {
                    RefreshNoticeBar(
                        count = state.refreshNotice ?: 0,
                        onDismiss = onDismissRefreshNotice,
                        onGoTop = onGoTop,
                        dark = dark,
                    )
                }
            }
        }
        }
        state.updateBanner?.let { release ->
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

@Composable
private fun UpdateBannerBar(
    release: GitHubRelease,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val pill = RoundedCornerShape(22.dp)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(release.tagName) {
        delay(8_000)
        currentOnDismiss()
    }
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(10.dp, pill)
            .clip(pill)
            .background(if (dark) LoginCardDark else LoginCardLight, pill)
            .border(
                BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                pill,
            )
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.SystemUpdate,
            contentDescription = null,
            tint = AccentDark,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.update_available, release.tagName),
            color = primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.update_open),
            color = AccentDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.detail_fullscreen_close),
                tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun RefreshNoticeBar(
    count: Int,
    onDismiss: () -> Unit,
    onGoTop: () -> Unit,
    dark: Boolean,
) {
    val isNew = count > 0
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val pill = RoundedCornerShape(22.dp)
    LaunchedEffect(isNew) {
        delay(if (isNew) 3500 else 1600)
        onDismiss()
    }
    Row(
        modifier = Modifier
            .padding(top = 10.dp)
            .shadow(10.dp, pill)
            .clip(pill)
            .background(if (dark) LoginCardDark else LoginCardLight, pill)
            .border(
                BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                pill,
            )
            .clickable {
                if (isNew) {
                    onGoTop()
                    onDismiss()
                } else {
                    onDismiss()
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isNew) Icons.Filled.KeyboardArrowUp else Icons.Filled.Check,
            contentDescription = null,
            tint = if (isNew) AccentDark else if (dark) FollowDark else FollowLight,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isNew) {
                stringResource(R.string.home_refresh_new, count)
            } else {
                stringResource(R.string.home_refresh_fresh)
            },
            color = primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkWaterfall(
    works: List<Work>,
    favoriteIds: Set<Long>,
    loadingMore: Boolean,
    loadMoreErrorRes: Int?,
    endReached: Boolean,
    showShuffle: Boolean,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onShuffle: () -> Unit,
    onGoTop: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onWorkClick: (Work) -> Unit,
    dark: Boolean,
    isScrolling: MutableState<Boolean>,
    gridState: LazyStaggeredGridState,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val prefetchContext = LocalContext.current
    val scrollProgress = remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            if (info.totalItemsCount <= 1) {
                0f
            } else {
                ((info.visibleItemsInfo.lastOrNull()?.index ?: 0).toFloat() /
                    (info.totalItemsCount - 1).toFloat())
                    .coerceIn(0f, 1f)
            }
        }
    }
    val showFab = remember {
        derivedStateOf { gridState.firstVisibleItemIndex > FAB_SHOW_AFTER_ITEMS }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling.value = it }
    }

    LaunchedEffect(gridState, works.size, loadMoreErrorRes) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !endReached && !loadingMore && loadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val prefetchSidePx = remember(isTablet, screenWidthDp) {
        if (isTablet) {
            512
        } else {
            (((screenWidthDp - 44) / 2f) * density.density).roundToInt().coerceIn(256, 512)
        }
    }

    LaunchedEffect(works.lastOrNull()?.id, gridState) {
        val loader = SingletonImageLoader.get(prefetchContext)
        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        works
            .drop(lastVisible + 1)
            .take(PREFETCH_IMAGE_COUNT)
            .forEach { work ->
                val url = feedThumbUrl(work.thumbnailUrl)
                if (url.isBlank()) return@forEach
                loader.enqueue(
                    ImageRequest.Builder(prefetchContext)
                        .data(url)
                        .size(CoilSize(prefetchSidePx, prefetchSidePx))
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build(),
                )
            }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = if (isTablet) StaggeredGridCells.Adaptive(220.dp) else StaggeredGridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
        ) {
            items(works, key = { it.id }) { work ->
                WorkCard(
                    work = work,
                    isFavorite = work.id in favoriteIds,
                    onToggleFavorite = onToggleFavorite,
                    onClick = onWorkClick,
                    dark = dark,
                )
            }
            if (loadMoreErrorRes != null) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    LoadMoreErrorItem(
                        errorRes = loadMoreErrorRes,
                        onRetry = onRetryLoadMore,
                        dark = dark,
                    )
                }
            } else if (showShuffle) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    ShuffleItem(onShuffle = onShuffle, dark = dark)
                }
            } else if (loadingMore) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoaderDots(dark = dark)
                    }
                }
            } else if (endReached) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    NoMoreItem(dark = dark)
                }
            }
        }
        ScrollProgressBar(progress = scrollProgress, dark = dark)
        BackToTopFab(
            showFab = showFab,
            isScrolling = isScrolling,
            onGoTop = onGoTop,
            dark = dark,
        )
    }
}

@Composable
private fun BoxScope.BackToTopFab(
    showFab: State<Boolean>,
    isScrolling: State<Boolean>,
    onGoTop: () -> Unit,
    dark: Boolean,
) {
    AnimatedVisibility(
        visible = showFab.value && !isScrolling.value,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 12.dp),
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        BackToTopButton(onClick = onGoTop, dark = dark)
    }
}

@Composable
private fun ScrollProgressBar(progress: State<Float>, dark: Boolean) {
    val trackColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.50f)
    val fillColor = if (dark) Color.White.copy(alpha = 0.45f) else Color(0xFF2C2C2C).copy(alpha = 0.30f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawRect(trackColor)
                drawRect(
                    color = fillColor,
                    size = Size(size.width * progress.value, size.height),
                )
            },
    )
}

@Composable
private fun BackToTopButton(onClick: () -> Unit, dark: Boolean) {
    val pill = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(10.dp, pill, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
            .clip(pill)
            .background(if (dark) Color(0xE63A3834) else Color(0xE6FFFFFF))
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x59FFFFFF) else Color(0x59C8C2B8)),
                pill,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.home_back_to_top),
            tint = if (dark) LoginTextPrimaryDark else AccentDark,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LoadMoreErrorItem(errorRes: Int, onRetry: () -> Unit, dark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(errorRes),
            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (dark) GlassCardBgDark else Color.White)
                .border(
                    BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                    RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_retry),
                color = if (dark) LoginTextPrimaryDark else AccentDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun NoMoreItem(dark: Boolean) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.home_no_more),
            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ShuffleItem(onShuffle: () -> Unit, dark: Boolean) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (dark) GlassCardBgDark else Color.White)
                .border(
                    BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                    RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onShuffle)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_random_more),
                color = if (dark) LoginTextPrimaryDark else AccentDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SkeletonGrid(dark: Boolean) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    LazyVerticalStaggeredGrid(
        columns = if (isTablet) StaggeredGridCells.Adaptive(220.dp) else StaggeredGridCells.Fixed(2),
        state = rememberLazyStaggeredGridState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        items(6) {
            SkeletonCard(dark = dark)
        }
    }
}

@Composable
private fun SkeletonCard(dark: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val placeholder = if (dark) WorkCardPlaceholderDark else Color(0xFFE8E4DE)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (dark) WorkCardBgDark else Color(0xCCFFFFFF))
            .border(
                BorderStroke(1.dp, if (dark) WorkCardBorderDark else Color(0x59C8C2B8)),
                shape,
            )
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(placeholder),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(placeholder),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(placeholder),
        )
    }
}

@Composable
private fun ErrorState(errorRes: Int, onRetry: () -> Unit, dark: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(errorRes),
            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(
                    BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                    RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.home_retry),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 13.sp,
                onTextLayout = {},
            )
        }
    }
}
