package com.piku.client.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.piku.client.R
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.PopularTag
import com.piku.client.domain.model.ThemeMode
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.profile.ProfileEditSheet
import com.piku.client.ui.theme.AccentPurple
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.GlassIconBgDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginCardLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardBorderDark
import com.piku.client.ui.theme.WorkCardPlaceholderDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI
import kotlin.math.sin

/** 首屏可见 item 数超过该值（约 6 行）时显示“回到顶部”悬浮按钮 */
private const val FAB_SHOW_AFTER_ITEMS = 12

/** 预取视口下方缩略图数量（约 1.5~2 屏），滚动时图片已在缓存，避免首帧解码/加载突发 */
private const val PREFETCH_IMAGE_COUNT = 12

@Composable
fun HomeScreen(
    pendingTag: String?,
    onTagConsumed: () -> Unit,
    onWorkClick: (Work) -> Unit,
    onLoginClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onTagsClick: () -> Unit,
    onFollowUsersClick: () -> Unit,
    onUserSearch: (String) -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val context = LocalContext.current
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var showRetentionSheet by rememberSaveable { mutableStateOf(false) }
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showProfileEdit by rememberSaveable { mutableStateOf(false) }
    val isScrolling = remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    LaunchedEffect(pendingTag) {
        if (pendingTag != null) {
            viewModel.selectTag(pendingTag)
            onTagConsumed()
        }
    }

    // Whenever the feed is cleared (tab/category/tag switch, shuffle, reload), reset the grid to top.
    LaunchedEffect(state.works.isEmpty()) {
        if (state.works.isEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    // 退出登录不关抽屉：抽屉保留并显示未登录态，可在抽屉内直接点头像重新登录
    val onLogout = viewModel::logout

    UserDrawer(
        drawerState = drawerState,
        userProfile = state.userProfile,
        adultEnabled = state.adultEnabled,
        themeMode = state.themeMode,
        historyRetentionDays = state.historyRetentionDays,
        language = state.language,
        onToggleAdult = viewModel::toggleAdultContent,
        onThemeClick = { showThemeSheet = true },
        onRetentionClick = { showRetentionSheet = true },
        onLanguageClick = { showLanguageSheet = true },
        onHistoryClick = onHistoryClick,
        onCollectionClick = {
            scope.launch { drawerState.close() }
            onCollectionClick()
        },
        onTagsClick = {
            scope.launch { drawerState.close() }
            onTagsClick()
        },
        onFollowUsersClick = {
            scope.launch { drawerState.close() }
            onFollowUsersClick()
        },
        onProfileClick = {
            scope.launch { drawerState.close() }
            showProfileEdit = true
        },
        onLoginClick = {
            // 必须先关抽屉再导航：抽屉开着时导航，残留的 scrim/手势层会
            // 吞掉登录页的点击（历史 bug：登录页回退按钮点了无效）。
            // close() 挂起直到抽屉完全合上才继续导航，避免过渡期窗口被吞点击
            android.util.Log.d(
                "PikuDiag",
                "drawer header login click, drawerOpen=${drawerState.isOpen}",
            )
            if (drawerState.isOpen) {
                scope.launch {
                    drawerState.close()
                    onLoginClick()
                }
            } else {
                onLoginClick()
            }
        },
        onLogout = onLogout,
        dark = dark,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawHomeBackdrop(dark)
            }
            if (isTablet) {
                Row(Modifier.fillMaxSize()) {
                    CategorySidebar(
                        selected = state.category,
                        onSelect = viewModel::selectCategory,
                        dark = dark,
                    )
                    Column(Modifier.weight(1f)) {
                        // 平板端头部整块（图标行 + 标签行）共用一片液态玻璃
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
                            )
                            Column(Modifier.fillMaxWidth()) {
                                TabletTopBar(
                                    avatarUrl = state.userAvatarUrl,
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onSearchClick = { showSearch = true },
                                    onMoreTags = { showTags = true },
                                    onDoubleTapTop = { scope.launch { gridState.animateScrollToItem(0) } },
                                    dark = dark,
                                )
                                FeedTabRow(
                                    feedTab = state.feedTab,
                                    currentTag = state.currentTag,
                                    currentKeyword = state.currentKeyword,
                                    onSelectFeedTab = viewModel::selectFeedTab,
                                    onClearTag = { viewModel.selectTag(null) },
                                    onClearKeyword = { viewModel.selectKeyword(null) },
                                    dark = dark,
                                )
                            }
                        }
                        HomeContent(
                            state = state,
                            onRetry = viewModel::retry,
                            onLoadMore = viewModel::loadMore,
                            onRetryLoadMore = viewModel::retryLoadMore,
                            onShuffle = viewModel::shuffleRandom,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onWorkClick = onWorkClick,
                            onLoginClick = onLoginClick,
                            onDismissRefreshNotice = viewModel::dismissRefreshNotice,
                            onGoTop = { scope.launch { gridState.animateScrollToItem(0) } },
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
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSearchClick = { showSearch = true },
                    onSelectFeedTab = viewModel::selectFeedTab,
                    onMoreTags = { showTags = true },
                    onCategoryClick = { showCategories = true },
                    onClearTag = { viewModel.selectTag(null) },
                    onClearKeyword = { viewModel.selectKeyword(null) },
                    onDoubleTapTop = { scope.launch { gridState.animateScrollToItem(0) } },
                    dark = dark,
                    isScrolling = isScrolling,
                )
                    HomeContent(
                        state = state,
                        onRetry = viewModel::retry,
                        onLoadMore = viewModel::loadMore,
                        onRetryLoadMore = viewModel::retryLoadMore,
                        onShuffle = viewModel::shuffleRandom,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onWorkClick = onWorkClick,
                        onLoginClick = onLoginClick,
                        onDismissRefreshNotice = viewModel::dismissRefreshNotice,
                        onGoTop = { scope.launch { gridState.animateScrollToItem(0) } },
                        dark = dark,
                        isScrolling = isScrolling,
                        gridState = gridState,
                    )
                }
            }

if (showCategories) {
            CategorySheet(
                selected = state.category,
                onSelect = {
                    viewModel.selectCategory(it)
                    showCategories = false
                },
                onDismiss = { showCategories = false },
                dark = dark,
            )
        }

        if (showTags) {
            TagSheet(
                tags = state.tags,
                customTags = state.customTags,
                currentTag = state.currentTag,
                onSelect = {
                    viewModel.selectTag(it)
                    showTags = false
                },
                onSelectCustomTag = { tag ->
                    viewModel.selectTag(tag)
                    showTags = false
                },
                onManageTags = {
                    showTags = false
                    onTagsClick()
                },
                onDismiss = { showTags = false },
                dark = dark,
            )
        }

            if (showSearch) {
                HomeSearchSheet(
                    onSearch = { keyword ->
                        val tag = keyword.removePrefix("#").trim()
                        when {
                            keyword.startsWith("#") && tag.isNotEmpty() -> {
                                viewModel.selectTag(tag)
                                showSearch = false
                            }
                            // @ 前缀：作者搜索（需登录），进入独立作者搜索结果页
                            keyword.startsWith("@") && keyword.length > 1 -> {
                                onUserSearch(keyword)
                                showSearch = false
                            }
                            else -> {
                                viewModel.selectKeyword(keyword)
                                showSearch = false
                            }
                        }
                    },
                    onDismiss = { showSearch = false },
                )
            }

            if (showThemeSheet) {
                ThemeModeSheet(
                    selected = state.themeMode,
                    onSelect = { mode ->
                        viewModel.setThemeMode(mode)
                        showThemeSheet = false
                    },
                    onDismiss = { showThemeSheet = false },
                    dark = dark,
                )
            }

            if (showRetentionSheet) {
                RetentionSheet(
                    selectedDays = state.historyRetentionDays,
                    onSelect = { days ->
                        viewModel.setHistoryRetentionDays(days)
                        showRetentionSheet = false
                    },
                    onDismiss = { showRetentionSheet = false },
                    dark = dark,
                )
            }

            if (showLanguageSheet) {
                LanguageSheet(
                    selected = state.language,
                    onSelect = { language ->
                        viewModel.setLanguage(language)
                        showLanguageSheet = false
                        // 语言切换需要重建 Activity 让新的 Locale 配置生效
                        (context as? Activity)?.recreate()
                    },
                    onDismiss = { showLanguageSheet = false },
                    dark = dark,
                )
            }

            if (showProfileEdit) {
                ProfileEditSheet(
                    profile = state.userProfile,
                    dark = dark,
                    onOpenPublicProfile = {
                        val url = state.userProfile?.profileUrl
                        if (url != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    onDismiss = { showProfileEdit = false },
                )
            }
        }
    }
}

/** 头部毛玻璃衬底的模糊半径 */
private val GlassBlurRadius = 24.dp

/**
 * 页面背景（渐变 + 彩色光斑）：
 * 首页 Canvas 与头部毛玻璃衬底共用同一绘制，保证模糊层与页面背景严格对齐。
 */
private fun DrawScope.drawHomeBackdrop(dark: Boolean) {
    drawRect(
        brush = Brush.verticalGradient(
            if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
            else listOf(HomeBgTopLight, HomeBgBottomLight),
        ),
    )
    val blobWarm = if (dark) Color(0x33C98A2D) else Color(0x4DC98A2D)
    val blobPink = if (dark) Color(0x33D8A8B8) else Color(0x4DD8A8B8)
    fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = Offset(cx, cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )
    }
    if (dark) {
        // 暗色：梦幻紫光斑（原灰斑改色，尺寸不变）
        blob(Color(0x409A7FC9), size.width - 40.dp.toPx(), 96.dp.toPx(), 120.dp.toPx())
    } else {
        // 亮色：梦幻紫光斑缩小、减淡并下移，避免在头部下方形成过大的色晕
        blob(Color(0x4D9A7FC9), size.width - 36.dp.toPx(), 140.dp.toPx(), 76.dp.toPx())
    }
    blob(blobWarm, 0f, 400.dp.toPx(), 100.dp.toPx())
    blob(blobPink, size.width, 620.dp.toPx(), 90.dp.toPx())
}

/**
 * 头部玻璃衬底的绘制：
 * - 亮色：只用干净渐变——页面顶部的灰色光斑不透入玻璃，避免"上白下灰"的违和感；
 * - 暗色：页面背景磨砂拷贝（与页面 Canvas 同源），暗色下灰斑含蓄，衔接自然。
 */
private fun DrawScope.drawHeaderBackdrop(dark: Boolean) {
    if (!dark) {
        drawRect(
            brush = Brush.verticalGradient(listOf(HomeBgTopLight, HomeBgBottomLight)),
        )
        return
    }
    drawHomeBackdrop(dark)
}

@Composable
private fun LiquidGlassBackdrop(
    dark: Boolean,
    isScrolling: State<Boolean>,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "liquidGlass")
    // 斜向光带：6s 一个来回
    val sheen by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sheenX",
    )
    // 液态时钟：12s 一圈，驱动气泡上浮与表面波纹
    val liquid by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liquidT",
    )
    // 滚动时玻璃变浓（短暂动画），模拟液体受扰动后聚拢的质感
    val deepen by animateFloatAsState(
        targetValue = if (isScrolling.value) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "glassDeepen",
    )
    // tint：暗色保持深色通透玻璃；亮色回到接近不透明的暖白（与页面 F5F3F0 同色），
    // 底部略透一点形成下坠感，不再有"白色层 + 背后灰斑"的违和
    val tintTop = if (dark) Color(0xFF1C1A18) else Color(0xFFF5F3F0)
    val tintBottom = if (dark) Color(0xFF2B2533) else Color(0xFFF5F3F0)
    val tintTopAlpha = if (dark) 0.50f + 0.10f * deepen else 0.95f + 0.03f * deepen
    val tintBottomAlpha = if (dark) 0.32f + 0.14f * deepen else 0.80f + 0.08f * deepen
    Box(modifier) {
        // 模糊层：亮色为干净渐变，暗色为页面背景磨砂拷贝
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawHeaderBackdrop(dark) }
                .blur(GlassBlurRadius),
        )
        // tint 层
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tintTop.copy(alpha = tintTopAlpha),
                            tintBottom.copy(alpha = tintBottomAlpha),
                        ),
                    ),
                ),
        )
        // 光泽层
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawGlassShine(sheen, liquid, dark) },
        )
    }
}

/** 液态玻璃中缓缓上浮的气泡参数 */
private class GlassBubble(
    val xFrac: Float,
    val phase: Float,
    val radius: Dp,
    val alpha: Float,
    val sway: Dp,
    val swayWaves: Float,
)

private val GlassBubbles = listOf(
    GlassBubble(xFrac = 0.12f, phase = 0.00f, radius = 4.dp, alpha = 0.22f, sway = 8.dp, swayWaves = 1.5f),
    GlassBubble(xFrac = 0.35f, phase = 0.33f, radius = 3.dp, alpha = 0.16f, sway = 6.dp, swayWaves = 2.0f),
    GlassBubble(xFrac = 0.58f, phase = 0.66f, radius = 3.5.dp, alpha = 0.24f, sway = 10.dp, swayWaves = 1.0f),
    GlassBubble(xFrac = 0.78f, phase = 0.20f, radius = 2.5.dp, alpha = 0.14f, sway = 5.dp, swayWaves = 2.5f),
    GlassBubble(xFrac = 0.92f, phase = 0.50f, radius = 2.dp, alpha = 0.12f, sway = 4.dp, swayWaves = 3.0f),
)

/** 玻璃中细碎星光的参数：固定点位，按各自频率明暗闪烁 */
private class GlassSparkle(
    val xFrac: Float,
    val yFrac: Float,
    val phase: Float,
    val speed: Float,
)

private val GlassSparkles = listOf(
    GlassSparkle(0.06f, 0.30f, 0.00f, 1.5f),
    GlassSparkle(0.24f, 0.68f, 0.35f, 2.1f),
    GlassSparkle(0.48f, 0.18f, 0.70f, 1.2f),
    GlassSparkle(0.68f, 0.52f, 0.22f, 2.4f),
    GlassSparkle(0.90f, 0.35f, 0.55f, 1.8f),
)

private fun DrawScope.drawGlassShine(sheen: Float, liquid: Float, dark: Boolean) {
    // 顶部玻璃边缘高光
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                if (dark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.55f),
                Color.Transparent,
            ),
        ),
        size = Size(size.width, 2.dp.toPx()),
    )
    // 缓慢扫过的斜向光带
    val band = size.width * 0.5f
    val centerX = (sheen - 0.5f) * (size.width + band * 2f)
    drawRect(
        brush = Brush.linearGradient(
            colors = if (dark) {
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.14f),
                    Color.Transparent,
                )
            } else {
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.20f),
                    Color.Transparent,
                )
            },
            start = Offset(centerX - band / 2f, -size.height * 0.5f),
            end = Offset(centerX + band / 2f, size.height * 1.5f),
        ),
    )
    // 液态气泡：从底部缓缓上浮，带轻微横向摆动，两端渐隐
    // 亮色：白底 + 细灰边（在暖白底上可见）；暗色：纯白气泡
    GlassBubbles.forEach { b ->
        val t = (liquid + b.phase) % 1f
        val fade = when {
            t < 0.12f -> t / 0.12f
            t > 0.88f -> (1f - t) / 0.12f
            else -> 1f
        }
        val x = size.width * b.xFrac +
            (sin((liquid + b.phase) * 2 * PI * b.swayWaves) * b.sway.toPx()).toFloat()
        val y = size.height * (1f - t)
        val center = Offset(x, y)
        val radius = b.radius.toPx()
        if (dark) {
            drawCircle(
                color = Color.White.copy(alpha = b.alpha * fade * 1.4f),
                radius = radius,
                center = center,
            )
        } else {
            drawCircle(
                color = Color.White.copy(alpha = (0.10f + b.alpha * 2.0f) * fade),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = Color(0xFF2C2C2C).copy(alpha = 0.12f * fade),
                radius = radius,
                center = center,
                style = Stroke(0.8.dp.toPx()),
            )
        }
    }
    // 细碎星光：在玻璃内原地明暗闪烁，与气泡的浮升节奏错开，增加灵动感
    GlassSparkles.forEach { s ->
        val twinkle = (0.5f + 0.5f * sin(liquid * 2 * PI * s.speed + s.phase * 2 * PI)).toFloat()
        val alpha = if (dark) 0.30f * twinkle else 0.22f * twinkle
        val sparkleColor = if (dark) Color(0xFFC9B8E8) else Color(0xFFFFFFFF)
        drawCircle(
            color = sparkleColor.copy(alpha = alpha),
            radius = 1.2.dp.toPx(),
            center = Offset(size.width * s.xFrac, size.height * s.yFrac),
        )
    }
    // 底部内沿：液态表面波纹，缓慢起伏（亮色用浅灰线，暗色用白线）
    val waveBase = size.height - 3.dp.toPx()
    val waveAmp = (if (dark) 1.6.dp else 2.2.dp).toPx()
    val waveColor = if (dark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color(0xFF2C2C2C).copy(alpha = 0.12f)
    }
    val steps = 32
    val stepW = size.width / steps
    val wavePhase = liquid * 4 * PI
    var prevX = 0f
    var prevY = waveBase + (sin(wavePhase) * waveAmp).toFloat()
    for (i in 1..steps) {
        val x = stepW * i
        val y = waveBase + (sin(wavePhase + x * 0.03) * waveAmp).toFloat()
        drawLine(
            color = waveColor,
            start = Offset(prevX, prevY),
            end = Offset(x, y),
            strokeWidth = 1.dp.toPx(),
        )
        prevX = x
        prevY = y
    }
    // 底部发丝分隔线（暗色下 PillBorderDark 太暗，改用半透明白色保证可见）
    drawLine(
        color = if (dark) Color.White.copy(alpha = 0.14f) else PillBorderLight,
        start = Offset(0f, size.height - 0.5.dp.toPx()),
        end = Offset(size.width, size.height - 0.5.dp.toPx()),
        strokeWidth = 0.5.dp.toPx(),
    )
}

@Composable
private fun GlassHeader(
    state: HomeUiState,
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSelectFeedTab: (FeedTab) -> Unit,
    onMoreTags: () -> Unit,
    onCategoryClick: () -> Unit,
    onClearTag: () -> Unit,
    onClearKeyword: () -> Unit,
    onDoubleTapTop: () -> Unit,
    dark: Boolean,
    isScrolling: State<Boolean>,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onDoubleTapTop) {
                detectTapGestures(onDoubleTap = { onDoubleTapTop() })
            }
            .statusBarsPadding()
            .padding(top = 8.dp),
    ) {
        // 液态玻璃衬底：模糊背景拷贝 + 通透 tint + 缓慢流动的高光
        LiquidGlassBackdrop(dark = dark, isScrolling = isScrolling, modifier = Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserMenuButton(
                    avatarUrl = avatarUrl,
                    onMenuClick = onMenuClick,
                    dark = dark,
                )
                Spacer(Modifier.weight(1f))
                SearchMenuButton(
                    onClick = onSearchClick,
                    dark = dark,
                )
                if (state.feedTab == FeedTab.LATEST) {
                    Spacer(Modifier.width(8.dp))
                    CategoryMenuButton(
                        active = state.category != PoipikuCategory.ALL,
                        onClick = onCategoryClick,
                        dark = dark,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TagMenuButton(onClick = onMoreTags, dark = dark)
            }
            FeedTabRow(
                feedTab = state.feedTab,
                currentTag = state.currentTag,
                currentKeyword = state.currentKeyword,
                onSelectFeedTab = onSelectFeedTab,
                onClearTag = onClearTag,
                onClearKeyword = onClearKeyword,
                dark = dark,
            )
        }
    }
}

@Composable
private fun TabletTopBar(
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreTags: () -> Unit,
    onDoubleTapTop: () -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onDoubleTapTop) {
                detectTapGestures(onDoubleTap = { onDoubleTapTop() })
            }
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserMenuButton(
            avatarUrl = avatarUrl,
            onMenuClick = onMenuClick,
            dark = dark,
        )
        Spacer(Modifier.weight(1f))
        SearchMenuButton(
            onClick = onSearchClick,
            dark = dark,
        )
        Spacer(Modifier.width(8.dp))
        TagMenuButton(onClick = onMoreTags, dark = dark)
    }
}

/**
 * 头部小图标按钮外壳：按下时轻微缩小、松手弹性回弹，增加触感。
 * 背景/边框固定为图标按钮样式，内容由调用方提供。
 */
@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    dark: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "iconPress",
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (dark) GlassIconBgDark else Color.White)
            .border(
                BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun SearchMenuButton(
    onClick: () -> Unit,
    dark: Boolean,
) {
    GlassIconButton(onClick = onClick, dark = dark) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = stringResource(R.string.search_placeholder),
            tint = if (dark) LoginTextPrimaryDark else Color(0xFF5A5A5A),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CategoryMenuButton(
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    GlassIconButton(onClick = onClick, dark = dark) {
        Icon(
            imageVector = Icons.Outlined.GridView,
            contentDescription = stringResource(R.string.home_category_select),
            tint = when {
                active -> if (dark) LoginTextPrimaryDark else AccentPurple
                // 暗色下未激活用半透明暖白：比灰色更清晰，同时与激活态保持亮度区分
                dark -> LoginTextPrimaryDark.copy(alpha = 0.55f)
                else -> Color(0xFF5A5A5A)
            },
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun FeedTabRow(
    feedTab: FeedTab,
    currentTag: String?,
    currentKeyword: String? = null,
    onSelectFeedTab: (FeedTab) -> Unit,
    onClearTag: () -> Unit,
    onClearKeyword: () -> Unit = {},
    dark: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 250))
            .padding(top = 4.dp, bottom = 6.dp),
    ) {
        if (currentTag != null || currentKeyword != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentKeyword != null) {
                    CurrentTagChip(
                        tag = currentKeyword,
                        prefix = "",
                        onClear = onClearKeyword,
                        dark = dark,
                    )
                } else {
                    CurrentTagChip(
                        tag = currentTag ?: "",
                        onClear = onClearTag,
                        dark = dark,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedTabItem(
                text = stringResource(R.string.home_tab_hot),
                active = feedTab == FeedTab.HOT,
                onClick = { onSelectFeedTab(FeedTab.HOT) },
                dark = dark,
            )
            Spacer(Modifier.width(20.dp))
            FeedTabItem(
                text = stringResource(R.string.home_tab_latest),
                active = feedTab == FeedTab.LATEST,
                onClick = { onSelectFeedTab(FeedTab.LATEST) },
                dark = dark,
            )
            Spacer(Modifier.width(20.dp))
            FeedTabItem(
                text = stringResource(R.string.home_tab_follow),
                active = feedTab == FeedTab.FOLLOW,
                onClick = { onSelectFeedTab(FeedTab.FOLLOW) },
                dark = dark,
            )
            Spacer(Modifier.width(20.dp))
            FeedTabItem(
                text = stringResource(R.string.home_tab_random),
                active = feedTab == FeedTab.RANDOM,
                onClick = { onSelectFeedTab(FeedTab.RANDOM) },
                dark = dark,
            )
        }
    }
}

@Composable
private fun FeedTabItem(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val underlineWidth by animateDpAsState(
        targetValue = if (active) 18.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabUnderline",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginTextPrimaryDark else Color(0xFF2C2C2C)
            else -> if (dark) LoginTextSecondaryDark else Color(0xFF8A8A8A)
        },
        animationSpec = tween(durationMillis = 200),
        label = "tabTextColor",
    )
    val underlineColor by animateColorAsState(
        targetValue = if (dark) LoginTextPrimaryDark else AccentPurple,
        animationSpec = tween(durationMillis = 200),
        label = "tabUnderlineColor",
    )
    // 选中时放大、未选中略微缩小，配合弹跳阻尼让切换更有手感
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabScale",
    )
    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(underlineWidth)
                .height(2.dp)
                // 下划线外圈光晕：随宽度/颜色一同生长，选中时带柔光质感
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(1.dp),
                    ambientColor = underlineColor,
                    spotColor = underlineColor,
                )
                .clip(RoundedCornerShape(1.dp))
                .background(underlineColor),
        )
    }
}

@Composable
private fun TagMenuButton(onClick: () -> Unit, dark: Boolean) {
    GlassIconButton(onClick = onClick, dark = dark) {
        Text(
            text = "#",
            color = if (dark) LoginTextPrimaryDark else Color(0xFF5A5A5A),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CurrentTagChip(
    tag: String,
    prefix: String = "#",
    onClear: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (dark) GlassCardBgDark else Color.White)
            .border(
                BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                shape,
            )
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$prefix$tag",
            color = if (dark) LoginTextPrimaryDark else AccentPurple,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.home_tag_clear),
                tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun TagPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    active -> if (dark) LoginTextPrimaryDark else Color(0xFF2C2C2C)
                    else -> if (dark) GlassCardBgDark else Color.White
                },
            )
            .border(
                BorderStroke(
                    0.5.dp,
                    when {
                        active -> if (dark) LoginTextPrimaryDark else Color(0xFF2C2C2C)
                        else -> if (dark) PillBorderDark else PillBorderLight
                    },
                ),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = if (active) {
                if (dark) LoginBackgroundDark else Color.White
            } else {
                if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
            },
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun UserMenuButton(
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    dark: Boolean,
) {
    // 头像背后的紫色光晕：缓慢呼吸脉动（明暗 + 半径），让头部有"活"的感觉
    val haloTransition = rememberInfiniteTransition(label = "avatarHalo")
    val haloAlpha by haloTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = if (dark) 0.34f else 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloAlpha",
    )
    val haloRadius by haloTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloRadius",
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9A7FC9).copy(alpha = haloAlpha),
                            Color.Transparent,
                        ),
                    ),
                    radius = size.minDimension / 2f * haloRadius,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // 无论是否登录都打开抽屉：未登录时抽屉内可点击头像登录
        UserAvatar(
            avatarUrl = avatarUrl,
            onClick = onMenuClick,
            dark = dark,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
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
    when {
        state.loading && state.works.isEmpty() -> {
            SkeletonGrid(dark = dark)
        }
        state.errorRes != null && state.works.isEmpty() -> {
            ErrorState(errorRes = state.errorRes, onRetry = onRetry, dark = dark)
        }
        state.works.isEmpty() && !state.loading -> {
            val emptyRes = when {
                // 关注页：未登录 → 登录引导；已登录但空 → 无关注内容
                state.feedTab == FeedTab.FOLLOW && state.followNeedLogin -> R.string.home_follow_login
                state.feedTab == FeedTab.FOLLOW -> R.string.home_follow_empty
                state.currentKeyword != null -> R.string.search_empty
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
                                .background(AccentPurple)
                                .clickable(onClick = onLoginClick)
                                .padding(horizontal = 24.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.login_button),
                                color = Color.White,
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
            tint = if (isNew) AccentPurple else if (dark) Color(0xFF81C784) else Color(0xFF4CAF50),
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    selected: PoipikuCategory,
    onSelect: (PoipikuCategory) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.home_category_select),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.home_category_current,
                    stringResource(selected.nameRes),
                ),
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            AllCategoriesButton(
                active = selected == PoipikuCategory.ALL,
                onClick = { onSelect(PoipikuCategory.ALL) },
                dark = dark,
            )
            Spacer(Modifier.height(20.dp))
            CATEGORY_GROUPS.forEach { group ->
                Text(
                    text = stringResource(group.titleRes),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    group.categories.forEach { category ->
                        CategoryPill(
                            text = stringResource(category.nameRes),
                            active = selected == category,
                            onClick = { onSelect(category) },
                            dark = dark,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(
    tags: List<PopularTag>,
    customTags: List<String>,
    currentTag: String?,
    onSelect: (String?) -> Unit,
    onSelectCustomTag: (String) -> Unit,
    onManageTags: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.home_tag_select),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_tag_hint),
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.menu_my_tags),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.my_tags_manage),
                    color = AccentPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onManageTags)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (customTags.isEmpty()) {
                Text(
                    text = stringResource(R.string.my_tags_empty),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 12.sp,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    customTags.forEach { tag ->
                        TagPill(
                            text = "#$tag",
                            active = tag == currentTag,
                            onClick = { onSelectCustomTag(tag) },
                            dark = dark,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TagPill(
                text = stringResource(R.string.home_category_all),
                active = currentTag == null,
                onClick = { onSelect(null) },
                dark = dark,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    TagPill(
                        text = "#${tag.name}",
                        active = tag.name == currentTag,
                        onClick = { onSelect(tag.name) },
                        dark = dark,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSheet(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.theme_select_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            SettingsOptionRow(
                text = stringResource(R.string.theme_mode_system),
                selected = selected == ThemeMode.SYSTEM,
                onClick = { onSelect(ThemeMode.SYSTEM) },
                dark = dark,
            )
            Spacer(Modifier.height(8.dp))
            SettingsOptionRow(
                text = stringResource(R.string.theme_mode_light),
                selected = selected == ThemeMode.LIGHT,
                onClick = { onSelect(ThemeMode.LIGHT) },
                dark = dark,
            )
            Spacer(Modifier.height(8.dp))
            SettingsOptionRow(
                text = stringResource(R.string.theme_mode_dark),
                selected = selected == ThemeMode.DARK,
                onClick = { onSelect(ThemeMode.DARK) },
                dark = dark,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionSheet(
    selectedDays: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.retention_select_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.retention_select_hint),
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            listOf(7, 30, 90, 0).forEach { days ->
                SettingsOptionRow(
                    text = stringResource(retentionDaysRes(days)),
                    selected = selectedDays == days,
                    onClick = { onSelect(days) },
                    dark = dark,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.language_select_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            AppLanguage.entries.forEach { language ->
                SettingsOptionRow(
                    text = stringResource(language.labelRes()),
                    selected = selected == language,
                    onClick = { onSelect(language) },
                    dark = dark,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val container = when {
        selected -> if (dark) {
            LoginTextPrimaryDark.copy(alpha = 0.14f)
        } else {
            LoginTextPrimaryLight.copy(alpha = 0.07f)
        }
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = AccentPurple,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun retentionDaysRes(days: Int): Int = when (days) {
    7 -> R.string.retention_7d
    30 -> R.string.retention_30d
    90 -> R.string.retention_90d
    else -> R.string.retention_forever
}

private data class CategoryGroup(
    val titleRes: Int,
    val categories: List<PoipikuCategory>,
)

private val CATEGORY_GROUPS = listOf(
    CategoryGroup(
        R.string.category_group_practice,
        listOf(
            PoipikuCategory.RAKUGAKI,
            PoipikuCategory.JISHUREN,
            PoipikuCategory.RIHABIRI,
        ),
    ),
    CategoryGroup(
        R.string.category_group_share,
        listOf(
            PoipikuCategory.DEKITA,
            PoipikuCategory.KAKO_WO_SARASU,
            PoipikuCategory.KUYOU,
        ),
    ),
    CategoryGroup(
        R.string.category_group_wip,
        listOf(
            PoipikuCategory.SAGYOSHINCHOKU,
            PoipikuCategory.KAKIKAKE,
            PoipikuCategory.KAKENEE,
        ),
    ),
    CategoryGroup(
        R.string.category_group_community,
        listOf(
            PoipikuCategory.OSHIRASE,
            PoipikuCategory.MEMO,
            PoipikuCategory.NETABARE,
            PoipikuCategory.SHIRIWOTATAKU,
            PoipikuCategory.OSHINAGAKI,
        ),
    ),
)

@Composable
private fun AllCategoriesButton(
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val container by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
            else -> if (dark) LoginCardDark else Color(0xFF000000).copy(alpha = 0.03f)
        },
        label = "allBtnBg",
    )
    val content by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginBackgroundDark else Color.White
            else -> if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
        },
        label = "allBtnText",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_category_all),
            color = content,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (active) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CategoryPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(20.dp)
    val container by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
            else -> if (dark) LoginCardDark else Color(0xFF000000).copy(alpha = 0.04f)
        },
        label = "pillBg",
    )
    val content by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginBackgroundDark else Color.White
            else -> if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
        },
        label = "pillText",
    )
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (active) Modifier.shadow(3.dp, shape) else Modifier)
            .background(container)
            .border(BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight), shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (active) 9.dp else 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 13.sp,
        )
        AnimatedVisibility(
            visible = active,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(14.dp),
            )
        }
    }
}

@Composable
private fun CategorySidebar(
    selected: PoipikuCategory,
    onSelect: (PoipikuCategory) -> Unit,
    dark: Boolean,
) {
    Column(
        Modifier
            .width(176.dp)
            .fillMaxHeight()
            .background(if (dark) LoginCardDark else Color.White)
            .border(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(vertical = 12.dp),
    ) {
        SidebarItem(
            text = stringResource(R.string.home_category_all),
            active = selected == PoipikuCategory.ALL,
            onClick = { onSelect(PoipikuCategory.ALL) },
            dark = dark,
        )
        PoipikuCategory.entries.filter { it != PoipikuCategory.ALL }.forEach { category ->
            SidebarItem(
                text = stringResource(category.nameRes),
                active = selected == category,
                onClick = { onSelect(category) },
                dark = dark,
            )
        }
    }
}

@Composable
private fun SidebarItem(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        targetValue = when {
            active -> if (dark) {
                LoginTextPrimaryDark.copy(alpha = 0.14f)
            } else {
                LoginTextPrimaryLight.copy(alpha = 0.07f)
            }
            else -> Color.Transparent
        },
        label = "sidebarBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (active) {
                        if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
                    } else {
                        Color.Transparent
                    },
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkWaterfall(
    works: List<Work>,
    favoriteIds: Set<String>,
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
        // A+ 方案：毛玻璃常开，不做 blur 开关翻转（避免每帧边界上的 effect 重建与重绘）；
        // blur 每帧成本由 glassInfo/glassHeader 的 inputScale=0.5 摊薄。
        // isScrolling 仅用于 FAB 显隐与"已是最新"提示自动消失。
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

    LaunchedEffect(works, gridState) {
        // 预取视口下方一屏的缩略图（与 AsyncImage 共用 SingletonImageLoader 磁盘缓存），
        // 滚动时新进入视口的卡片图片直接读磁盘，不触发网络请求，消除滚动首帧的加载突发。
        // 预取只暖磁盘缓存：按小尺寸解码且不入内存缓存，避免原图尺寸位图积压内存
        // （内存缓存 key 含尺寸，显示时按视图尺寸请求本来也命中不了原图条目）。
        val loader = SingletonImageLoader.get(prefetchContext)
        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        works
            .drop(lastVisible + 1)
            .take(PREFETCH_IMAGE_COUNT)
            .forEach { work ->
                loader.enqueue(
                    ImageRequest.Builder(prefetchContext)
                        .data(work.thumbnailUrl)
                        .size(CoilSize(512, 512))
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
                    isFavorite = work.id.toString() in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(work) },
                    onClick = { onWorkClick(work) },
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
        // isScrolling 的读取放在独立组合作用域内，滚动状态翻转时不会波及网格父级
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
    // 在 draw 阶段读取 progress：滚动中每帧只重绘 2dp 进度条，不触发重组与重新测量
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
            tint = if (dark) LoginTextPrimaryDark else AccentPurple,
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
                color = if (dark) LoginTextPrimaryDark else AccentPurple,
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
                color = if (dark) LoginTextPrimaryDark else AccentPurple,
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
            )
        }
    }
}