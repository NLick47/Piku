package com.piku.client.ui.home.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.Work
import com.piku.client.ui.home.FeedTab
import com.piku.client.ui.home.FeedTabColors
import com.piku.client.ui.home.FeedTabRow
import com.piku.client.ui.home.GlassHeader
import com.piku.client.ui.home.HomeContent
import com.piku.client.ui.home.HomeUiState
import com.piku.client.ui.home.TabletTopBar
import com.piku.client.ui.home.background.LiquidGlassBackdrop
import com.piku.client.ui.home.background.TabBand
import com.piku.client.ui.home.reportTabBand

/**
 * 主源（poipiku）专属壳：tab 图标、分类侧栏是主源自己的语义，HomeViewModel 的
 * feed 状态（FeedTab/分类/works）也只服务于它。
 *
 * 其它源不进这里——一律由宿主分流到 ui/source 的声明驱动通用壳 [com.piku.client.ui.source.SourceFeedContent]，
 * 新源注册进 SourceRegistry 即可获得完整首页，主源壳与通用壳互不感知。
 */
@Composable
internal fun PoipikuHomeShell(
    state: HomeUiState,
    isTablet: Boolean,
    dark: Boolean,
    isScrolling: MutableState<Boolean>,
    gridState: LazyStaggeredGridState,
    /** 头部底衬进度：绘制阶段读取，滚动只重绘底边线不触发重组 */
    scrollProgress: () -> Float,
    /** 列表停在顶部：自定义背景下头部底衬整体退场，把清晰头图让出来 */
    atTop: Boolean,
    tabColors: FeedTabColors?,
    onTabBand: (TabBand) -> Unit,
    drawerIsOpen: Boolean,
    menuEnabled: Boolean,
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onSelectFeedTab: (FeedTab) -> Unit,
    onSelectCategory: (PoipikuCategory) -> Unit,
    onGoTop: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onWorkClick: (Work) -> Unit,
    onAuthorClick: ((Work) -> Unit)?,
    onLoginClick: () -> Unit,
    onDismissRefreshNotice: () -> Unit,
    onOpenUpdate: () -> Unit,
    onDismissUpdateBanner: () -> Unit,
) {
    if (isTablet) {
        Row(Modifier.fillMaxSize()) {
            CategorySidebar(
                selected = state.category,
                onSelect = onSelectCategory,
                dark = dark,
            )
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                ) {
                    LiquidGlassBackdrop(
                        dark = dark,
                        isScrolling = isScrolling,
                        modifier = Modifier.matchParentSize(),
                        translucent = state.customBackgroundPath != null,
                        progress = scrollProgress,
                    )
                    Column(Modifier.fillMaxWidth()) {
                        TabletTopBar(
                            avatarUrl = state.userAvatarUrl,
                            onMenuClick = onOpenDrawer,
                            menuEnabled = menuEnabled,
                            onSearchClick = onSearchClick,
                            onDoubleTapTop = onGoTop,
                            dark = dark,
                        )
                        Box(Modifier.reportTabBand(onTabBand)) {
                            FeedTabRow(
                                feedTab = state.feedTab,
                                onSelectFeedTab = onSelectFeedTab,
                                dark = dark,
                                tabColors = tabColors,
                            )
                        }
                    }
                }
                HomeContent(
                    state = state,
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
                    onRetryLoadMore = onRetryLoadMore,
                    onShuffle = onShuffle,
                    onToggleFavorite = onToggleFavorite,
                    onWorkClick = onWorkClick,
                    onAuthorClick = onAuthorClick,
                    onLoginClick = onLoginClick,
                    onDismissRefreshNotice = onDismissRefreshNotice,
                    onGoTop = onGoTop,
                    onOpenUpdate = onOpenUpdate,
                    onDismissUpdateBanner = onDismissUpdateBanner,
                    dark = dark,
                    isScrolling = isScrolling,
                    gridState = gridState,
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            GlassHeader(
                state = state,
                avatarUrl = state.userAvatarUrl,
                onMenuClick = onOpenDrawer,
                menuEnabled = menuEnabled,
                onSearchClick = onSearchClick,
                onSelectFeedTab = onSelectFeedTab,
                onCategoryClick = onCategoryClick,
                onDoubleTapTop = onGoTop,
                dark = dark,
                isScrolling = isScrolling,
                scrollProgress = scrollProgress,
                drawerIsOpen = drawerIsOpen,
                atTop = atTop,
                tabColors = tabColors,
                onTabBand = onTabBand,
            )
            HomeContent(
                state = state,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
                onShuffle = onShuffle,
                onToggleFavorite = onToggleFavorite,
                onWorkClick = onWorkClick,
                onAuthorClick = onAuthorClick,
                onLoginClick = onLoginClick,
                onDismissRefreshNotice = onDismissRefreshNotice,
                onGoTop = onGoTop,
                onOpenUpdate = onOpenUpdate,
                onDismissUpdateBanner = onDismissUpdateBanner,
                dark = dark,
                isScrolling = isScrolling,
                gridState = gridState,
            )
        }
    }
}
