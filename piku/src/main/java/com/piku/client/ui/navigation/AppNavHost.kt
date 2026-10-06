package com.piku.client.ui.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.piku.client.R
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.AuthorPageStyle
import com.piku.client.domain.source.SourceAuthRoutes
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceLink
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.ui.author.AuthorProfileScreen
import com.piku.client.ui.collection.CollectionScreen
import com.piku.client.ui.detail.DetailScreen
import com.piku.client.ui.detail.rememberWorkDetailPrefetch
import com.piku.client.ui.follow.UserWorksScreen
import com.piku.client.ui.history.HistoryScreen
import com.piku.client.ui.home.HomeScreen
import com.piku.client.ui.login.EmailLoginScreen
import com.piku.client.ui.login.RegisterScreen
import com.piku.client.ui.myposts.MyPostsScreen
import com.piku.client.ui.login.PixivLoginScreen
import com.piku.client.ui.publish.PublishScreen
import com.piku.client.ui.search.SearchScreen
import com.piku.client.ui.source.SourceWorkDetailScreen
import com.piku.client.ui.source.SourceWorkOpenHost
import com.piku.client.ui.source.SourceOpenViewModel
import com.piku.client.ui.tags.TagScreen
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

object Routes {
    /** poipiku 的登录页也由它的插件声明（SourceAuthRoutes），这里只登记 */
    const val LOGIN = SourceAuthRoutes.POIPIKU_LOGIN
    const val REGISTER = "register"
    const val PIXIV_LOGIN = SourceAuthRoutes.PIXIV_LOGIN
    const val HOME = "home"
    const val COLLECTION = "collection"
    const val DETAIL = "detail/{authorId}/{workId}?thumb={thumb}"
    const val SOURCE_DETAIL =
        "source_detail/{source}/{kind}/{authorId}/{workId}?thumb={thumb}&title={title}&authorName={authorName}&avatar={avatar}&r18={r18}&len={len}"
    const val HISTORY = "history"
    const val TAGS = "tags"
    const val USER_WORKS = "user_works/{userId}?userName={userName}"
    const val MY_POSTS = "my_posts/{userId}?userName={userName}"

    const val AUTHOR_PROFILE = "author/{source}/{userId}?userName={userName}"
    const val EDIT_POST = "edit_post/{workId}"
    const val SEARCH = "search/{keyword}?tag={tag}&source={source}"
    const val MAX_DETAIL_DEPTH = 3

    fun home() = "home"

    /**
     * 统一搜索页：keyword 为空串表示待机态（搜索历史 + 热门标签）；
     * # 前缀直达标签 tab，@ 前缀直达用户 tab。
     * [tag] 非空 = 精确标签名，落地即该标签的作品列表（详情页点标签进来，跳过标签建议）；
     * 需与带 # 前缀的 keyword 搭配使用（keyword 决定 tab 与分页可用性）。
     * [source] 非空 = 作品所在源（详情页点标签进来）：本次搜索固定在该源（源 chip 可见可切），
     * 不写回全局首页源——点标签是隐式动作，不替用户改首页设置；null = 跟随全局首页源。
     */
    fun search(keyword: String = "", tag: String = "", source: WorkSource? = null) =
        "search/${Uri.encode(keyword)}?tag=${Uri.encode(tag)}&source=${source?.name.orEmpty()}"

    fun userWorks(userId: Long, userName: String = "") =
        "user_works/$userId?userName=${Uri.encode(userName)}"

    fun authorProfile(source: WorkSource, userId: Long, userName: String = "") =
        "author/${source.name}/$userId?userName=${Uri.encode(userName)}"

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

    /**
     * 应用内看图器源的共用详情壳。除了 ids，还携带卡片自带的展示字段：
     * 详情 VM 加载前会拿它们同步造一份打底 detail 秒进首帧（标题/作者/缩略图），
     * 也是共享元素转场首帧的形变落点——字段缺了首帧就退化成骨架屏。
     */
    fun sourceDetail(work: Work) =
        "source_detail/${work.source.name}/${work.kind.name}/${work.authorId}/${work.id}" +
            "?thumb=${Uri.encode(work.thumbnailUrl)}" +
            "&title=${Uri.encode(work.title)}" +
            "&authorName=${Uri.encode(work.authorName)}" +
            "&avatar=${Uri.encode(work.authorAvatarUrl.orEmpty())}" +
            "&r18=${work.r18}" +
            "&len=${work.textLength}"
}

/**
 * 两次返回间隔小于该值时忽略第二次，防止快速连按把 startDestination（HOME）也弹出，
 * 导致返回栈清空而白屏（详情页加载中连按两次返回键可稳定复现）。
 */
private const val BACK_POP_DEBOUNCE_MS = 400L

private const val EXIT_CONFIRM_INTERVAL_MS = 2000L

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

    // 源声明注册表：作品点击按它分流（主壳详情 / 应用内共用壳 / 出站）
    val sourceOpen: SourceOpenViewModel = hiltViewModel()
    val context = LocalContext.current
    val openExternal: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // 点击卡片即预热详情页首图：详情页首图是同一张图的 _640，与卡片渲染的 _360
    // 缓存互不相通，预热与详情页 HTML/append 请求并行
    val prefetchDetailImage = rememberWorkDetailPrefetch()
    val openDetail: (Work) -> Unit = { work ->
        prefetchDetailImage(work.thumbnailUrl)
        navController.navigate(Routes.detail(work.authorId, work.id, work.thumbnailUrl))
    }

    /**
     * 按作品所属源分流打开——所有作品点击的统一出口（首页两壳/搜索/收藏/历史/标签/
     * 用户作品/画师主页）。poipiku 走主壳详情路由；声明应用内看图器的源（pixiv）走
     * 共用源详情壳路由；外链出站。pixiv 作品从此不再误入 poipiku 详情取数。
     *
     * R-18 门不在这里：它挂在 [SourceWorkOpenHost]（feed/收藏/历史的两段式路径），
     * 而声明 InAppViewer 的源目前只有 pixiv，其 R-18 由服务端账号设置管控，直通无碍。
     */
    val openWork: (Work) -> Unit = { work ->
        when (val open = sourceOpen.open(work)) {
            SourceWorkOpen.NativeDetail -> openDetail(work)
            SourceWorkOpen.InAppViewer -> navController.navigate(Routes.sourceDetail(work))
            is SourceWorkOpen.External -> openExternal(open.url)
        }
    }

    /** 源详情里点相关作品：压栈进新详情，栈深到上限就换掉栈顶（与 poipiku 详情同规） */
    val openRelatedSourceWork: (Work) -> Unit = { work ->
        val detailDepth = navController.currentBackStack.value
            .count { it.destination.route == Routes.SOURCE_DETAIL }
        if (detailDepth >= Routes.MAX_DETAIL_DEPTH) {
            navController.navigate(Routes.sourceDetail(work)) {
                popUpTo(Routes.SOURCE_DETAIL) { inclusive = true }
            }
        } else {
            navController.navigate(Routes.sourceDetail(work))
        }
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
    val openAuthor: (WorkSource, Long, String) -> Unit = { source, userId, userName ->
        when (sourceOpen.authorPageStyle(source)) {
            AuthorPageStyle.Works ->
                navController.navigate(Routes.userWorks(userId, userName)) { launchSingleTop = true }
            AuthorPageStyle.Profile ->
                navController.navigate(Routes.authorProfile(source, userId, userName)) { launchSingleTop = true }
        }
    }

    /** 从作品点作者：先问源的去向（出站 / 不可点 / 应用内），应用内再按形态分 */
    val openAuthorOfWork: (Work) -> Unit = { work ->
        when (val open = sourceOpen.authorPage(work)) {
            SourceAuthorOpen.NativeDetail, SourceAuthorOpen.NativeProfile ->
                openAuthor(work.source, work.authorId, work.authorName)
            is SourceAuthorOpen.External -> openExternal(open.url)
            null -> Unit
        }
    }

    /** 站内链接的去向：作品引用走 [openWork] 的源声明分流，作者引用按形态挑作者页 */
    val openLink: (SourceLink) -> Unit = { link ->
        when (link) {
            is SourceLink.Work -> openWork(link.toStubWork())
            is SourceLink.User -> openAuthor(link.source, link.userId, "")
        }
    }

    // 深链唤起：解析跨源（host 定源，与外壳当前源无关），不认识的 URL 落回首页。
    // 搜索框粘贴不走这里，由 SearchScreen.onOpenLink 汇入同一个 openLink
    LaunchedEffect(deepLink) {
        if (deepLink == null) return@LaunchedEffect
        sourceOpen.resolveLink(deepLink)?.let(openLink)
        onDeepLinkConsumed()
    }

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
        composable(Routes.PIXIV_LOGIN) {
            PixivLoginScreen(
                onBack = safePopBack,
                onSuccess = safePopBack,
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
                        openWork(work)
                    },
                    onLoginClick = {
                        Log.d(TAG, "navigate LOGIN " +
                            "current=${navController.currentBackStackEntry?.destination?.route} " +
                            "prev=${navController.previousBackStackEntry?.destination?.route}")
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = true
                        navController.navigate(Routes.LOGIN)
                    },
                    // 非 poipiku 源的登录门：路由由该源的登录插件给出，外壳只跳不解释
                    onSourceLoginClick = { route -> navController.navigate(route) },
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
                    onSearchClick = { navController.navigate(Routes.search()) },
                    onAuthorClick = { work: Work ->
                        // 卡片作者区：不写 SHOULD_REOPEN_DRAWER，避免回到首页时抽屉被自动弹出
                        openAuthorOfWork(work)
                    },
                    onProfileOpen = { source, uid, name ->
                        // 抽屉里"我的资料"点击：保留重开抽屉的语义，便于连续切换抽屉菜单项
                        backStackEntry.savedStateHandle[KEY_SHOULD_REOPEN_DRAWER] = true
                        openAuthor(source, uid, name)
                    },
                )
            }
        }
        composable(
            route = Routes.SEARCH,
            arguments = listOf(
                navArgument("keyword") { type = NavType.StringType; defaultValue = "" },
                navArgument("tag") { type = NavType.StringType; defaultValue = "" },
                navArgument("source") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ProvideNavSharedScope(sharedScope, this) {
                SearchScreen(
                    onBack = safePopBack,
                    onLoginClick = { source ->
                        navController.navigate(sourceOpen.loginRoute(source) ?: Routes.LOGIN)
                    },
                    onManageTags = { navController.navigate(Routes.TAGS) },
                    // 换词重搜另起一页：把当前页生效的源带过去，种子源不因重搜悄悄回落全局源
                    onSearch = { keyword, source ->
                        navController.navigate(Routes.search(keyword, source = source)) {
                            popUpTo(Routes.SEARCH) { inclusive = true }
                        }
                    },
                    onWorkClick = { work: Work ->
                        openWork(work)
                    },
                    onUserClick = { source, user: FollowUser ->
                        openAuthor(source, user.userId, user.name)
                    },
                    onOpenExternal = openExternal,
                    onOpenLink = openLink,
                )
            }
        }
        composable(
            route = Routes.USER_WORKS,
            arguments = listOf(
                navArgument("userId") { type = NavType.LongType },
                navArgument("userName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ProvideNavSharedScope(sharedScope, this) {
                UserWorksScreen(
                    onBack = safePopBack,
                    onWorkClick = { work: Work ->
                        openWork(work)
                    },
                    onManageClick = { uid, name ->
                        navController.navigate(Routes.myPosts(uid, name))
                    },
                )
            }
        }
        composable(
            route = Routes.AUTHOR_PROFILE,
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("userId") { type = NavType.LongType },
                navArgument("userName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            ProvideNavSharedScope(sharedScope, this) {
                val source = entry.arguments?.getString("source")
                    ?.let { name -> WorkSource.entries.firstOrNull { it.name == name } }
                AuthorProfileScreen(
                    onBack = safePopBack,
                    onWorkClick = { work: Work ->
                        openWork(work)
                    },
                    onLoginClick = {
                        val route = source?.let(sourceOpen::loginRoute)
                        if (route != null) navController.navigate(route) { launchSingleTop = true }
                    },
                )
            }
        }
        composable(
            route = Routes.MY_POSTS,
            arguments = listOf(
                navArgument("userId") { type = NavType.LongType },
                navArgument("userName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ProvideNavSharedScope(sharedScope, this) {
                MyPostsScreen(
                    onBack = safePopBack,
                    onWorkClick = { work: Work ->
                        openWork(work)
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
            ProvideNavSharedScope(sharedScope, this) {
                TagScreen(
                    onBack = safePopBack,
                    onWorkClick = { work: Work ->
                        openWork(work)
                    },
                )
            }
        }
        composable(Routes.COLLECTION) {
            ProvideNavSharedScope(sharedScope, this) {
                CollectionScreen(
                    onBack = safePopBack,
                    onWorkClick = { work: Work ->
                        openWork(work)
                    },
                    onAuthorClick = { work: Work ->
                        openAuthorOfWork(work)
                    },
                )
            }
        }
        composable(Routes.HISTORY) {
            ProvideNavSharedScope(sharedScope, this) {
                CompositionLocalProvider(LocalWorkMorphEnabled provides false) {
                    HistoryScreen(
                        onBack = safePopBack,
                        onWorkClick = { work: Work ->
                            openWork(work)
                        },
                    )
                }
            }
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
                    // （回退即回到作品，不用重新找），关键词带 # 前缀让搜索页落在标签 tab；
                    // 源固定在本作品源，pixiv 标签不落到 poipiku 源去搜
                    onTagClick = { tag ->
                        navController.navigate(
                            Routes.search("#$tag", tag = tag, source = WorkSource.POIPIKU),
                        )
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
                        // 主详情页只承载 poipiku（取数只有 PoipikuApi），去向仍由源的声明挑页
                        openAuthor(WorkSource.POIPIKU, authorId, authorName)
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
        composable(
            route = Routes.SOURCE_DETAIL,
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("kind") { type = NavType.StringType },
                navArgument("authorId") { type = NavType.LongType },
                navArgument("workId") { type = NavType.LongType },
                navArgument("thumb") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("authorName") { type = NavType.StringType; defaultValue = "" },
                navArgument("avatar") { type = NavType.StringType; defaultValue = "" },
                navArgument("r18") { type = NavType.BoolType; defaultValue = false },
                navArgument("len") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            // 路由参数重建 Work：详情 VM 拿它同步造打底 detail 秒进首帧，
            // 这些字段同时是共享元素转场首帧的形变落点
            val work = remember(entry) {
                val a = requireNotNull(entry.arguments) { "SOURCE_DETAIL arguments missing" }
                Work(
                    id = a.getLong("workId"),
                    authorId = a.getLong("authorId"),
                    authorName = a.getString("authorName").orEmpty(),
                    authorAvatarUrl = a.getString("avatar")?.takeIf { it.isNotEmpty() },
                    categoryCd = -1,
                    categoryName = "",
                    title = a.getString("title").orEmpty(),
                    thumbnailUrl = a.getString("thumb").orEmpty(),
                    imageCount = 0,
                    textLength = a.getInt("len"),
                    r18 = a.getBoolean("r18"),
                    source = WorkSource.entries.firstOrNull { it.name == a.getString("source") }
                        ?: WorkSource.PIXIV,
                    kind = WorkKind.entries.firstOrNull { it.name == a.getString("kind") }
                        ?: WorkKind.ILLUST,
                )
            }
            ProvideNavSharedScope(sharedScope, this) {
                SourceWorkDetailScreen(
                    work = work,
                    onBack = safePopBack,
                    onHomeClick = safePopToHome,
                    onOpenAuthor = openAuthorOfWork,
                    onRelatedClick = openRelatedSourceWork,
                    onTagClick = { tag ->
                        navController.navigate(
                            Routes.search("#$tag", tag = tag, source = work.source),
                        )
                    },
                    onLoginClick = { navController.navigate(Routes.PIXIV_LOGIN) },
                )
            }
        }
        }

        // 应用内看图器作品不再有导航层浮层：pixiv 详情由 SOURCE_DETAIL 路由承载，
        // R-18 门留在 SourceWorkOpenHost（feed/收藏/历史的两段式路径）
    }
}

/** 链接引用 → 可导航的最小 Work：只有源/id/kind，展示字段空缺 = 详情壳骨架屏首帧 */
private fun SourceLink.Work.toStubWork() = Work(
    id = workId,
    authorId = authorId,
    authorName = "",
    authorAvatarUrl = null,
    categoryCd = -1,
    categoryName = "",
    title = "",
    thumbnailUrl = "",
    imageCount = 0,
    r18 = false,
    source = source,
    kind = kind,
)
