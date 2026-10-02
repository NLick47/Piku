package com.piku.client.ui.author

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.AuthorProfile
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.key
import com.piku.client.ui.common.AvatarViewerDialog
import com.piku.client.ui.common.FeedbackHost
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.LoginPrompt
import com.piku.client.ui.detail.TranslateChip
import com.piku.client.domain.model.WorkKind
import com.piku.client.ui.source.NovelWorkCard
import com.piku.client.ui.source.ProportionalWorkCard
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.onAccent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val BannerHeight = 132.dp

/** 横幅视差行程：图片预先加高此量，滚动时缓慢下移，和卡片拉开层次 */
private val BannerParallax = 52.dp

private val AvatarSize = 64.dp
private val TopBarHeight = 46.dp
private const val LOAD_MORE_NEAR_END = 4
private const val BIO_MAX_LINES = 2

private const val TakeoverStart = 0.38f
private const val TakeoverEnd = 0.68f

@Composable
fun AuthorProfileScreen(
    onBack: () -> Unit,
    onWorkClick: (Work) -> Unit,
    onLoginClick: () -> Unit,
    dark: Boolean = LocalDarkTheme.current,
    viewModel: AuthorProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyStaggeredGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val linkCopiedMessage = stringResource(R.string.detail_link_copied)
    var showAvatarViewer by rememberSaveable { mutableStateOf(false) }
    var expandedBio by rememberSaveable { mutableStateOf(false) }

    val openExternal: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    FeedbackHost(channel = viewModel.feedback, snackbarHostState = snackbarHostState)

    // 折叠进度：0=完全展开，1=横幅卡片完全滚出（顶栏接管）。
    // 与 poipiku 作者页同一套算法——横幅卡片是第 0 项，滚出多少就是多少进度
    val density = LocalDensity.current
    val bannerHeightPx = with(density) { BannerHeight.toPx() }
    val collapseProgress by remember(gridState) {
        derivedStateOf {
            val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
            if (first.index > 0) 1f else (-first.offset.y.toFloat() / bannerHeightPx).coerceIn(0f, 1f)
        }
    }
    // 名字在折叠过半后交叉淡入，避免展开态与顶栏重名
    val topBarTitleAlpha =
        ((collapseProgress - TakeoverStart) / (TakeoverEnd - TakeoverStart)).coerceIn(0f, 1f)

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (total > 0 && last >= total - LOAD_MORE_NEAR_END) viewModel.loadMore()
            }
    }

    val profile = state.profile

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                        else listOf(HomeBgTopLight, HomeBgBottomLight),
                    ),
                ),
        )
        Column(Modifier.fillMaxSize()) {
            AuthorTopBar(
                userName = profile?.name?.ifBlank { state.userName } ?: state.userName,
                titleAlpha = topBarTitleAlpha,
                onBack = onBack,
                onCopyLink = {
                    clipboard.setText(AnnotatedString(viewModel.profileUrl))
                    scope.launch { snackbarHostState.showSnackbar(linkCopiedMessage) }
                },
                onOpenBrowser = { openExternal(viewModel.profileUrl) },
            )
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(if (isTablet) 3 else 2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalItemSpacing = 11.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    AuthorHeaderCard(
                        avatarUrl = profile?.avatarUrl,
                        bannerUrl = profile?.bannerUrl,
                        name = profile?.name?.ifBlank { state.userName } ?: state.userName,
                        account = profile?.account.orEmpty(),
                        followCount = profile?.followCount,
                        premium = profile?.premium == true,
                        followed = profile?.followed == true,
                        showFollow = state.loggedIn && !state.isSelf,
                        followSending = state.followSending,
                        // 传 lambda 而不是当帧的值：视差的读写都落在 graphicsLayer 里，
                        // 滚动时只更新图层，不带着整个头部重组
                        collapseProgress = { collapseProgress },
                        onAvatarClick = { showAvatarViewer = true },
                        onToggleFollow = viewModel::toggleFollow,
                    )
                }

                if (profile != null) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        AuthorProfileBody(
                            profile = profile,
                            expanded = expandedBio,
                            onToggleExpand = { expandedBio = !expandedBio },
                            translatedBio = state.bioTranslated,
                            showTranslatedBio = state.showTranslatedBio,
                            translating = state.translating,
                            canTranslate = state.canTranslate,
                            onToggleTranslate = viewModel::toggleBioTranslation,
                        )
                    }
                } else if (state.profileErrorRes != null) {
                    // 资料失败只占资料区这一块，下面的作品列表照常——重试也只重拉资料
                    item(span = StaggeredGridItemSpan.FullLine) {
                        AuthorNotice(
                            text = stringResource(state.profileErrorRes ?: R.string.home_error_parse),
                            actionText = stringResource(R.string.common_retry),
                            onAction = viewModel::retryProfile,
                        )
                    }
                }

                if (state.loggedIn) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        AuthorTabRow(state = state, onSelect = viewModel::selectTab)
                    }
                    // 非公开收藏只有本人可见，切换只长在自己的主页上
                    if (state.tab == AuthorTab.BOOKMARKS && state.isSelf) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            BookmarkPoolToggle(
                                private = state.bookmarkPrivate,
                                onSelect = viewModel::selectBookmarkPool,
                            )
                        }
                    }
                }

                when {
                    !state.loggedIn -> item(span = StaggeredGridItemSpan.FullLine) {
                        Box(Modifier.fillMaxWidth().height(240.dp)) {
                            LoginPrompt(
                                message = stringResource(R.string.author_login_hint),
                                onLogin = onLoginClick,
                                dark = dark,
                            )
                        }
                    }

                    state.loading && state.works.isEmpty() ->
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                Modifier.fillMaxWidth().height(160.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoaderDots(dark = dark)
                            }
                        }

                    else -> {
                        if (state.tabLoadFailed) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                AuthorNotice(
                                    text = stringResource(R.string.home_error_parse),
                                    actionText = stringResource(R.string.common_retry),
                                    onAction = viewModel::retryTab,
                                )
                            }
                        } else if (state.works.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                AuthorNotice(
                                    text = stringResource(
                                        when (state.tab) {
                                            AuthorTab.BOOKMARKS -> when {
                                                state.bookmarkPrivate -> R.string.author_empty_bookmarks_private
                                                state.isSelf -> R.string.author_empty_bookmarks_self
                                                else -> R.string.author_empty_bookmarks
                                            }
                                            AuthorTab.NOVEL -> R.string.author_empty_novel
                                            else -> R.string.user_works_empty
                                        },
                                    ),
                                )
                            }
                        }
                        items(items = state.works, key = { it.key.toString() }) { work ->
                            if (work.kind == WorkKind.NOVEL) {
                                NovelWorkCard(
                                    work = work,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onClick = onWorkClick,
                                    dark = dark,
                                )
                            } else {
                                ProportionalWorkCard(
                                    work = work,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onClick = onWorkClick,
                                    dark = dark,
                                    // 这一页全是同一个作者，卡片再挂一行作者名是纯噪声
                                    showAuthor = false,
                                )
                            }
                        }
                        if (state.loadMoreErrorRes != null) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                AuthorNotice(
                                    text = stringResource(state.loadMoreErrorRes ?: R.string.home_error_parse),
                                    actionText = stringResource(R.string.common_retry),
                                    onAction = viewModel::retryLoadMore,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAvatarViewer) {
            AvatarViewerDialog(
                avatarUrl = profile?.avatarUrl,
                onDismiss = { showAvatarViewer = false },
                onSave = viewModel::saveAvatar,
            )
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

/** 顶栏：名字在头部卡片滚出后淡入；更多里放复制链接与出站 */
@Composable
private fun AuthorTopBar(
    userName: String,
    titleAlpha: Float,
    onBack: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TopBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = PikuColors.textPrimary,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = userName,
            color = PikuColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = titleAlpha },
        )
        Box {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = PikuColors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = PikuColors.surface,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.author_copy_link), fontSize = 14.sp) },
                    onClick = {
                        menuOpen = false
                        onCopyLink()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_open_browser), fontSize = 14.sp) },
                    onClick = {
                        menuOpen = false
                        onOpenBrowser()
                    },
                )
            }
        }
    }
}

/** 头部卡片：横幅 + 左下头像 + 名字/@account·关注数 + 关注胶囊，随滚动折叠并带横幅视差 */
@Composable
private fun AuthorHeaderCard(
    avatarUrl: String?,
    bannerUrl: String?,
    name: String,
    account: String,
    followCount: Int?,
    premium: Boolean,
    followed: Boolean,
    showFollow: Boolean,
    followSending: Boolean,
    collapseProgress: () -> Float,
    onAvatarClick: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val density = LocalDensity.current
    val parallaxPx = with(density) { BannerParallax.toPx() }
    val liftPx = with(density) { BannerHeight.toPx() }
    // 关注数跟身份信息走：它只有一个数字，单独占一行太浪费
    val followingText = followCount?.let { stringResource(R.string.author_following_count, it) }
    val metaLine = listOfNotNull(
        account.takeIf { it.isNotBlank() }?.let { "@$it" },
        followingText,
    ).joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BannerHeight)
            .clip(shape)
            .border(BorderStroke(0.5.dp, Color(0x66FFFFFF)), shape),
    ) {
        // 视差层：预先加高 BannerParallax，滚动时整体下移，速度慢于卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight + BannerParallax)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val p = collapseProgress()
                    translationY = p * parallaxPx
                    alpha = 1f - p * 0.9f
                },
        ) {
            // 渐变永远铺底：横幅图在不受管的域时取不到（官方默认背景），加载中也别留白
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFFE9E3D8), Color(0xFFBFB5A3)))),
            )
            if (!bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // 底部渐变遮罩保证白字可读，同样随折叠淡出
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - collapseProgress() * 0.9f }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x42201A14), Color(0x00201A14), Color(0x94201A14)),
                    ),
                ),
        )
        // 信息行：向上收进卡片、随后淡出，让位给顶栏
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 14.dp, bottom = 13.dp)
                .graphicsLayer {
                    val p = collapseProgress()
                    translationY = -p * liftPx
                    alpha = 1f - ((p - 0.5f) / 0.4f).coerceIn(0f, 1f)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(AvatarSize)
                    .graphicsLayer {
                        // 向自己的左下角缩，和 poipiku 作者页一致
                        val scale = 1f - 0.55f * collapseProgress()
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 1f)
                    }
                    .clip(CircleShape)
                    .background(Color(0xFFAFA595))
                    .border(2.5.dp, Color.White, CircleShape)
                    .clickable(enabled = !avatarUrl.isNullOrBlank(), onClick = onAvatarClick),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        // 名字比信息行更早消失：头像还在时名字已经开始淡出
                        alpha = (1f - collapseProgress() / 0.55f).coerceIn(0f, 1f)
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (premium) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "P",
                            color = Color(0xFF4A3A18),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFE6C88A), Color(0xFFC9A25C)),
                                    ),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.5.dp),
                        )
                    }
                }
                if (metaLine.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = metaLine,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showFollow) {
                Spacer(Modifier.width(8.dp))
                FollowOnBanner(
                    followed = followed,
                    sending = followSending,
                    onClick = onToggleFollow,
                )
            }
        }
    }
}

@Composable
private fun FollowOnBanner(followed: Boolean, sending: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = stringResource(if (followed) R.string.detail_followed else R.string.detail_follow),
        color = if (followed) Color.White else PikuColors.accent,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (followed) Color(0x3DFFFFFF) else Color.White)
            .then(if (followed) Modifier.border(1.dp, Color(0x9EFFFFFF), shape) else Modifier)
            .clickable(enabled = !sending, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

@Composable
private fun AuthorProfileBody(
    profile: AuthorProfile,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    translatedBio: String?,
    showTranslatedBio: Boolean,
    translating: Boolean,
    canTranslate: Boolean,
    onToggleTranslate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        val original = profile.comment
        val shown = translatedBio?.takeIf { showTranslatedBio } ?: original
        if (original.isNotBlank()) {
            // 「更多」由排版结果决定，不靠字数猜：中英混排每行装多少字算不准
            var overflowed by remember(shown) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = shown,
                    color = PikuColors.textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 21.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else BIO_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflowed = it.hasVisualOverflow },
                    modifier = Modifier.weight(1f),
                )
                if (canTranslate) {
                    Spacer(Modifier.width(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TranslateChip(
                            showTranslation = showTranslatedBio,
                            onClick = onToggleTranslate,
                            modifier = Modifier.alpha(if (translating) 0.4f else 1f),
                        )
                        if (translating) {
                            Spacer(Modifier.width(4.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = PikuColors.textSecondary,
                            )
                        }
                    }
                }
            }
            if (overflowed || expanded) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.author_bio_collapse else R.string.author_bio_more,
                    ),
                    color = PikuColors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleExpand)
                        .padding(2.dp),
                )
            }
        }

        // 一行只放外链。作品数/收藏数长在 Tab 上，关注数跟身份信息走了——这一行没有可重复的东西，
        // 没有外链时整行不出现
        val links = listOfNotNull(
            profile.twitterUrl?.let { AuthorLink("Twitter", "@${handleOf(it)}", it) },
            profile.webpage?.let { AuthorLink("Web", hostOf(it), it) },
        )
        if (links.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                links.forEachIndexed { index, link ->
                    if (index > 0) Spacer(Modifier.width(7.dp))
                    AuthorLinkChip(link = link)
                }
            }
        }
    }
}

/** 一条外链：图标按域名选，文字显示真实的账号或域名 */
private data class AuthorLink(val kind: String, val label: String, val url: String)

@Composable
private fun AuthorLinkChip(link: AuthorLink) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            // 上限管住长域名：两颗 chip 加起来也塞得进一行，超出的部分省略
            .widthIn(max = 150.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xE6FFFFFF))
            .border(0.5.dp, PikuColors.border, RoundedCornerShape(50))
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
            }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (link.kind == "Twitter") {
            Icon(
                imageVector = Icons.Outlined.AlternateEmail,
                contentDescription = null,
                tint = PikuColors.textSecondary,
                modifier = Modifier.size(11.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = PikuColors.textSecondary,
                modifier = Modifier.size(11.dp),
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = link.label,
            color = PikuColors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `https://twitter.com/kuro_hady/` → `kuro_hady`；取不到就给空串，调用方据此不画这个 chip */
private fun handleOf(url: String): String =
    url.trimEnd('/').substringAfterLast('/').removePrefix("@")

/** `https://www.kurohady.com/works` → `kurohady.com` */
private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').removePrefix("www.")

/** Tab 行：相关画师取不到就整枚不出现，不给一个永远空着的入口 */
@Composable
private fun AuthorTabRow(state: AuthorUiState, onSelect: (AuthorTab) -> Unit) {
    val labelIllust = stringResource(R.string.author_tab_illust)
    val labelManga = stringResource(R.string.author_tab_manga)
    val labelBookmarks = stringResource(R.string.author_tab_bookmarks)
    val labelNovel = stringResource(R.string.author_tab_novel)

    data class TabItem(val tab: AuthorTab, val label: String, val count: Int?)

    val tabs = buildList {
        add(TabItem(AuthorTab.ILLUST, labelIllust, state.illustCount))
        add(TabItem(AuthorTab.MANGA, labelManga, state.mangaCount))
        // 计数来自资料的公开收藏数，非公开池子上不显示——接口不给非公开计数，别拿公开数冒充
        add(
            TabItem(
                AuthorTab.BOOKMARKS,
                labelBookmarks,
                if (state.bookmarkPrivate) null else state.bookmarkCount,
            ),
        )
        add(TabItem(AuthorTab.NOVEL, labelNovel, state.novelCount))
    }

    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        // 整行底线；选中指示条压在它上面，读起来是「贴在线上」而不是浮在半空
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(PikuColors.border),
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { item ->
                val selected = state.tab == item.tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(item.tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.label,
                            color = if (selected) PikuColors.textPrimary else PikuColors.textSecondary,
                            fontSize = if (selected) 14.5.sp else 13.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                        if (item.count != null) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = item.count.toString(),
                                color = if (selected) PikuColors.textSecondary else PikuColors.textFaint,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) PikuColors.accent else Color.Transparent),
                    )
                }
            }
        }
    }
}

/**
 * 收藏池子切换：公开 / 非公开。样式与投稿箱的筛选项同一套 chip 语言——
 * 这只是列表内的一次过滤，压不过上面那排真正的 Tab。
 */
@Composable
private fun BookmarkPoolToggle(
    private: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookmarkPoolChip(
            text = stringResource(R.string.pixiv_mirror_public_short),
            selected = !private,
            onClick = { onSelect(false) },
        )
        Spacer(Modifier.width(8.dp))
        BookmarkPoolChip(
            text = stringResource(R.string.pixiv_mirror_private_short),
            selected = private,
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun BookmarkPoolChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) PikuColors.accent
                else PikuColors.textSecondary.copy(alpha = 0.08f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) onAccent() else PikuColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AuthorNotice(text: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = PikuColors.textFaint,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = actionText,
                color = PikuColors.accent,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
