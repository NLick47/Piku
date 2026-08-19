package com.piku.client.ui.navigation

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.Work
import com.piku.client.ui.collection.CollectionScreen
import com.piku.client.ui.detail.DetailScreen
import com.piku.client.ui.follow.FollowUsersScreen
import com.piku.client.ui.follow.UserWorksScreen
import com.piku.client.ui.history.HistoryScreen
import com.piku.client.ui.home.HomeScreen
import com.piku.client.ui.login.EmailLoginScreen
import com.piku.client.ui.login.RegisterScreen
import com.piku.client.ui.search.SearchScreen
import com.piku.client.ui.tags.TagScreen

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
    const val SEARCH = "search/{keyword}"
    const val MAX_DETAIL_DEPTH = 3

    fun home() = "home"

    fun followUsers() = FOLLOW_USERS

    /** 统一搜索页：keyword 为空串表示待机态（搜索历史 + 热门标签）；# 前缀直达标签 tab，@ 前缀直达用户 tab */
    fun search(keyword: String = "") = "search/${Uri.encode(keyword)}"

    fun userWorks(userId: Long, userName: String = "") =
        "user_works/$userId?userName=${Uri.encode(userName)}"

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

private const val KEY_PENDING_TAG = "pending_tag"

private const val TAG = "PikuDiag"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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

    // 仅在非首页拦截系统返回；首页时禁用，保持默认退出行为
    BackHandler(enabled = currentRoute != null && currentRoute != Routes.HOME) {
        safePopBack()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
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
                    // 确定性弹栈：直接回到 HOME（栈底），不依赖当前栈顶状态，
                    // 也不会误弹掉 startDestination
                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                    Log.d(TAG, "login back pop result=$popped " +
                        "current=${navController.currentBackStackEntry?.destination?.route}")
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
            val pendingTag by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_PENDING_TAG, null)
                .collectAsStateWithLifecycle()

            HomeScreen(
                pendingTag = pendingTag,
                onTagConsumed = {
                    backStackEntry.savedStateHandle[KEY_PENDING_TAG] = null
                },
                onWorkClick = { work: Work ->
                    navController.navigate(Routes.detail(work.authorId, work.id, work.thumbnailUrl))
                },
                onLoginClick = {
                    Log.d(TAG, "navigate LOGIN " +
                        "current=${navController.currentBackStackEntry?.destination?.route} " +
                        "prev=${navController.previousBackStackEntry?.destination?.route}")
                    navController.navigate(Routes.LOGIN)
                },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onCollectionClick = { navController.navigate(Routes.COLLECTION) },
                onTagsClick = { navController.navigate(Routes.TAGS) },
                onFollowUsersClick = { navController.navigate(Routes.followUsers()) },
                onSearchClick = { navController.navigate(Routes.search()) },
                onProfileOpen = { uid, name ->
                    navController.navigate(Routes.userWorks(uid, name))
                },
            )
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
                    navController.navigate(Routes.detail(work.authorId, work.id, work.thumbnailUrl))
                },
                onUserClick = { user: FollowUser ->
                    navController.navigate(Routes.userWorks(user.userId, user.name))
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
                    navController.navigate(Routes.detail(work.authorId, work.id, work.thumbnailUrl))
                },
            )
        }
        composable(Routes.TAGS) {
            TagScreen(
                onBack = safePopBack,
                onWorkClick = { work: Work ->
                    navController.navigate(Routes.detail(work.authorId, work.id, work.thumbnailUrl))
                },
            )
        }
        composable(Routes.COLLECTION) {
            CollectionScreen(
                onBack = safePopBack,
                onWorkClick = { work: Work ->
                    navController.navigate(Routes.detail(work.authorId, work.id, work.thumbnailUrl))
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = safePopBack,
                onWorkClick = { work: Work ->
                    navController.navigate(Routes.detail(work.authorId, work.id, work.thumbnailUrl))
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
            DetailScreen(
                onBack = safePopBack,
                onHomeClick = safePopToHome,
                onTagClick = { tag ->
                    navController.getBackStackEntry(Routes.HOME)
                        .savedStateHandle[KEY_PENDING_TAG] = tag
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onRelatedWorkClick = { authorId, workId, thumbnailUrl ->
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
            )
        }
    }
}
