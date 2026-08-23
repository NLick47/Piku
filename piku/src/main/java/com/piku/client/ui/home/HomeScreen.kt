package com.piku.client.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.piku.client.BuildConfig
import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.GitHubRelease
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.ThemeMode
import com.piku.client.domain.model.Work
import java.io.File
import kotlin.math.roundToInt
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.common.feedThumbUrl
import com.piku.client.ui.profile.ProfileEditSheet
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
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
import com.piku.client.ui.theme.LoginButtonDark
import com.piku.client.ui.theme.LoginButtonLight
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardBorderDark
import com.piku.client.ui.theme.WorkCardPlaceholderDark
import com.piku.client.ui.theme.themedSwitchColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/** 首屏可见 item 数超过该值（约 6 行）时显示“回到顶部”悬浮按钮 */
private const val FAB_SHOW_AFTER_ITEMS = 12

private const val PREFETCH_IMAGE_COUNT = 12

private const val GO_TOP_ANIMATE_MAX_ITEMS = 50

/** 回顶：深位置直跳、近距离保留下滑动画 */
private fun LazyStaggeredGridState.scrollToTopSmart(scope: CoroutineScope) {
    scope.launch {
        if (firstVisibleItemIndex > GO_TOP_ANIMATE_MAX_ITEMS) {
            scrollToItem(0)
        } else {
            animateScrollToItem(0)
        }
    }
}

private const val GITHUB_REPO_URL = "https://github.com/NLick47/Piku"

private const val GITHUB_ISSUES_URL = "https://github.com/NLick47/Piku/issues"

/** 背景编辑预览模式：真实主页（所见即所得） */
private const val BG_PREVIEW_REAL = 0

/** 背景编辑预览模式：仅背景 + 完整蓝图辅助 */
private const val BG_PREVIEW_HIDDEN = 2

/** 背景编辑对象：头部层（清晰大图） */
private const val BG_EDIT_TARGET_HERO = 0

/** 背景编辑对象：背景层（毛玻璃，需已分离独立图） */
private const val BG_EDIT_TARGET_BACKDROP = 1

/** 暗色下自定义背景的额外压暗量（叠加在用户 dim 之上），让图片融入深色主题 */
private const val DARK_DIM_EXTRA = 0.08f

/** 暗色下自定义背景的压暗上限 */
private const val DARK_DIM_MAX = 0.85f

/** 暗色下 hero 中段遮罩透明度相对压暗值的系数（亮色沿用 0.15，保持艺术图清透） */
private const val DARK_DIM_MID_FACTOR = 0.50f

/** 暗色 tint 融合主题深色 HomeBgTopDark 的比例（0=纯图片色，1=纯主题色） */
private const val DARK_TINT_BLEND = 0.5f

/** 按比例把 [color] 融合进 [target]（仅 RGB，保留 alpha），让图片色融入主题色 */
private fun Color.blendInto(target: Color, t: Float): Color = Color(
    red = red * (1f - t) + target.red * t,
    green = green * (1f - t) + target.green * t,
    blue = blue * (1f - t) + target.blue * t,
    alpha = alpha,
)

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
    onSearchClick: () -> Unit,
    /** 登录后点头像进入自己的个人主页（userId, userName） */
    onProfileOpen: (Long, String) -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isBackgroundEditMode by rememberSaveable { mutableStateOf(false) }
    var bgPreviewMode by rememberSaveable { mutableIntStateOf(BG_PREVIEW_REAL) }
    var bgEditTarget by rememberSaveable { mutableIntStateOf(BG_EDIT_TARGET_HERO) }

    /** 编辑页参数栏折叠态：收起后只留细条，让背景图（尤其背景层）完整可见 */
    var bgPanelCollapsed by rememberSaveable { mutableStateOf(false) }
    val screenDensity = LocalDensity.current.density
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.setCustomBackground(uri)
            isBackgroundEditMode = true
        }
    }
    val pickBackdropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? ->
        if (uri != null) viewModel.setCustomBackdrop(uri)
    }
    val dark = LocalDarkTheme.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val context = LocalContext.current
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var originalOffsetX by remember { mutableFloatStateOf(0f) }
    var originalOffsetY by remember { mutableFloatStateOf(0f) }
    var originalDim by remember { mutableFloatStateOf(SettingsRepository.BACKGROUND_DIM_DEFAULT) }
    var originalScale by remember { mutableFloatStateOf(SettingsRepository.BACKGROUND_SCALE_DEFAULT) }
    var originalHeroOffsetX by remember { mutableFloatStateOf(0f) }
    var originalHeroOffsetY by remember { mutableFloatStateOf(0f) }
    var originalHeroScale by remember { mutableFloatStateOf(SettingsRepository.HERO_SCALE_DEFAULT) }
    var showRetentionSheet by rememberSaveable { mutableStateOf(false) }
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutSheet by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showProfileEdit by rememberSaveable { mutableStateOf(false) }
    val isScrolling = remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    var drawerOpenPending by remember { mutableStateOf(false) }
    val drawerButtonEnabled = !drawerOpenPending &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        drawerState.currentValue == DrawerValue.Closed &&
        drawerState.targetValue == DrawerValue.Closed

    val openDrawer = {
        if (drawerButtonEnabled) {
            drawerOpenPending = true
            scope.launch {
                try {
                    delay(180)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        drawerState.currentValue == DrawerValue.Closed &&
                        drawerState.targetValue == DrawerValue.Closed
                    ) {
                        drawerState.open()
                    }
                } finally {
                    drawerOpenPending = false
                }
            }
        }
    }

    LaunchedEffect(pendingTag) {
        if (pendingTag != null) {
            viewModel.selectTag(pendingTag)
            onTagConsumed()
        }
    }

    // 内容换血（tab/分类/标签切换、缓存恢复、重载、洗牌）后回到顶部；
    // 追加加载不递增 feedEpoch，不打断浏览位置
    LaunchedEffect(state.feedEpoch) {
        gridState.scrollToItem(0)
    }

    // 退出登录先弹确认框，确认后再登出（抽屉保留并显示未登录态，可在抽屉内直接点头像重新登录）
    val onLogout = { showLogoutConfirm = true }

    val onOpenUpdate = {
        val release = state.updateBanner
            ?: (state.updateCheckState as? UpdateCheckState.Available)?.release
        if (release != null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
        }
        viewModel.dismissUpdateBanner()
    }

    UserDrawer(
        drawerState = drawerState,
        userProfile = state.userProfile,
        adultEnabled = state.adultEnabled,
        themeMode = state.themeMode,
        customBackgroundPath = state.customBackgroundPath,
        historyRetentionDays = state.historyRetentionDays,
        language = state.language,
        currentVersion = if (BuildConfig.DEBUG && BuildConfig.DEBUG_VERSION_NAME.isNotBlank()) {
            BuildConfig.DEBUG_VERSION_NAME
        } else {
            BuildConfig.VERSION_NAME.substringBefore("-")
        },
        updateAvailable = state.updateCheckState is UpdateCheckState.Available,
        onToggleAdult = viewModel::toggleAdultContent,
        onAboutClick = { showAboutSheet = true },
        onThemeClick = { showThemeSheet = true },
        onBackgroundClick = {
            scope.launch { drawerState.close() }
            originalOffsetX = state.backgroundOffsetX
            originalOffsetY = state.backgroundOffsetY
            originalDim = state.backgroundDim
            originalScale = state.backgroundScale
            originalHeroOffsetX = state.heroOffsetX
            originalHeroOffsetY = state.heroOffsetY
            originalHeroScale = state.heroScale
            bgPreviewMode = BG_PREVIEW_REAL
            isBackgroundEditMode = true
        },
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
        onProfileOpen = {
            val profile = state.userProfile
            val uid = profile?.uid?.toLongOrNull()
            if (uid != null) {
                scope.launch { drawerState.close() }
                onProfileOpen(uid, profile.name.orEmpty())
            }
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
            val customBgPath = state.customBackgroundPath
            if (customBgPath != null) {
                CustomHomeBackground(
                    heroPath = customBgPath,
                    backdropPath = state.backdropPath,
                    dim = state.backgroundDim,
                    backdropScale = state.backgroundScale,
                    dark = dark,
                    scrimDark = state.backgroundScrimDark,
                    scrimLight = state.backgroundScrimLight,
                    heroOffsetX = state.heroOffsetX,
                    heroOffsetY = state.heroOffsetY,
                    heroScale = state.heroScale,
                    backdropOffsetX = state.backgroundOffsetX,
                    backdropOffsetY = state.backgroundOffsetY,
                    imgWidth = state.backgroundImgWidth,
                    imgHeight = state.backgroundImgHeight,
                    blurDp = state.backgroundBlur,
                    heroFraction = state.backgroundHeroFraction,
                    editMode = isBackgroundEditMode && bgPreviewMode != BG_PREVIEW_REAL,
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawHomeBackdrop(dark)
                }
            }

            val contentAlpha by animateFloatAsState(
                targetValue = if (!isBackgroundEditMode || bgPreviewMode == BG_PREVIEW_REAL) 1f else 0f,
                label = "contentAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha }
            ) {
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
                                    translucent = state.customBackgroundPath != null,
                                )
                                Column(Modifier.fillMaxWidth()) {
                                    TabletTopBar(
                                        avatarUrl = state.userAvatarUrl,
                                        onMenuClick = openDrawer,
                                        menuEnabled = drawerButtonEnabled,
                                        onSearchClick = onSearchClick,
                                        onDoubleTapTop = { gridState.scrollToTopSmart(scope) },
                                        dark = dark,
                                    )
                                    FeedTabRow(
                                        feedTab = state.feedTab,
                                        currentTag = state.currentTag,
                                    onSelectFeedTab = viewModel::selectFeedTab,
                                    onClearTag = { viewModel.selectTag(null) },
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
                                onGoTop = { gridState.scrollToTopSmart(scope) },
                                onOpenUpdate = onOpenUpdate,
                                onDismissUpdateBanner = viewModel::dismissUpdateBanner,
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
                            onMenuClick = openDrawer,
                            menuEnabled = drawerButtonEnabled,
                            onSearchClick = onSearchClick,
                            onSelectFeedTab = viewModel::selectFeedTab,
                            onCategoryClick = { showCategories = true },
                            onClearTag = { viewModel.selectTag(null) },
                            onDoubleTapTop = { gridState.scrollToTopSmart(scope) },
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
                            onGoTop = { gridState.scrollToTopSmart(scope) },
                            onOpenUpdate = onOpenUpdate,
                            onDismissUpdateBanner = viewModel::dismissUpdateBanner,
                            dark = dark,
                            isScrolling = isScrolling,
                            gridState = gridState,
                        )
                    }
                }
            }

            if (isBackgroundEditMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bgEditTarget, state.backdropPath) {
                // 下划线外圈光晕：随宽度/颜色一同生长，选中时带柔光质感
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (state.customBackgroundPath != null) {
                                    val editHero = bgEditTarget == BG_EDIT_TARGET_HERO ||
                                        state.backdropPath == null
                                    if (editHero) {
                                        val ns = (state.heroScale * zoom).coerceIn(
                                            SettingsRepository.HERO_SCALE_MIN,
                                            SettingsRepository.HERO_SCALE_MAX,
                                        )
                                        if (ns != state.heroScale) viewModel.setHeroScale(ns)
                                        var nx = state.heroOffsetX
                                        var ny = state.heroOffsetY
                                        val iw = state.backgroundImgWidth
                                        val ih = state.backgroundImgHeight
                                        if (ns >= 1f) {
                                            val (overflowX, overflowY) = cropOverflowPx(
                                                imgWidth = iw,
                                                imgHeight = ih,
                                                viewWidth = size.width,
                                                viewHeight = size.height,
                                                scale = ns,
                                            )
                                            if (overflowX > 1f) nx = state.heroOffsetX + pan.x / overflowX
                                            if (overflowY > 1f) ny = state.heroOffsetY + pan.y / overflowY
                                        } else if (iw != null && ih != null && iw > 0 && ih > 0) {
                                            val screenHdp = size.height / screenDensity
                                            val zoneHpx = (screenHdp * state.backgroundHeroFraction)
                                                .coerceIn(200f, 420f) * screenDensity
                                            val zoneWpx = size.width.toFloat()
                                            val fill = maxOf(zoneWpx / iw, zoneHpx / ih)
                                            val cardW = iw * fill * ns
                                            val cardH = ih * fill * ns
                                            val slackX = ((zoneWpx - cardW) / 2f).coerceAtLeast(0f)
                                            val slackY = ((zoneHpx - cardH) / 2f).coerceAtLeast(0f)
                                            if (slackX > 1f) nx = state.heroOffsetX + pan.x / slackX
                                            if (slackY > 1f) ny = state.heroOffsetY + pan.y / slackY
                                        }
                                        viewModel.setHeroOffset(nx, ny, persist = true)
                                    } else {
                                        val ns = (state.backgroundScale * zoom).coerceIn(
                                            SettingsRepository.BACKGROUND_SCALE_MIN,
                                            SettingsRepository.BACKGROUND_SCALE_MAX,
                                        )
                                        if (ns != state.backgroundScale) viewModel.setBackgroundScale(ns)
                                        val (overflowX, overflowY) = cropOverflowPx(
                                            imgWidth = state.backdropImgWidth,
                                            imgHeight = state.backdropImgHeight,
                                            viewWidth = size.width,
                                            viewHeight = size.height,
                                            scale = state.backgroundScale,
                                        )
                                        // 图片跟随手指：偏移与手指同向
                                        val nx = if (overflowX > 1f) {
                                            state.backgroundOffsetX + pan.x / overflowX
                                        } else state.backgroundOffsetX
                                        val ny = if (overflowY > 1f) {
                                            state.backgroundOffsetY + pan.y / overflowY
                                        } else state.backgroundOffsetY
                                        viewModel.setBackgroundOffset(nx, ny, persist = true)
                                    }
                                }
                            }
                        }
                        .pointerInput(bgEditTarget, state.backdropPath) {
                            // 双击复位：当前编辑对象的偏移回正中、缩放回默认
                            detectTapGestures(onDoubleTap = {
                                if (state.customBackgroundPath != null) {
                                    if (bgEditTarget == BG_EDIT_TARGET_BACKDROP &&
                                        state.backdropPath != null
                                    ) {
                                        viewModel.setBackgroundOffset(0f, 0f, persist = true)
                                        viewModel.setBackgroundScale(SettingsRepository.BACKGROUND_SCALE_DEFAULT)
                                    } else {
                                        viewModel.setHeroOffset(0f, 0f, persist = true)
                                        viewModel.setHeroScale(SettingsRepository.HERO_SCALE_DEFAULT)
                                    }
                                }
                            })
                        }
                )

                BackgroundBlueprintOverlay(
                    dark = dark,
                    heroHeightDp = (LocalConfiguration.current.screenHeightDp.dp * state.backgroundHeroFraction)
                        .coerceIn(200.dp, 420.dp),
                    offsetX = state.heroOffsetX,
                    offsetY = state.heroOffsetY,
                    imgWidth = state.backgroundImgWidth,
                    imgHeight = state.backgroundImgHeight,
                    scale = state.heroScale,
                    minimal = bgPreviewMode == BG_PREVIEW_REAL,
                    editTarget = bgEditTarget,
                    backdropOffsetX = state.backgroundOffsetX,
                    backdropOffsetY = state.backgroundOffsetY,
                    backdropSeparated = state.backdropPath != null,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val toggleBgPreview = {
                        bgPreviewMode = if (bgPreviewMode == BG_PREVIEW_REAL) {
                            BG_PREVIEW_HIDDEN
                        } else {
                            BG_PREVIEW_REAL
                        }
                    }
                    val panelCollapsed = bgPanelCollapsed && state.customBackgroundPath != null
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (dark) Color(0xE61C1A18) else Color(0xE6FFFFFF)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
                        if (panelCollapsed) {
                            // 收起态：细条常驻底部，只留预览切换与展开，把背景图让出来
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = toggleBgPreview,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (dark) {
                                            LoginTextPrimaryDark
                                        } else {
                                            LoginTextPrimaryLight
                                        }
                                    ),
                                ) {
                                    Icon(
                                        imageVector = if (bgPreviewMode == BG_PREVIEW_REAL) {
                                            Icons.Filled.Visibility
                                        } else {
                                            Icons.Filled.VisibilityOff
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (bgPreviewMode == BG_PREVIEW_REAL) {
                                                R.string.background_preview_real
                                            } else {
                                                R.string.background_preview_hidden
                                            }
                                        ),
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { bgPanelCollapsed = false }) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.background_panel_expand),
                                    )
                                }
                            }
                        } else {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .animateContentSize()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Wallpaper,
                                    contentDescription = null,
                                    tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.background_select_title),
                                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (state.customBackgroundPath != null) {
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { bgPanelCollapsed = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.background_panel_collapse),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (state.customBackgroundPath != null) {
                                    stringResource(R.string.background_drag_hint)
                                } else {
                                    stringResource(R.string.background_select_hint)
                                },
                                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(16.dp))

                            if (state.customBackgroundPath != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        BG_EDIT_TARGET_HERO to R.string.background_edit_target_hero,
                                        BG_EDIT_TARGET_BACKDROP to R.string.background_edit_target_backdrop,
                                    ).forEach { (target, labelRes) ->
                                        val selected = bgEditTarget == target
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(50))
                                                .background(
                                                    if (selected) {
                                                        if (dark) Color(0xFF6C538C) else Color(0xFFE8DEF8)
                                                    } else {
                                                        if (dark) Color(0xFF332F2B) else Color(0xFFF0EDE9)
                                                    }
                                                )
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) { bgEditTarget = target }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(labelRes),
                                                color = if (selected) {
                                                    if (dark) Color.White else Color(0xFF21005D)
                                                } else {
                                                    if (dark) Color.White else Color.Black
                                                },
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))

                                if (bgEditTarget == BG_EDIT_TARGET_BACKDROP) {
                                    // ---- 背景层（毛玻璃）----
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(
                                                if (state.backdropPath != null) {
                                                    R.string.background_backdrop_separated
                                                } else {
                                                    R.string.background_follow_hint
                                                }
                                            ),
                                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (state.backdropPath != null) {
                                            TextButton(
                                                onClick = { viewModel.restoreFollowBackground() },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = AccentDark
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.background_restore_follow),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.background_dim_label),
                                            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                                            fontSize = 13.sp,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "${(state.backgroundDim * 100).roundToInt()}%",
                                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Slider(
                                        value = state.backgroundDim,
                                        onValueChange = { viewModel.setBackgroundDim(it) },
                                        valueRange = 0f..SettingsRepository.BACKGROUND_DIM_MAX,
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.background_blur_label),
                                            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                                            fontSize = 13.sp,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "${state.backgroundBlur.roundToInt()}dp",
                                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Slider(
                                        value = state.backgroundBlur,
                                        onValueChange = { viewModel.setBackgroundBlur(it) },
                                        valueRange = SettingsRepository.BACKGROUND_BLUR_MIN..SettingsRepository.BACKGROUND_BLUR_MAX,
                                    )

                                    if (state.backdropPath != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.background_backdrop_scale_label),
                                                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                                                fontSize = 13.sp,
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                text = "${String.format("%.2f", state.backgroundScale)}x",
                                                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                                fontSize = 12.sp,
                                            )
                                        }
                                        Slider(
                                            value = state.backgroundScale,
                                            onValueChange = { viewModel.setBackgroundScale(it) },
                                            valueRange = SettingsRepository.BACKGROUND_SCALE_MIN..SettingsRepository.BACKGROUND_SCALE_MAX,
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.background_hero_scale_label),
                                            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                                            fontSize = 13.sp,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "${String.format("%.2f", state.heroScale)}x",
                                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Slider(
                                        value = heroScaleToSlider(state.heroScale),
                                        onValueChange = { viewModel.setHeroScale(sliderToHeroScale(it)) },
                                    )
                                    if (state.heroScale < 1f) {
                                        Text(
                                            text = stringResource(R.string.background_frame_hint),
                                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.background_hero_label),
                                            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                                            fontSize = 13.sp,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "${(state.backgroundHeroFraction * 100).roundToInt()}%",
                                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Slider(
                                        value = state.backgroundHeroFraction,
                                        onValueChange = { viewModel.setBackgroundHeroFraction(it) },
                                        valueRange = SettingsRepository.BACKGROUND_HERO_MIN..SettingsRepository.BACKGROUND_HERO_MAX,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            state.backgroundErrorRes?.let { res ->
                                Text(
                                    text = stringResource(res),
                                    color = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (state.customBackgroundPath != null &&
                                            bgEditTarget == BG_EDIT_TARGET_BACKDROP
                                        ) {
                                            pickBackdropLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        } else {
                                            pickBackgroundLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (dark) Color(0xFF332F2B) else Color(0xFFF0EDE9),
                                        contentColor = if (dark) Color.White else Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            when {
                                                state.customBackgroundPath == null -> R.string.background_pick_image
                                                bgEditTarget == BG_EDIT_TARGET_BACKDROP ->
                                                    if (state.backdropPath != null) {
                                                        R.string.background_change_backdrop
                                                    } else {
                                                        R.string.background_pick_backdrop
                                                    }
                                                else -> R.string.background_change_image
                                            }
                                        ),
                                        fontSize = 13.sp
                                    )
                                }

                                if (state.customBackgroundPath != null) {
                                    Button(
                                        onClick = toggleBgPreview,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (bgPreviewMode != BG_PREVIEW_REAL) {
                                                if (dark) Color(0xFF6C538C) else Color(0xFFE8DEF8)
                                            } else {
                                                if (dark) Color(0xFF332F2B) else Color(0xFFF0EDE9)
                                            },
                                            contentColor = if (bgPreviewMode != BG_PREVIEW_REAL) {
                                                if (dark) Color.White else Color(0xFF21005D)
                                            } else {
                                                if (dark) Color.White else Color.Black
                                            }
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (bgPreviewMode == BG_PREVIEW_REAL) {
                                                Icons.Filled.Visibility
                                            } else {
                                                Icons.Filled.VisibilityOff
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(
                                                if (bgPreviewMode == BG_PREVIEW_REAL) {
                                                    R.string.background_preview_real
                                                } else {
                                                    R.string.background_preview_hidden
                                                }
                                            ),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = if (dark) Color(0xFF2C2825) else Color(0xFFEAE7E4))
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.customBackgroundPath != null) {
                                    TextButton(
                                        onClick = {
                                            viewModel.clearCustomBackground()
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.background_reset),
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                TextButton(
                                    onClick = {
                                        viewModel.setHeroOffset(originalHeroOffsetX, originalHeroOffsetY, persist = true)
                                        viewModel.setHeroScale(originalHeroScale)
                                        viewModel.setBackgroundOffset(originalOffsetX, originalOffsetY, persist = true)
                                        viewModel.setBackgroundDim(originalDim)
                                        viewModel.setBackgroundScale(originalScale)
                                        isBackgroundEditMode = false
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (dark) LoginTextFaintDark else LoginTextFaintLight
                                    )
                                ) {
                                    Text(text = stringResource(R.string.search_cancel), fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.setHeroOffset(state.heroOffsetX, state.heroOffsetY, persist = true)
                                        viewModel.setHeroScale(state.heroScale)
                                        viewModel.setBackgroundOffset(state.backgroundOffsetX, state.backgroundOffsetY, persist = true)
                                        viewModel.setBackgroundDim(state.backgroundDim)
                                        viewModel.setBackgroundScale(state.backgroundScale)
                                        isBackgroundEditMode = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentDark,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(text = stringResource(R.string.profile_edit_save), fontSize = 13.sp)
                                }
                            }
                        }
                        }
                    }
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

            if (showLogoutConfirm) {
                LogoutConfirmDialog(
                    onConfirm = {
                        showLogoutConfirm = false
                        viewModel.logout()
                    },
                    onDismiss = { showLogoutConfirm = false },
                    dark = dark,
                )
            }

            if (showAboutSheet) {
                AboutSheet(
                    currentVersion = if (BuildConfig.DEBUG && BuildConfig.DEBUG_VERSION_NAME.isNotBlank()) {
                        BuildConfig.DEBUG_VERSION_NAME
                    } else {
                        BuildConfig.VERSION_NAME.substringBefore("-")
                    },
                    autoCheckEnabled = state.autoCheckEnabled,
                    updateCheckState = state.updateCheckState,
                    onToggleAutoCheck = { viewModel.setAutoCheckEnabled(!state.autoCheckEnabled) },
                    onCheckUpdate = {
                        if (state.updateCheckState !is UpdateCheckState.Checking) {
                            viewModel.checkForUpdateManual()
                        }
                    },
                    onOpenUpdate = onOpenUpdate,
                    onOpenGithub = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)))
                    },
                    onOpenFeedback = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_ISSUES_URL)))
                    },
                    onDismiss = { showAboutSheet = false },
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
        blob(Color(0x4D9A7FC9), size.width - 36.dp.toPx(), 140.dp.toPx(), 76.dp.toPx())
    }
    blob(blobWarm, 0f, 400.dp.toPx(), 100.dp.toPx())
    blob(blobPink, size.width, 620.dp.toPx(), 90.dp.toPx())
}

/**
 * 头部玻璃衬底的绘制：
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
    /** 有自定义背景时玻璃只做低透明度淡染，避免盖住背景图 */
    translucent: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var acc by rememberSaveable { mutableFloatStateOf(0f) }
    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val elapsedSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                    acc = (acc + elapsedSeconds / 12f) % 1f
                }
                lastFrameNanos = frameNanos
            }
        }
    }
    val liquid = acc
    val sheen = -0.5f + ((acc * 2f) % 1f) * 2f
    val deepen by animateFloatAsState(
        targetValue = if (isScrolling.value) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "glassDeepen",
    )
    val tintTop = if (dark) HomeBgTopDark else HomeBgTopLight
    val tintBottom = if (dark) Color(0xFF2B2533) else HomeBgTopLight
    // 自定义背景：玻璃只做极淡染（暗色补一点深色调，与图片、深色页面连成明暗坡度）；
    // 无背景时沿用标准玻璃浓度
    val tintTopAlpha = when {
        translucent && dark -> 0.16f + 0.05f * deepen
        translucent -> 0f
        dark -> 0.50f + 0.10f * deepen
        else -> 0.95f + 0.03f * deepen
    }
    val tintMidAlpha = when {
        translucent && dark -> 0.10f + 0.04f * deepen
        translucent -> 0f
        dark -> 0.32f + 0.14f * deepen
        else -> 0.80f + 0.08f * deepen
    }
    Box(modifier) {
        if (!translucent) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind { drawHeaderBackdrop(dark) },
            )
        }
        // tint 层
        Box(
            Modifier
                .matchParentSize()
                .background(
                    if (translucent) {
                        Brush.verticalGradient(
                            0f to tintTop.copy(alpha = tintTopAlpha),
                            0.6f to tintTop.copy(alpha = tintMidAlpha),
                            1f to Color.Transparent,
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                tintTop.copy(alpha = tintTopAlpha),
                                tintBottom.copy(alpha = tintMidAlpha),
                            ),
                        )
                    },
                ),
        )
        // 光泽层：自定义背景时关闭气泡/星光等装饰，只留顶部边缘高光与流光带
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawGlassShine(sheen, liquid, dark, decorations = !translucent) },
        )
    }
}

/**
 * 头部玻璃衬底的绘制：
 *   任何缩放下都能拖、永不露边；
 * - 顶层：分区渐变遮罩——状态栏处保持全浓度护字，头部突出区近乎全清透，
 * 背景/边框固定为图标按钮样式，内容由调用方提供。
 */
@Composable
private fun CustomHomeBackground(
    heroPath: String,
    backdropPath: String?,
    dim: Float,
    backdropScale: Float,
    dark: Boolean,
    scrimDark: Int?,
    scrimLight: Int?,
    heroOffsetX: Float = 0f,
    heroOffsetY: Float = 0f,
    heroScale: Float = SettingsRepository.HERO_SCALE_DEFAULT,
    backdropOffsetX: Float = 0f,
    backdropOffsetY: Float = 0f,
    imgWidth: Int? = null,
    imgHeight: Int? = null,
    blurDp: Float = SettingsRepository.BACKGROUND_BLUR_DEFAULT,
    heroFraction: Float = SettingsRepository.BACKGROUND_HERO_DEFAULT,
    editMode: Boolean = false,
) {
    val context = LocalContext.current
    val heroHeight = (LocalConfiguration.current.screenHeightDp.dp * heroFraction)
        .coerceIn(200.dp, 420.dp)
    val heroAlignment = BiasAlignment(heroOffsetX, heroOffsetY)
    val separated = backdropPath != null

    Box(Modifier.fillMaxSize()) {
        val frostScale = if (separated) backdropScale else heroScale.coerceAtLeast(1f)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(backdropPath ?: heroPath))
                .size(CoilSize(128, 128))
                .build(),
            contentDescription = null,
            alignment = if (separated) {
                BiasAlignment(backdropOffsetX, backdropOffsetY)
            } else {
                heroAlignment
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurDp.dp)
                .graphicsLayer {
                    scaleX = frostScale
                    scaleY = frostScale
                }
        )

        val heroImgW = imgWidth?.takeIf { it > 0 }
        val heroImgH = imgHeight?.takeIf { it > 0 }
        val framedT = if (heroImgW != null && heroImgH != null) {
            ((1f - heroScale) / (1f - SettingsRepository.HERO_SCALE_MIN)).coerceIn(0f, 1f)
        } else {
            0f
        }
        if (framedT > 0f && heroImgW != null && heroImgH != null) {
            // 画框式：满铺覆盖尺寸 × 缩放 = 卡片尺寸，圆角与投影随缩小程度渐显，
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(heroHeight),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val zoneWpx = with(density) { maxWidth.toPx() }
                val zoneHpx = with(density) { maxHeight.toPx() }
                val coverFactor = maxOf(zoneWpx / heroImgW, zoneHpx / heroImgH)
                val cardWpx = heroImgW * coverFactor * heroScale
                val cardHpx = heroImgH * coverFactor * heroScale
                val cardW = with(density) { cardWpx.toDp() }
                val cardH = with(density) { cardHpx.toDp() }
                val slackX = ((zoneWpx - cardWpx) / 2f).coerceAtLeast(0f)
                val slackY = ((zoneHpx - cardHpx) / 2f).coerceAtLeast(0f)
                val corner = lerp(0.dp, 24.dp, framedT)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(heroPath))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (heroOffsetX * slackX).roundToInt(),
                                (heroOffsetY * slackY).roundToInt(),
                            )
                        }
                        .size(cardW, cardH)
                        .shadow(elevation = 14.dp * framedT, shape = RoundedCornerShape(corner))
                        .clip(RoundedCornerShape(corner)),
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(heroPath))
                    .build(),
                contentDescription = null,
                alignment = heroAlignment,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // 视觉边界贴近分界线；正常态：45% 起缓慢溶入，过渡柔和
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                (if (editMode) 0.72f else 0.45f) to Color.Black,
                                (if (editMode) 0.96f else 1f) to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
            )
        }

        //    hero 渐隐结束后恢复全浓度，保住内容区卡片间的氛围
        //    暗色：用「图片暗色 + 主题深色」融合的 tint，并叠加额外压暗与更高的中段覆盖，
        //    让鲜艳原图融入深色页面；亮色沿用原曲线（中段近清透，保留艺术图观感）。
        val darkTint = (scrimDark?.let { Color(it) } ?: Color.Black)
            .blendInto(HomeBgTopDark, DARK_TINT_BLEND)
        val scrim = if (dark) darkTint else scrimLight?.let { Color(it) } ?: Color.White
        val dimBase = if (dark) (dim + DARK_DIM_EXTRA).coerceAtMost(DARK_DIM_MAX) else dim
        val midFactor = if (dark) DARK_DIM_MID_FACTOR else 0.15f
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val heroFrac = (heroHeight.toPx() / size.height).coerceIn(0f, 0.9f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to scrim.copy(alpha = dimBase),
                            (heroFrac * 0.18f) to scrim.copy(alpha = dimBase * midFactor),
                            (heroFrac + 0.04f).coerceAtMost(1f) to scrim.copy(alpha = dimBase),
                            1f to scrim.copy(alpha = dimBase * 0.6f),
                        ),
                    )
                },
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

private fun DrawScope.drawGlassShine(sheen: Float, liquid: Float, dark: Boolean, decorations: Boolean = true) {
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
    if (decorations) {
        // 液态气泡：从底部缓缓上浮，带轻微横向摆动，两端渐隐
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
    menuEnabled: Boolean,
    onSearchClick: () -> Unit,
    onSelectFeedTab: (FeedTab) -> Unit,
    onCategoryClick: () -> Unit,
    onClearTag: () -> Unit,
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
        LiquidGlassBackdrop(
            dark = dark,
            isScrolling = isScrolling,
            modifier = Modifier.matchParentSize(),
            translucent = state.customBackgroundPath != null,
        )
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
                    enabled = menuEnabled,
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
            }
            FeedTabRow(
                feedTab = state.feedTab,
                currentTag = state.currentTag,
                onSelectFeedTab = onSelectFeedTab,
                onClearTag = onClearTag,
                dark = dark,
            )
        }
    }
}

@Composable
private fun TabletTopBar(
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    menuEnabled: Boolean,
    onSearchClick: () -> Unit,
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
            enabled = menuEnabled,
            dark = dark,
        )
        Spacer(Modifier.weight(1f))
        SearchMenuButton(
            onClick = onSearchClick,
            dark = dark,
        )
    }
}

/**
 * 头部玻璃衬底的绘制：
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
                active -> if (dark) LoginTextPrimaryDark else AccentDark
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
    onSelectFeedTab: (FeedTab) -> Unit,
    onClearTag: () -> Unit,
    dark: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 250))
            .padding(top = 4.dp, bottom = 6.dp),
    ) {
        if (currentTag != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CurrentTagChip(
                    tag = currentTag,
                    onClear = onClearTag,
                    dark = dark,
                )
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
        targetValue = if (!active) 0.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabUnderline",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginTextPrimaryDark else AccentDark
            else -> if (dark) LoginTextSecondaryDark else Color(0xFF8A8A8A)
        },
        animationSpec = tween(durationMillis = 200),
        label = "tabTextColor",
    )
    val underlineColor by animateColorAsState(
        targetValue = if (dark) LoginTextPrimaryDark else AccentDark,
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
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(4.dp),
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
                .clip(RoundedCornerShape(1.dp))
                .background(underlineColor),
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
            color = if (dark) LoginTextPrimaryDark else AccentDark,
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
private fun UserMenuButton(
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    enabled: Boolean,
    dark: Boolean,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9A7FC9).copy(alpha = if (dark) 0.24f else 0.18f),
                            Color.Transparent,
                        ),
                    ),
                    radius = size.minDimension / 2f * 1.25f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            onClick = onMenuClick,
            dark = dark,
            enabled = enabled,
            showIndication = false,
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
private fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginCardDark else Color.White,
        title = {
            Text(
                text = stringResource(R.string.logout),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.logout_confirm_message),
                color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.logout_confirm),
                    color = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.detail_favorite_cancel),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                )
            }
        },
    )
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
private fun AboutSheet(
    currentVersion: String,
    autoCheckEnabled: Boolean,
    updateCheckState: UpdateCheckState,
    onToggleAutoCheck: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenFeedback: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    val divider = if (dark) LoginCardBorderDark else LoginCardBorderLight
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.menu_about),
                color = primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            AboutUpdateButton(
                state = updateCheckState,
                onCheckUpdate = onCheckUpdate,
                onOpenUpdate = onOpenUpdate,
                dark = dark,
            )
            Spacer(Modifier.height(4.dp))
            AboutToggleRow(
                text = stringResource(R.string.menu_auto_check_update),
                checked = autoCheckEnabled,
                onToggle = onToggleAutoCheck,
                dark = dark,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = divider)
            Spacer(Modifier.height(6.dp))
            // 当前版本（次级信息）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.about_version, "v$currentVersion"),
                    color = faint,
                    fontSize = 12.sp,
                )
            }
            AboutLinkRow(
                text = stringResource(R.string.menu_github),
                onClick = onOpenGithub,
                dark = dark,
            )
            AboutLinkRow(
                text = stringResource(R.string.menu_feedback),
                onClick = onOpenFeedback,
                dark = dark,
            )
        }
    }
}

@Composable
private fun AboutUpdateButton(
    state: UpdateCheckState,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    dark: Boolean,
) {
    val green = if (dark) FollowDark else FollowLight
    val red = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B)
    val (targetBg, targetFg) = when (state) {
        UpdateCheckState.Checking -> AccentDark to Color.White
        UpdateCheckState.Latest -> green.copy(alpha = if (dark) 0.22f else 0.12f) to green
        UpdateCheckState.Failed -> red.copy(alpha = if (dark) 0.22f else 0.12f) to red
        else -> AccentDark to Color.White
    }
    val bg by animateColorAsState(targetBg, label = "updateBtnBg")
    val fg by animateColorAsState(targetFg, label = "updateBtnFg")
    val shape = RoundedCornerShape(16.dp)
    val clickable = state !is UpdateCheckState.Checking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(bg)
            .clickable(enabled = clickable) {
                when (state) {
                    UpdateCheckState.Checking -> Unit
                    is UpdateCheckState.Available -> onOpenUpdate()
                    else -> onCheckUpdate()
                }
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            UpdateCheckState.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            UpdateCheckState.Latest -> {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            else -> Unit
        }
        Text(
            text = when (val s = state) {
                UpdateCheckState.Checking -> stringResource(R.string.about_checking)
                UpdateCheckState.Latest -> stringResource(R.string.about_latest)
                is UpdateCheckState.Available -> stringResource(
                    R.string.about_update_download,
                    s.release.tagName,
                )
                UpdateCheckState.Failed -> stringResource(R.string.about_retry)
                UpdateCheckState.Idle -> stringResource(R.string.menu_check_update)
            },
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AboutToggleRow(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = themedSwitchColors(dark),
        )
    }
}

@Composable
private fun AboutLinkRow(
    text: String,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = faint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = faint,
            modifier = Modifier.size(14.dp),
        )
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
                tint = AccentDark,
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
        // 预取视口下方一屏的缩略图（与 AsyncImage 共用 SingletonImageLoader 磁盘缓存），
        // （内存缓存 key 含尺寸，显示时按视图尺寸请求本来也命中不了原图条目）。
        // key 用末元素 id：追加页触发；thumbUpdated 重建同内容列表不会误触发整段重跑
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

/**
 * 背景编辑蓝图覆盖层（高对比分区可视化）：
 * - 清晰区：四角取景括号框出头部取样范围（相机取景框样式）；
 * - 头部画框式（缩放 <1）：虚线圆角矩形框出卡片实际占位，直观看到留白范围；
 *
 */
@Composable
private fun BackgroundBlueprintOverlay(
    dark: Boolean,
    heroHeightDp: Dp,
    offsetX: Float,
    offsetY: Float,
    imgWidth: Int?,
    imgHeight: Int?,
    scale: Float,
    minimal: Boolean = false,
    editTarget: Int = BG_EDIT_TARGET_HERO,
    backdropOffsetX: Float = 0f,
    backdropOffsetY: Float = 0f,
    backdropSeparated: Boolean = false,
) {
    val lineColor = if (dark) {
        Color.White.copy(alpha = if (minimal) 0.55f else 0.7f)
    } else {
        Color.Black.copy(alpha = if (minimal) 0.45f else 0.55f)
    }
    val textColor = if (dark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.6f)
    // 编辑对象为背景层时，头部元素整体弱化，突出当前正在调整的图层
    val editingBackdrop = editTarget == BG_EDIT_TARGET_BACKDROP && backdropSeparated
    val heroLineColor = if (editingBackdrop) {
        lineColor.copy(alpha = lineColor.alpha * 0.4f)
    } else {
        lineColor
    }
    // 蚂蚁线相位动画：dash 沿分界线持续流动
    val antsPhase by rememberInfiniteTransition(label = "ants").animateFloat(
        initialValue = 0f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "antsPhase",
    )
    val antsEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -antsPhase)
    val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val heroPx = heroHeightDp.toPx().coerceIn(0f, size.height)

            if (!minimal) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.08f),
                    topLeft = Offset(0f, heroPx),
                    size = Size(size.width, size.height - heroPx),
                )
                val hatchGap = 16.dp.toPx()
                val zoneH = size.height - heroPx
                var hx = -zoneH
                while (hx < size.width) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.25f),
                        start = Offset(hx, heroPx),
                        end = Offset(hx + zoneH, size.height),
                        strokeWidth = 1f,
                    )
                    hx += hatchGap
                }
            }

            val inset = 12.dp.toPx()
            val bracketLen = 22.dp.toPx()
            val bracketStroke = 2.5.dp.toPx()
            listOf(
                Triple(Offset(inset, inset), 1f, 1f),
                Triple(Offset(size.width - inset, inset), -1f, 1f),
                Triple(Offset(inset, heroPx - inset), 1f, -1f),
                Triple(Offset(size.width - inset, heroPx - inset), -1f, -1f),
            ).forEach { (pos, dx, dy) ->
                drawLine(heroLineColor, pos, Offset(pos.x + bracketLen * dx, pos.y), bracketStroke)
                drawLine(heroLineColor, pos, Offset(pos.x, pos.y + bracketLen * dy), bracketStroke)
            }

            if (!minimal && imgWidth != null && imgHeight != null && imgWidth > 0 && imgHeight > 0) {
                val framedT = ((1f - scale) / (1f - SettingsRepository.HERO_SCALE_MIN)).coerceIn(0f, 1f)
                if (framedT > 0f) {
                    val coverFactor = maxOf(size.width / imgWidth, heroPx / imgHeight)
                    val cardW = imgWidth * coverFactor * scale
                    val cardH = imgHeight * coverFactor * scale
                    val slackX = ((size.width - cardW) / 2f).coerceAtLeast(0f)
                    val slackY = ((heroPx - cardH) / 2f).coerceAtLeast(0f)
                    drawRoundRect(
                        color = heroLineColor,
                        topLeft = Offset(
                            size.width / 2f - cardW / 2f + offsetX * slackX,
                            heroPx / 2f - cardH / 2f + offsetY * slackY,
                        ),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(24.dp.toPx() * framedT),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect),
                    )
                }
            }

            if (!minimal && editingBackdrop) {
                val borderInset = 10.dp.toPx()
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(borderInset, borderInset),
                    size = Size(
                        size.width - borderInset * 2,
                        size.height - borderInset * 2,
                    ),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            drawLine(
                color = heroLineColor,
                start = Offset(0f, heroPx),
                end = Offset(size.width, heroPx),
                strokeWidth = 2.dp.toPx(),
                pathEffect = antsEffect,
            )

            if (!minimal) {
                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 0.8f,
                    pathEffect = dashEffect,
                )
                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 0.8f,
                    pathEffect = dashEffect,
                )
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 1.2f),
                )
            }

            if (!minimal) {
                val (overflowX, overflowY) = cropOverflowPx(
                    imgWidth, imgHeight, size.width.toInt(), size.height.toInt(), scale,
                )
                val markLen = 48.dp.toPx()
                if (overflowX > 1f) {
                    listOf(
                        Offset(6f, size.height / 2f),
                        Offset(size.width - 6f, size.height / 2f),
                    ).forEach { c ->
                        drawLine(
                            color = lineColor,
                            start = Offset(c.x, c.y - markLen / 2f),
                            end = Offset(c.x, c.y + markLen / 2f),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
                if (overflowY > 1f) {
                    listOf(
                        Offset(size.width / 2f, 6f),
                        Offset(size.width / 2f, size.height - 6f),
                    ).forEach { c ->
                        drawLine(
                            color = lineColor,
                            start = Offset(c.x - markLen / 2f, c.y),
                            end = Offset(c.x + markLen / 2f, c.y),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
            }
        }

        if (!minimal) {
            Text(
                text = stringResource(R.string.background_zone_sharp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = (heroHeightDp - 44.dp).coerceAtLeast(8.dp))
                    .background(Color(0xE600BCD4), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.background_zone_blur),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = heroHeightDp + 12.dp)
                    .background(Color(0xE67C4DFF), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        val readoutX = if (editingBackdrop) backdropOffsetX else offsetX
        val readoutY = if (editingBackdrop) backdropOffsetY else offsetY
        Text(
            text = stringResource(
                R.string.background_offset_readout,
                (readoutX * 100).roundToInt(),
                (readoutY * 100).roundToInt(),
            ),
            color = textColor,
            fontSize = if (minimal) 10.sp else 11.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (minimal) heroHeightDp + 10.dp else heroHeightDp + 56.dp)
                .background(
                    color = if (dark) {
                        Color.Black.copy(alpha = if (minimal) 0.25f else 0.35f)
                    } else {
                        Color.White.copy(alpha = if (minimal) 0.45f else 0.55f)
                    },
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = if (minimal) 8.dp else 12.dp, vertical = 3.dp),
        )
    }
}

/**
 */
private fun cropOverflowPx(
    imgWidth: Int?,
    imgHeight: Int?,
    viewWidth: Int,
    viewHeight: Int,
    scale: Float,
): Pair<Float, Float> {
    if (imgWidth == null || imgHeight == null || imgWidth <= 0 || imgHeight <= 0 ||
        viewWidth <= 0 || viewHeight <= 0
    ) {
        return (viewWidth * 0.25f) to (viewHeight * 0.25f)
    }
    val fillScale = maxOf(
        viewWidth.toFloat() / imgWidth,
        viewHeight.toFloat() / imgHeight,
    ) * scale
    val overflowX = ((imgWidth * fillScale - viewWidth) / 2f).coerceAtLeast(0f)
    val overflowY = ((imgHeight * fillScale - viewHeight) / 2f).coerceAtLeast(0f)
    return overflowX to overflowY
}

/**
 */
private fun sliderToHeroScale(t: Float): Float =
    exp(
        ln(SettingsRepository.HERO_SCALE_MIN) +
            t.coerceIn(0f, 1f) * (ln(SettingsRepository.HERO_SCALE_MAX) - ln(SettingsRepository.HERO_SCALE_MIN))
    )

private fun heroScaleToSlider(scale: Float): Float =
    ((ln(scale) - ln(SettingsRepository.HERO_SCALE_MIN)) /
        (ln(SettingsRepository.HERO_SCALE_MAX) - ln(SettingsRepository.HERO_SCALE_MIN)))
        .coerceIn(0f, 1f)
