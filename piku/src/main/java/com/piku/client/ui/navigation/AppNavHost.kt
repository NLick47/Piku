package com.piku.client.ui.navigation

import android.app.Activity
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.piku.client.R
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.Work
import com.piku.client.ui.collection.CollectionScreen
import com.piku.client.ui.detail.DetailScreen
import com.piku.client.ui.detail.rememberWorkDetailPrefetch
import com.piku.client.ui.follow.FollowUsersScreen
import com.piku.client.ui.follow.UserWorksScreen
import com.piku.client.ui.history.HistoryScreen
import com.piku.client.ui.home.HomeScreen
import com.piku.client.ui.login.EmailLoginScreen
import com.piku.client.ui.login.RegisterScreen
import com.piku.client.ui.myposts.MyPostsScreen
import com.piku.client.ui.publish.PublishScreen
import com.piku.client.ui.search.PoipikuLink
import com.piku.client.ui.search.SearchScreen
import com.piku.client.ui.search.parsePoipikuLink
import com.piku.client.ui.tags.TagScreen
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val COLLECTION = "collection"
    const val DETAIL = "detail/{authorId}/{workId}?thumb={thumb}"
    const val HISTORY = "history"
    const val TAGS = "tags"
    const val FOLLOW_USERS = "follow_users"
    const val USER_WORKS = "user_works/{userId}?userName={userName}"
    const val MY_POSTS = "my_posts/{userId}?userName={userName}"
    const val EDIT_POST = "edit_post/{workId}"
    const val SEARCH = "search/{keyword}?tag={tag}"
    const val MAX_DETAIL_DEPTH = 3

    fun home() = "home"

    fun followUsers() = FOLLOW_USERS

    /**
     * 统一搜索页：keyword 为空串表示待机态（搜索历史 + 热门标签）；
     * # 前缀直达标签 tab，@ 前缀直达用户 tab。
     * [tag] 非空 = 精确标签名，落地即该标签的作品列表（详情页点标签进来，跳过标签建议）；
     * 需与带 # 前缀的 keyword 搭配使用（keyword 决定 tab 与分页可用性）。
     */
    fun search(keyword: String = "", tag: String = "") =
        "search/${Uri.encode(keyword)}" + if (tag.isEmpty()) "" else "?tag=${Uri.encode(tag)}"

    fun userWorks(userId: Long, userName: String = "") =
        "user_works/$userId?userName=${Uri.encode(userName)}"

    fun myPosts(userId: Long, userName: String = "") =
        "my_posts/$userId?userName=${Uri.encode(userName)}"

    /** 编辑已发布作品（发布页编辑模式；类型由页面自己判定，不进路由参数） */
    fun editPost(workId: Long) = "edit_post/$workId"

    /** 管理页删除成功后写回用户主页的标记（SavedStateHandle 返回结果模式） */
    const val KEY_POSTS_CHANGED = "my_posts_changed"

    /**
     * [thumbnailUrl] 为来源页（feed/历史/收藏/相关作品）的缩略图，供详情页在作品
     * 未解锁时回填历史/收藏记录；空串表示来源无缩略图信息（如正文文本链接）。
     */
    fun detail(authorId: Long, workId: Long, thumbnailUrl: String = "") =
        "detail/$authorId/$workId?thumb=${Uri.encode(thumbnailUrl)}"
}

/**
 * 两次返回间隔小于该值时忽略第二次，防止快速连按把 startDestination（HOME）也弹出，
 * 导致返回栈清空而白屏（详情页加载中连按两次返回键可稳定复现）。
 */
private const val BACK_POP_DEBOUNCE_MS = 400L

private const val EXIT_CONFIRM_INTERVAL_MS = 2000L

/** 路由转场时长，同时也是共享元素过渡的动画窗口 */
private const val SHARED_TRANSITION_MS = 220

private const val KEY_SHOULD_REOPEN_DRAWER = "should_reopen_drawer"

private const val TAG = "PikuDiag"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(
    deepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 点击卡片即预热详情页首图：详情页首图是同一张图的 _640，与卡片渲染的 _360
    // 缓存互不相通，预热与详情页 HTML/append 请求并行
    val prefetchDetailImage = rememberWorkDetailPrefetch()
    val openDetail: (Work) -> Unit = { work ->
        prefetchDetailImage(work.thumbnailUrl)
        navController.navigate(Routes.detail(work.authorId, work.id, work.thumbnailUrl))
    }

    LaunchedEffect(deepLink) {
        if (deepLink == null) return@LaunchedEffect
        when (val link = parsePoipikuLink(deepLink)) {
            is PoipikuLink.Work ->
                navController.navigate(Routes.detail(link.authorId, link.workId)) {
                    launchSingleTop = true
                }
            is PoipikuLink.User ->
                navController.navigate(Routes.userWorks(link.userId)) {
                    launchSingleTop = true
                }
            null -> Unit
        }
        onDeepLinkConsumed()
    }

    // 连按返回防抖 + 栈底保护：快速连按（含转场动画未结束时）只弹出最上层，
    // 且绝不弹出 startDestination（HOME）——返回栈清空会白屏。
    // previousBackStackEntry 为 null 表示当前已在栈底，直接忽略本次弹出。
    var lastPopAt by remember { mutableLongStateOf(0L) }
    val safePopBack = {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastPopAt
        val hasPrev = navController.previousBackStackEntry != null
        Log.d(TAG, "safePopBack elapsed=$elapsed hasPrev=$hasPrev " +
            "current=${navController.currentBackStackEntry?.destination?.route}")
        if (elapsed >= BACK_POP_DEBOUNCE_MS && hasPrev) {
            lastPopAt = now
            navController.popBackStack()
        }
    }

    // 详情页"回到首页"按钮：与返回共用同一防抖闸门，防止快速连点时在转场窗口内重复弹栈
    val safePopToHome = {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPopAt >= BACK_POP_DEBOUNCE_MS) {
            lastPopAt = now
            navController.popBackStack(Routes.HOME, inclusive = false)
        }
    }

    // 拦截系统返回：
    // - 非首页：safePopBack 弹出上一层
    // - 首页（栈底）：第一次按返回弹"再按一次退出"提示，2 秒内再按一次才退出，防误触
    val context = LocalContext.current
    var lastExitHintAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = currentRoute != null) {
        if (currentRoute == Routes.HOME) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastExitHintAt < EXIT_CONFIRM_INTERVAL_MS) {
                (context as? Activity)?.finish()
            } else {
                lastExitHintAt = now
                Toast.makeText(context, R.string.exit_confirm_hint, Toast.LENGTH_SHORT).show()
            }
        } else {
            safePopBack()
        }
    }

    // 共享元素过渡：Home ↔ Detail 之间的作品图放大/缩回。
    // 注意这里必须给非零时长——全 None 时 AnimatedContent 瞬间完成，sharedBounds 会直接跳变。
    SharedTransitionLayout {
        val sharedScope = this
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(SHARED_TRANSITION_MS)) },
            exitTransition = { fadeOut(tween(SHARED_TRANSITION_MS)) },
            popEnterTransition = { fadeIn(tween(SHARED_TRANSITION_MS)) },
            popExitTransition = { fadeOut(tween(SHARED_TRANSITION_MS)) },
        ) {
        composable(Routes.LOGIN) {
            // 有上一页（能回退）才显示返回按钮；按钮直接弹栈，
            // 不经过 safePopBack 的防抖闸门（快速往返登录页时防抖会吞掉回退）
            val canGoBack = navController.previousBackStackEntry != null
            Log.d(TAG, "LOGIN composed canGoBack=$canGoBack " +
                "current=${navController.currentBackStackEntry?.destination?.route} " +
                "prev=${navController.previousBackStackEntry?.destination?.route}")
            EmailLoginScreen(
                onBack = {
                    Log.d(TAG, "login back button tapped " +
                        "current=${navController.currentBackStackEntry?.destination?.route}")
                    // 单级弹栈：从哪里来回哪里。首页进入时回到首页；
                    // 详情页门卡「去登录」进入时回详情页（旧版弹到 HOME 会连详情页一起丢掉）
                    navController.popBackStack()
                },
                canGoBack = canGoBack,
                onSuccess = safePopBack,
                onRegisterClick = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            // 注册页从登录页进入：返回键/去登录都弹回登录页
            val canGoBack = navController.previousBackStackEntry != null
            RegisterScreen(
                onBack = {
                    Log.d(TAG, "register back button tapped")
                    navController.popBackStack(Routes.LOGIN, inclusive = false)
                },
                canGoBack = canGoBack,
                onSuccess = {
                    Log.d(TAG, "register success, popping to home")
                    // 注册成功后已登录，直接回到首页（栈底），弹掉 REGISTER 与 LOGIN
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onLoginClick = {
                    navController.popBackStack(Routes.LOGIN, inclusive = false)
                },
            )
        }
        composable(Routes.HOME) { backStackEntry ->
            val shouldReopenDrawer by backStackEntry.savedStateHandle
                .getStateFlow<Boolean>(KEY_SHOULD_REOPEN_DRAWER, false)
                .collectAsStateWithLifecycle()

            ProvideNavSharedScope(sharedScope, this) {
                HomeScreen(
                    shouldReopenDrawer = shouldReopenDrawer,
                    onDrawerReopenConsumed = {
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = false
                    },
                    onWorkClick = { work: Work ->
                        openDetail(work)
                    },
                    onLoginClick = {
                        Log.d(TAG, "navigate LOGIN " +
                            "current=${navController.currentBackStackEntry?.destination?.route} " +
                            "prev=${navController.previousBackStackEntry?.destination?.route}")
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = true
                        navController.navigate(Routes.LOGIN)
                    },
                    onHistoryClick = {
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = true
                        navController.navigate(Routes.HISTORY)
                    },
                    onCollectionClick = {
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = true
                        navController.navigate(Routes.COLLECTION)
                    },
                    onTagsClick = {
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = true
                        navController.navigate(Routes.TAGS)
                    },
                    onFollowUsersClick = {
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = true
                        navController.navigate(Routes.followUsers())
                    },
                    onSearchClick = { navController.navigate(Routes.search()) },
                    onAuthorClick = { work: Work ->
                        // 卡片作者区：不写 SHOULD_REOPEN_DRAWER，避免回到首页时抽屉被自动弹出
                        navController.navigate(Routes.userWorks(work.authorId, work.authorName)) {
                            launchSingleTop = true
                        }
                    },
                    onProfileOpen = { uid, name ->
                        // 抽屉里"我的资料"点击：保留重开抽屉的语义，便于连续切换抽屉菜单项
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = true
                        navController.navigate(Routes.userWorks(uid, name))
                    },
                )
            }
        }
        composable(Routes.FOLLOW_USERS) {
            FollowUsersScreen(
                onBack = safePopBack,
                onLoginClick = { navController.navigate(Routes.LOGIN) },
                onUserClick = { user: FollowUser ->
                    navController.navigate(Routes.userWorks(user.userId, user.name))
                },
            )
        }
        composable(
            route = Routes.SEARCH,
            arguments = listOf(
                navArgument("keyword") { type = NavType.StringType; defaultValue = "" },
                navArgument("tag") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            SearchScreen(
                onBack = safePopBack,
                onLoginClick = { navController.navigate(Routes.LOGIN) },
                onManageTags = { navController.navigate(Routes.TAGS) },
                onSearch = { keyword ->
                    navController.navigate(Routes.search(keyword)) {
                        popUpTo(Routes.SEARCH) { inclusive = true }
                    }
                },
                onWorkClick = { work: Work ->
                    openDetail(work)
                },
                onUserClick = { user: FollowUser ->
                    navController.navigate(Routes.userWorks(user.userId, user.name))
                },
                onOpenLink = { link ->
                    when (link) {
                        is PoipikuLink.Work ->
                            navController.navigate(Routes.detail(link.authorId, link.workId))
                        is PoipikuLink.User ->
                            navController.navigate(Routes.userWorks(link.userId))
                    }
                },
            )
        }
        composable(
            route = Routes.USER_WORKS,
            arguments = listOf(
                navArgument("userId") { type = NavType.LongType },
                navArgument("userName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            UserWorksScreen(
                onBack = safePopBack,
                onWorkClick = { work: Work ->
                    openDetail(work)
                },
                onManageClick = { uid, name ->
                    navController.navigate(Routes.myPosts(uid, name))
                },
            )
        }
        composable(
            route = Routes.MY_POSTS,
            arguments = listOf(
                navArgument("userId") { type = NavType.LongType },
                navArgument("userName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            MyPostsScreen(
                onBack = safePopBack,
                onWorkClick = { work: Work ->
                    openDetail(work)
                },
                onEditClick = { work: Work ->
                    navController.navigate(Routes.editPost(work.id))
                },
                onDeleted = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.KEY_POSTS_CHANGED, true)
                },
            )
        }
        composable(
            route = Routes.EDIT_POST,
            arguments = listOf(
                navArgument("workId") { type = NavType.LongType },
            ),
        ) {
            PublishScreen(
                onBack = safePopBack,
                editWorkId = it.arguments?.getLong("workId") ?: -1L,
                onPublished = {
                    // 编辑保存成功：写回投稿管理页标记触发刷新，然后返回
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.KEY_POSTS_CHANGED, true)
                    safePopBack()
                },
            )
        }
        composable(Routes.TAGS) {
            TagScreen(
                onBack = safePopBack,
                onWorkClick = { work: Work ->
                    openDetail(work)
                },
            )
        }
        composable(Routes.COLLECTION) {
            CollectionScreen(
                onBack = safePopBack,
                onWorkClick = { work: Work ->
                    openDetail(work)
                },
                onAuthorClick = { work: Work ->
                    navController.navigate(Routes.userWorks(work.authorId, work.authorName)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = safePopBack,
                onWorkClick = { work: Work ->
                    openDetail(work)
                },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("authorId") { type = NavType.LongType },
                navArgument("workId") { type = NavType.LongType },
                navArgument("thumb") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ProvideNavSharedScope(sharedScope, this) {
                DetailScreen(
                    onBack = safePopBack,
                    onHomeClick = safePopToHome,
                    // 点标签：压栈进统一搜索页并直达该标签的作品列表。详情页留在返回栈里
                    // （回退即回到作品，不用重新找），关键词带 # 前缀让搜索页落在标签 tab
                    onTagClick = { tag ->
                        navController.navigate(Routes.search("#$tag", tag = tag))
                    },
                    onRelatedWorkClick = { authorId, workId, thumbnailUrl ->
                        // 相关作品同样预热首图
                        prefetchDetailImage(thumbnailUrl)
                        val detailDepth = navController.currentBackStack.value
                            .count { it.destination.route == Routes.DETAIL }
                        if (detailDepth >= Routes.MAX_DETAIL_DEPTH) {
                            navController.navigate(Routes.detail(authorId, workId, thumbnailUrl)) {
                                popUpTo(Routes.DETAIL) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.detail(authorId, workId, thumbnailUrl))
                        }
                    },
                    onAuthorClick = { authorId, authorName ->
                        // 详情页 → 作者主页：作品页头部卡片展示作者主页信息，关注状态由页面自行解析
                        navController.navigate(
                            Routes.userWorks(
                                userId = authorId,
                                userName = authorName,
                            ),
                        )
                    },
                    // 受限门卡「去登录」：详情页留在返回栈，登录成功 popBack 后自动重载
                    onNavigateToLogin = {
                        navController.navigate(Routes.LOGIN) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
        }
    }
}
