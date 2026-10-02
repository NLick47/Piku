package com.piku.client.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.ui.common.labelRes
import com.piku.client.ui.home.background.HomeBackdropLayer
import com.piku.client.ui.home.background.HomeBackgroundEditOverlay
import com.piku.client.ui.home.background.rememberHomeBackdropState
import com.piku.client.ui.home.background.rememberHomeBackgroundEdit
import com.piku.client.ui.home.drawer.AccountsViewModel
import com.piku.client.ui.home.drawer.DrawerScope
import com.piku.client.ui.home.drawer.SourceAccountRow
import com.piku.client.ui.home.drawer.UserDrawer
import com.piku.client.ui.home.shell.PoipikuHomeShell
import com.piku.client.ui.source.SourceFeedContent
import com.piku.client.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

/**
 * 首页宿主：只做装配与分发，不实现任何一块功能——
 * - 内容壳按当前源分流：主源（poipiku）走 [PoipikuHomeShell]（tab 图标/分类侧栏是主源语义），
 *   其余源走 ui/source 的声明驱动通用壳 [SourceFeedContent]。新增源注册进
 *   SourceRegistry / SourceAuthRegistry 后即可换源、登录、展示，宿主与两个壳都不用改。
 * - 背景域（取景/取色/编辑会话）在 background/ 包，抽屉在 drawer/ 包，
 *   二级弹层在 [HomeDialogs]，抽屉通用功能页浮层在 [HomeOverlays]；
 *   源专属的抽屉条目与浮层（投稿等）由 [com.piku.client.ui.home.drawer.SourceDrawerPlugin]
 *   声明，宿主只装配 [DrawerScope] 与挂载，不认识任何一条。
 * - 数据源隔离：宿主不接触任何源的凭据与实现细节，账号信息一律经
 *   AccountsViewModel 的"当前源账号行"（源自己声明登录态与路由）。
 */
@Composable
fun HomeScreen(
    shouldReopenDrawer: Boolean = false,
    onDrawerReopenConsumed: () -> Unit = {},
    onWorkClick: (Work) -> Unit,
    onLoginClick: () -> Unit,
    onSourceLoginClick: (String) -> Unit = {},
    onHistoryClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onTagsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAuthorClick: (Work) -> Unit,
    onProfileOpen: (WorkSource, Long, String) -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 抽屉头部显示的是"当前首页源"的账号，所以这里跟的是账号源而不是 poipiku 的登录态
    val accountsViewModel: AccountsViewModel = hiltViewModel()
    val headerAccount by accountsViewModel.current.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val context = LocalContext.current

    // ---- 背景域：取景/取色派生 + 编辑会话（详见 background/ 包） ----
    val backdrop = rememberHomeBackdropState(state, dark, viewModel::sampleBackgroundImage)
    val edit = rememberHomeBackgroundEdit(viewModel, state)

    // ---- 抽屉触发的二级弹层开关 ----
    val dialogs = rememberHomeDialogsState()

    // ---- 抽屉功能页与插件浮层：任一激活时禁掉抽屉手势 ----
    var showHistoryPage by rememberSaveable { mutableStateOf(false) }
    var showCollectionPage by rememberSaveable { mutableStateOf(false) }
    var showTagsPage by rememberSaveable { mutableStateOf(false) }
    var showAccountsPage by rememberSaveable { mutableStateOf(false) }
    // 抽屉插件声明的浮层（投稿/资料编辑/关注屏蔽列表…）：内容由插件给，外壳只挂载不解释
    var drawerOverlay by remember {
        mutableStateOf<(@Composable (onDismiss: () -> Unit, onClose: () -> Unit) -> Unit)?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val anyOverlayActive = showHistoryPage || showAccountsPage ||
        showCollectionPage || showTagsPage || dialogs.showWebDavSettings ||
        drawerOverlay != null

    // ---- 滚动：网格状态、头部底衬进度、视差、停顶判定 ----
    val isScrolling = remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()
    // 传给头部在绘制阶段读取：滚动只重绘底边那条线，不触发重组
    val feedProgress: () -> Float = remember(gridState) { { gridState.feedScrollProgress() } }
    // 视差位移源：首项滚出视口顶部的像素量，封顶后到位即停；绘制阶段读取
    val parallax: () -> Int = remember(gridState) { { gridState.scrolledOverTopPx() } }
    // 自定义背景下列表停在顶部时，头部底衬整体退场把头图让出来；滚动即恢复
    val atTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
    }
    // 内容换血（切 tab/分类/重载/洗牌）时回顶
    var seenFeedEpoch by remember { mutableIntStateOf(state.feedEpoch) }
    LaunchedEffect(state.feedEpoch) {
        if (state.feedEpoch != seenFeedEpoch) {
            gridState.scrollToItem(0)
            seenFeedEpoch = state.feedEpoch
        }
    }

    // ---- 抽屉开合（带生命周期守卫：转场/后台时按钮不重复触发） ----
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

    LaunchedEffect(shouldReopenDrawer) {
        if (shouldReopenDrawer) {
            drawerState.open()
            onDrawerReopenConsumed()
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) viewModel.retryUserProfile()
    }

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
    val onGoTop = { gridState.scrollToTopSmart(scope) }

    // ---- 抽屉插件的外壳环境：源专属条目经它触达登录页/导航/浮层通道，宿主不解释条目语义 ----
    val dismissDrawerOverlay: () -> Unit = {
        drawerOverlay = null
        scope.launch { drawerState.open() }
    }
    val closeDrawerOverlay = { drawerOverlay = null }
    val drawerScope = object : DrawerScope {
        override val account: SourceAccountRow? get() = headerAccount

        override fun openLogin() {
            val row = headerAccount
            val route = row?.loginRoute
            if (route != null) onSourceLoginClick(route) else onLoginClick()
        }

        override fun openWork(work: Work) = onWorkClick(work)

        override fun openAuthorProfile(source: WorkSource, uid: Long, name: String) =
            onProfileOpen(source, uid, name)

        // 应用内没有对应页时的去向：交给系统浏览器
        override fun openExternal(url: String) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }

        override fun openOverlay(
            closeDrawer: Boolean,
            content: @Composable (onDismiss: () -> Unit, onClose: () -> Unit) -> Unit,
        ) {
            if (closeDrawer) scope.launch { drawerState.close() }
            drawerOverlay = content
        }
    }

    UserDrawer(
        drawerState = drawerState,
        headerAccount = headerAccount,
        adultEnabled = state.adultEnabled,
        themeMode = state.themeMode,
        imageRouteMode = state.imageRouteMode,
        pixivBookmarkMirror = state.pixivBookmarkMirror,
        customBackgroundPath = state.customBackgroundPath,
        language = state.language,
        currentVersion = displayVersionName(),
        updateAvailable = state.updateCheckState is UpdateCheckState.Available,
        onToggleAdult = viewModel::toggleAdultContent,
        onSettingsClick = {},
        onAboutClick = { dialogs.showAboutSheet = true },
        onThemeClick = { dialogs.showThemeSheet = true },
        homeSourceLabelRes = state.homeSource.labelRes(),
        onHomeSourceClick = { dialogs.showHomeSource = true },
        onImageRouteClick = { dialogs.showImageRouteSheet = true },
        onPixivMirrorClick = { dialogs.showPixivMirrorSheet = true },
        onBackgroundClick = {
            scope.launch { drawerState.close() }
            edit.enterEdit(state)
        },
        onHistoryClick = {
            showHistoryPage = true
        },
        onCollectionClick = {
            showCollectionPage = true
        },
        onTagsClick = {
            showTagsPage = true
        },
        // 头部点哪里全看这个源自己声明了什么能力，不看谁是"主源"：
        // 未登录 → 它的登录页；有账号主页 → 主页；没有 → 账号页
        onHeaderClick = {
            val row = headerAccount
            when {
                row == null || !row.loggedIn -> row?.loginRoute?.let(onSourceLoginClick)
                row.profileId != null -> row.profileId.toLongOrNull()?.let { uid ->
                    onProfileOpen(row.source, uid, row.account?.displayName.orEmpty())
                }
                else -> showAccountsPage = true
            }
        },
        onAccountsClick = { showAccountsPage = true },
        // 底部那一行：未登录去登录、已登录去断开（断开文案与确认由该源自己声明）
        onAccountAction = {
            val row = headerAccount
            when {
                row == null -> onLoginClick()
                row.loggedIn -> accountsViewModel.logout(row.source)
                else -> row.loginRoute?.let(onSourceLoginClick)
            }
        },
        // 头像的查看/保存走的是"账号资料"那一套，所以只有有主页的源才点得动
        onAvatarClick = { if (headerAccount?.profileId != null) dialogs.showAvatarViewer = true },
        gesturesEnabled = !anyOverlayActive,
        dark = dark,
        aiTranslateEnabled = state.aiTranslateEnabled,
        historyRetentionDays = state.historyRetentionDays,
        onAiTranslateClick = {
            dialogs.showAiTranslateSheet = true
        },
        onLanguageClick = {
            dialogs.showLanguageSheet = true
        },
        onRetentionClick = {
            dialogs.showRetentionSheet = true
        },
        onWebDavClick = {
            scope.launch { drawerState.close() }
            dialogs.showWebDavSettings = true
        },
        // 当前源的抽屉插件：投稿这类源专属入口由它声明，看哪个源就渲染哪个源的
        sourceDrawer = viewModel.drawerPlugin(state.homeSource),
        drawerScope = drawerScope,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景层：自定义头图 + 雾化，或默认渐变（取景几何与编辑会话在 background/ 包）
            HomeBackdropLayer(
                state = state,
                backdrop = backdrop,
                dark = dark,
                editPreviewing = edit.previewingContent,
                scrolledOverTopPx = parallax,
            )

            val contentAlpha by animateFloatAsState(
                targetValue = if (edit.contentVisible) 1f else 0f,
                label = "contentAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                // 换源分发：主源走专属壳，其余源走声明驱动的通用壳——新增源不需要改这里
                if (state.homeSource != WorkSource.POIPIKU) {
                    SourceFeedContent(
                        dark = dark,
                        isScrolling = isScrolling,
                        // 顶栏头像也要跟着当前源：放 poipiku 的头像，在看 pixiv 时永远是空的
                        avatarUrl = headerAccount?.account?.avatarUrl,
                        menuEnabled = drawerButtonEnabled,
                        hasCustomBackground = state.customBackgroundPath != null,
                        drawerIsOpen = drawerState.isOpen,
                        tabColors = backdrop.tabColors,
                        onTabBand = backdrop.onTabBand,
                        onOpenDrawer = openDrawer,
                        onSearchClick = onSearchClick,
                        updateBanner = state.updateBanner,
                        onOpenUpdate = onOpenUpdate,
                        onDismissUpdateBanner = viewModel::dismissUpdateBanner,
                        onOpenWork = onWorkClick,
                        onLoginClick = onSourceLoginClick,
                    )
                } else {
                    PoipikuHomeShell(
                        state = state,
                        isTablet = isTablet,
                        dark = dark,
                        isScrolling = isScrolling,
                        gridState = gridState,
                        scrollProgress = feedProgress,
                        atTop = atTop,
                        tabColors = backdrop.tabColors,
                        onTabBand = backdrop.onTabBand,
                        drawerIsOpen = drawerState.isOpen,
                        menuEnabled = drawerButtonEnabled,
                        onOpenDrawer = openDrawer,
                        onSearchClick = onSearchClick,
                        onCategoryClick = { dialogs.showCategories = true },
                        onSelectFeedTab = viewModel::selectFeedTab,
                        onSelectCategory = viewModel::selectCategory,
                        onGoTop = onGoTop,
                        onRetry = viewModel::retry,
                        onLoadMore = viewModel::loadMore,
                        onRetryLoadMore = viewModel::retryLoadMore,
                        onShuffle = viewModel::shuffleRandom,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onWorkClick = onWorkClick,
                        onAuthorClick = onAuthorClick,
                        onLoginClick = onLoginClick,
                        onDismissRefreshNotice = viewModel::dismissRefreshNotice,
                        onOpenUpdate = onOpenUpdate,
                        onDismissUpdateBanner = viewModel::dismissUpdateBanner,
                    )
                }
            }

            if (edit.isEditMode) {
                HomeBackgroundEditOverlay(
                    viewModel = viewModel,
                    edit = edit,
                    state = state,
                    backdrop = backdrop,
                    dark = dark,
                )
            }

            HomeDialogs(
                dialogs = dialogs,
                viewModel = viewModel,
                state = state,
                headerAvatarUrl = headerAccount?.account?.avatarUrl,
                dark = dark,
                onOpenUpdate = onOpenUpdate,
                onCloseAndReopenDrawer = { scope.launch { drawerState.open() } },
            )

            HomeOverlays(
                showAccountsPage = showAccountsPage,
                onAccountsBack = { showAccountsPage = false; scope.launch { drawerState.open() } },
                // 账号页里点「登录」：路由由该源自己声明。
                // 必须先收起账号页——它是独立窗口，不关会盖在导航过去的登录页上面
                onAccountsLogin = { row ->
                    showAccountsPage = false
                    row.loginRoute?.let(onSourceLoginClick)
                },
                showHistoryPage = showHistoryPage,
                onHistoryBack = { showHistoryPage = false; scope.launch { drawerState.open() } },
                showCollectionPage = showCollectionPage,
                onCollectionBack = { showCollectionPage = false; scope.launch { drawerState.open() } },
                showTagsPage = showTagsPage,
                onTagsBack = { showTagsPage = false; scope.launch { drawerState.open() } },
                // 浮层页是独立窗口：不收起会盖在导航过去的详情/作者页上面（同账号页→登录的约定），
                // 所以点作品/作者先收浮层再交给导航
                onWorkClick = { work ->
                    showHistoryPage = false
                    showCollectionPage = false
                    showTagsPage = false
                    onWorkClick(work)
                },
                onOpenAuthor = { work ->
                    showHistoryPage = false
                    showCollectionPage = false
                    showTagsPage = false
                    onAuthorClick(work)
                },
                state = state,
                dark = dark,
            )

            // 抽屉插件声明的浮层（投稿页/资料编辑/关注屏蔽列表…）：
            // 内容与关闭语义都由插件给，外壳只负责挂载，不解释其中任何一个
            drawerOverlay?.let { overlayContent ->
                overlayContent(dismissDrawerOverlay, closeDrawerOverlay)
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}
