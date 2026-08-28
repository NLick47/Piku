package com.piku.client.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.piku.client.BuildConfig
import com.piku.client.R
import com.piku.client.data.local.CatalogSource
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.Work
import com.piku.client.ui.profile.ProfileEditSheet
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GITHUB_REPO_URL = "https://github.com/NLick47/Piku"
private const val GITHUB_ISSUES_URL = "https://github.com/NLick47/Piku/issues"

@Composable
fun HomeScreen(
    pendingTag: String?,
    onTagConsumed: () -> Unit,
    shouldReopenDrawer: Boolean = false,
    onDrawerReopenConsumed: () -> Unit = {},
    onWorkClick: (Work) -> Unit,
    onLoginClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onTagsClick: () -> Unit,
    onFollowUsersClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileOpen: (Long, String) -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isBackgroundEditMode by rememberSaveable { mutableStateOf(false) }
    var bgPreviewMode by rememberSaveable { mutableIntStateOf(BG_PREVIEW_REAL) }
    var bgEditTarget by rememberSaveable { mutableIntStateOf(BG_EDIT_TARGET_HERO) }
    var bgPanelCollapsed by rememberSaveable { mutableStateOf(false) }
    val screenDensity = LocalDensity.current.density
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomBackground(uri)
            isBackgroundEditMode = true
        }
    }
    val pickBackdropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
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
    var originalBlur by remember { mutableFloatStateOf(SettingsRepository.BACKGROUND_BLUR_DEFAULT) }
    var originalHeroFraction by remember { mutableFloatStateOf(SettingsRepository.BACKGROUND_HERO_DEFAULT) }
    var showRetentionSheet by rememberSaveable { mutableStateOf(false) }
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showAiTranslateSheet by rememberSaveable { mutableStateOf(false) }
    var showCatalogSource by rememberSaveable { mutableStateOf(false) }
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

    val openDrawer = remember(drawerButtonEnabled) {
        {
            if (drawerButtonEnabled) {
                drawerOpenPending = true
                scope.launch {
                    try {
                        drawerState.open()
                    } finally {
                        drawerOpenPending = false
                    }
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

    LaunchedEffect(shouldReopenDrawer) {
        if (shouldReopenDrawer) {
            drawerState.open()
            onDrawerReopenConsumed()
        }
    }

    var seenFeedEpoch by remember { mutableIntStateOf(state.feedEpoch) }
    LaunchedEffect(state.feedEpoch) {
        if (state.feedEpoch != seenFeedEpoch) {
            gridState.scrollToItem(0)
            seenFeedEpoch = state.feedEpoch
        }
    }

    val onLogout = remember { { showLogoutConfirm = true } }

    val onOpenUpdate = remember(state.updateBanner, state.updateCheckState) {
        {
            val release = state.updateBanner
                ?: (state.updateCheckState as? UpdateCheckState.Available)?.release
            if (release != null) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
            }
            viewModel.dismissUpdateBanner()
        }
    }

    UserDrawer(
        drawerState = drawerState,
        userProfile = state.userProfile,
        adultEnabled = state.adultEnabled,
        themeMode = state.themeMode,
        customBackgroundPath = state.customBackgroundPath,
        historyRetentionDays = state.historyRetentionDays,
        language = state.language,
        aiTranslateEnabled = state.aiTranslateEnabled,
        currentVersion = displayVersionName(),
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
            originalBlur = state.backgroundBlur
            originalHeroFraction = state.backgroundHeroFraction
            bgPreviewMode = BG_PREVIEW_REAL
            isBackgroundEditMode = true
        },
        onRetentionClick = { showRetentionSheet = true },
        onLanguageClick = { showLanguageSheet = true },
        onAiTranslateClick = { showAiTranslateSheet = true },
        onHistoryClick = { onHistoryClick() },
        onCollectionClick = { onCollectionClick() },
        onTagsClick = { onTagsClick() },
        onFollowUsersClick = { onFollowUsersClick() },
        onProfileClick = { showProfileEdit = true },
        onProfileOpen = {
            val profile = state.userProfile
            val uid = profile?.uid?.toLongOrNull()
            if (uid != null) {
                onProfileOpen(uid, profile.name.orEmpty())
            }
        },
        onLoginClick = {
            onLoginClick()
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
                            drawerIsOpen = drawerState.isOpen,
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
                                        viewModel.setHeroOffset(nx, ny)
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
                                        val nx = if (overflowX > 1f) {
                                            state.backgroundOffsetX + pan.x / overflowX
                                        } else state.backgroundOffsetX
                                        val ny = if (overflowY > 1f) {
                                            state.backgroundOffsetY + pan.y / overflowY
                                        } else state.backgroundOffsetY
                                        viewModel.setBackgroundOffset(nx, ny)
                                    }
                                }
                            }
                        }
                        .pointerInput(bgEditTarget, state.backdropPath) {
                            detectTapGestures(onDoubleTap = {
                                if (state.customBackgroundPath != null) {
                                    if (bgEditTarget == BG_EDIT_TARGET_BACKDROP &&
                                        state.backdropPath != null
                                    ) {
                                        viewModel.setBackgroundOffset(0f, 0f, persist = true)
                                        viewModel.setBackgroundScale(
                                            SettingsRepository.BACKGROUND_SCALE_DEFAULT,
                                            persist = true,
                                        )
                                    } else {
                                        viewModel.setHeroOffset(0f, 0f, persist = true)
                                        viewModel.setHeroScale(
                                            SettingsRepository.HERO_SCALE_DEFAULT,
                                            persist = true,
                                        )
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
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = toggleBgPreview,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
                                    ),
                                ) {
                                    Icon(
                                        imageVector = if (bgPreviewMode == BG_PREVIEW_REAL) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (bgPreviewMode == BG_PREVIEW_REAL) R.string.background_preview_real else R.string.background_preview_hidden
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
                            BackgroundEditPanel(
                                state = state,
                                bgPreviewMode = bgPreviewMode,
                                bgEditTarget = bgEditTarget,
                                dark = dark,
                                onTogglePreview = toggleBgPreview,
                                onSelectTarget = { bgEditTarget = it },
                                onCollapse = { bgPanelCollapsed = true },
                                onPickImage = {
                                    if (bgEditTarget == BG_EDIT_TARGET_BACKDROP) {
                                        pickBackdropLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } else {
                                        pickBackgroundLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                onClearBackground = { viewModel.clearCustomBackground() },
                                onRestoreFollow = { viewModel.restoreFollowBackground() },
                                onDismiss = {
                                    viewModel.persistHeroOffset()
                                    viewModel.setBackgroundOffset(
                                        state.backgroundOffsetX, state.backgroundOffsetY, persist = true,
                                    )
                                    viewModel.setBackgroundDim(state.backgroundDim, persist = true)
                                    viewModel.setBackgroundScale(state.backgroundScale, persist = true)
                                    viewModel.setBackgroundBlur(state.backgroundBlur, persist = true)
                                    viewModel.setHeroScale(state.heroScale, persist = true)
                                    viewModel.setBackgroundHeroFraction(state.backgroundHeroFraction, persist = true)
                                    isBackgroundEditMode = false
                                },
                                onConfirm = {
                                    viewModel.setHeroOffset(state.heroOffsetX, state.heroOffsetY, persist = true)
                                    viewModel.setHeroScale(state.heroScale, persist = true)
                                    viewModel.setBackgroundOffset(
                                        state.backgroundOffsetX, state.backgroundOffsetY, persist = true,
                                    )
                                    viewModel.setBackgroundDim(state.backgroundDim, persist = true)
                                    viewModel.setBackgroundScale(state.backgroundScale, persist = true)
                                    viewModel.setBackgroundBlur(state.backgroundBlur, persist = true)
                                    viewModel.setBackgroundHeroFraction(state.backgroundHeroFraction, persist = true)
                                    isBackgroundEditMode = false
                                },
                                onRevert = {
                                    viewModel.setHeroOffset(originalHeroOffsetX, originalHeroOffsetY, persist = true)
                                    viewModel.setHeroScale(originalHeroScale, persist = true)
                                    viewModel.setBackgroundOffset(
                                        originalOffsetX, originalOffsetY, persist = true,
                                    )
                                    viewModel.setBackgroundDim(originalDim, persist = true)
                                    viewModel.setBackgroundScale(originalScale, persist = true)
                                    viewModel.setBackgroundBlur(originalBlur, persist = true)
                                    viewModel.setBackgroundHeroFraction(originalHeroFraction, persist = true)
                                    isBackgroundEditMode = false
                                },
                                onBackgroundDimChange = { viewModel.setBackgroundDim(it) },
                                onBackgroundBlurChange = { viewModel.setBackgroundBlur(it) },
                                onBackgroundScaleChange = { viewModel.setBackgroundScale(it) },
                                onHeroScaleChange = { viewModel.setHeroScale(it) },
                                onHeroFractionChange = { viewModel.setBackgroundHeroFraction(it) },
                                onSettingsFinished = {
                                    viewModel.persistHeroOffset()
                                    viewModel.setBackgroundOffset(
                                        state.backgroundOffsetX, state.backgroundOffsetY, persist = true,
                                    )
                                    viewModel.setBackgroundDim(state.backgroundDim, persist = true)
                                    viewModel.setBackgroundScale(state.backgroundScale, persist = true)
                                    viewModel.setBackgroundBlur(state.backgroundBlur, persist = true)
                                    viewModel.setHeroScale(state.heroScale, persist = true)
                                    viewModel.setBackgroundHeroFraction(state.backgroundHeroFraction, persist = true)
                                },
                            )
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
                        (context as? Activity)?.recreate()
                    },
                    onDismiss = { showLanguageSheet = false },
                    dark = dark,
                )
            }

            if (showAiTranslateSheet) {
                AiTranslateSheet(
                    state = state,
                    onToggleEnabled = viewModel::setAiTranslateEnabled,
                    onSelectModel = viewModel::selectTranslateModel,
                    onSelectNovelModel = viewModel::selectTranslateNovelModel,
                    onSaveCatalog = viewModel::saveCatalog,
                    onResetCatalog = viewModel::resetCatalogUrl,
                    onActivateSource = viewModel::activateCatalogSource,
                    onSaveAsSource = { url, key -> viewModel.saveCatalogAsSource(null, url, key) },
                    onRenameSource = { source, name -> viewModel.renameCatalogSource(source.id, name) },
                    onDeleteSource = { source -> viewModel.deleteCatalogSource(source.id) },
                    onOpenSources = { showCatalogSource = true },
                    catalogOpen = showCatalogSource,
                    onDismiss = { showAiTranslateSheet = false },
                    dark = dark,
                )
            }

            if (showCatalogSource) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showCatalogSource = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                        dismissOnClickOutside = false,
                    ),
                ) {
                    CatalogSourceScreen(
                        state = state,
                        onSaveCatalog = viewModel::saveCatalog,
                        onResetCatalog = viewModel::resetCatalogUrl,
                        onActivateSource = viewModel::activateCatalogSource,
                        onSaveAsSource = { url, key -> viewModel.saveCatalogAsSource(null, url, key) },
                        onRenameSource = { source, name -> viewModel.renameCatalogSource(source.id, name) },
                        onDeleteSource = { source -> viewModel.deleteCatalogSource(source.id) },
                        onBack = { showCatalogSource = false },
                        dark = dark,
                    )
                }
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
                    currentVersion = displayVersionName(),
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
 * 背景编辑底部面板：参数控制面板。
 * 从 HomeScreen 的巨大 Card 内容区抽取，避免主函数超过 800 行。
 */
@Composable
private fun BackgroundEditPanel(
    state: HomeUiState,
    bgPreviewMode: Int,
    bgEditTarget: Int,
    dark: Boolean,
    onTogglePreview: () -> Unit,
    onSelectTarget: (Int) -> Unit,
    onCollapse: () -> Unit,
    onPickImage: () -> Unit,
    onClearBackground: () -> Unit,
    onRestoreFollow: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRevert: () -> Unit,
    onBackgroundDimChange: (Float) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onBackgroundScaleChange: (Float) -> Unit,
    onHeroScaleChange: (Float) -> Unit,
    onHeroFractionChange: (Float) -> Unit,
    onSettingsFinished: () -> Unit,
) {

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
                IconButton(onClick = onCollapse) {
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
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
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
                            ) { onSelectTarget(target) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (state.backdropPath != null) R.string.background_backdrop_separated else R.string.background_follow_hint
                        ),
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.backdropPath != null) {
                        TextButton(
                            onClick = onRestoreFollow,
                            colors = ButtonDefaults.textButtonColors(contentColor = AccentDark),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
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
                    onValueChange = onBackgroundDimChange,
                    onValueChangeFinished = onSettingsFinished,
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
                    onValueChange = onBackgroundBlurChange,
                    onValueChangeFinished = onSettingsFinished,
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
                        onValueChange = onBackgroundScaleChange,
                        onValueChangeFinished = onSettingsFinished,
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
                    onValueChange = { onHeroScaleChange(sliderToHeroScale(it)) },
                    onValueChangeFinished = onSettingsFinished,
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
                    onValueChange = onHeroFractionChange,
                    onValueChangeFinished = onSettingsFinished,
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPickImage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Color(0xFF332F2B) else Color(0xFFF0EDE9),
                    contentColor = if (dark) Color.White else Color.Black
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                                if (state.backdropPath != null) R.string.background_change_backdrop else R.string.background_pick_backdrop
                            else -> R.string.background_change_image
                        }
                    ),
                    fontSize = 13.sp
                )
            }

            if (state.customBackgroundPath != null) {
                Button(
                    onClick = onTogglePreview,
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (bgPreviewMode == BG_PREVIEW_REAL) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (bgPreviewMode == BG_PREVIEW_REAL) R.string.background_preview_real else R.string.background_preview_hidden
                        ),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.HorizontalDivider(color = if (dark) Color(0xFF2C2825) else Color(0xFFEAE7E4))
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.customBackgroundPath != null) {
                TextButton(
                    onClick = onClearBackground,
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
                onClick = onRevert,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (dark) LoginTextFaintDark else LoginTextFaintLight
                )
            ) {
                Text(text = stringResource(R.string.search_cancel), fontSize = 13.sp)
            }

            Button(
                onClick = onConfirm,
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

/** 显示用版本号：debug 构建用 DEBUG_VERSION_NAME，release 去掉 "-xxx" 后缀 */
private fun displayVersionName(): String =
    if (BuildConfig.DEBUG && BuildConfig.DEBUG_VERSION_NAME.isNotBlank()) {
        BuildConfig.DEBUG_VERSION_NAME
    } else {
        BuildConfig.VERSION_NAME.substringBefore("-")
    }
