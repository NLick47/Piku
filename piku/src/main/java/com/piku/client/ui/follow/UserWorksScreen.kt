package com.piku.client.ui.follow

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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.UserPageInfo
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
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
import com.piku.client.ui.theme.StarSkyBottomDark
import com.piku.client.ui.theme.StarSkyBottomLight
import com.piku.client.ui.theme.StarSkyMidDark
import com.piku.client.ui.theme.StarSkyMidLight
import com.piku.client.ui.theme.StarSkyTopDark
import com.piku.client.ui.theme.StarSkyTopLight
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 作者头部卡片完全展开时的高度 */
private val HeaderCardHeight = 172.dp

/** 页头图视差行程（图片预先加高此量，滚动时缓慢下移形成层次） */
private val HeaderParallax = 52.dp

/** 折叠接管顶栏的起始/完成进度（0~1） */
private const val CollapseTakeoverStart = 0.38f
private const val CollapseTakeoverEnd = 0.68f

/** 单个用户的作品列表页：折叠式作者头部卡片（页头图/头像/关注）+ 瀑布流作品 */
@Composable
fun UserWorksScreen(
    onBack: () -> Unit,
    onWorkClick: (Work) -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: UserWorksViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val linkCopiedMessage = stringResource(R.string.detail_link_copied)
    val userPageUrl = "https://poipiku.com/${state.userId}.html"

    val followFeedback = state.followFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(followFeedback) {
        if (followFeedback != null) {
            snackbarHostState.showSnackbar(followFeedback)
            viewModel.clearFollowFeedback()
        }
    }

    // 折叠进度：0=完全展开，1=头部卡片完全滚出（顶栏接管）
    val gridState = rememberLazyStaggeredGridState()
    val density = LocalDensity.current
    val headerHeightPx = with(density) { HeaderCardHeight.toPx() }
    val collapseProgress by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
            if (first.index > 0) 1f
            else (-first.offset.y.toFloat() / headerHeightPx).coerceIn(0f, 1f)
        }
    }

    val avatarUrl = state.pageInfo?.avatarUrl ?: state.works.firstOrNull()?.authorAvatarUrl

    Box(Modifier.fillMaxSize()) {
        // 整页背景：简单渐变（作者背景显示在头部卡片内）
        UserWorksPageBackground(dark = dark)
        Column(Modifier.fillMaxSize()) {
            UserWorksTopBar(
                userName = state.userName,
                userId = state.userId,
                avatarUrl = avatarUrl,
                collapseProgress = collapseProgress,
                onBack = onBack,
                onCopyLink = {
                    clipboard.setText(AnnotatedString(userPageUrl))
                    scope.launch {
                        snackbarHostState.showSnackbar(linkCopiedMessage)
                    }
                },
                onOpenBrowser = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(userPageUrl)))
                },
                dark = dark,
            )
            when {
                state.loading && state.works.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
                state.errorRes != null && state.works.isEmpty() -> {
                    val errorRes = state.errorRes
                    UserWorksErrorState(errorRes = errorRes ?: R.string.home_error_parse, onRetry = viewModel::retry, dark = dark)
                }
                state.works.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.user_works_empty),
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 14.sp,
                        )
                    }
                }
                else -> {
                    UserWorksGrid(
                        state = state,
                        gridState = gridState,
                        collapseProgress = collapseProgress,
                        dark = dark,
                        isTablet = isTablet,
                        onLoadMore = viewModel::loadMore,
                        onRetryLoadMore = viewModel::retryLoadMore,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleFollow = viewModel::toggleFollow,
                        onWorkClick = onWorkClick,
                    )
                }
            }
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

@Composable
private fun UserWorksPageBackground(dark: Boolean) {
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
}

/** 解析 "#RGB" / "#RRGGBB" / "#AARRGGBB" 十六进制颜色，非法返回 null */
private fun parseHexColor(hex: String): Color? = try {
    val clean = hex.removePrefix("#")
    when (clean.length) {
        3 -> {
            val r = clean[0].digitToInt(16) * 17
            val g = clean[1].digitToInt(16) * 17
            val b = clean[2].digitToInt(16) * 17
            Color(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
        }
        6 -> Color(0xFF000000.toInt() or clean.toInt(16))
        8 -> Color(clean.toLong(16).toInt())
        else -> null
    }
} catch (_: Exception) {
    null
}

/** 顶栏：默认显示「X 的作品 + ID」，头部卡片折叠时渐变为「小头像 + 昵称」紧凑模式 */
@Composable
private fun UserWorksTopBar(
    userName: String,
    userId: Long,
    avatarUrl: String?,
    collapseProgress: Float,
    onBack: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenBrowser: () -> Unit,
    dark: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 折叠接管区间内交叉淡入淡出
    val takeover = ((collapseProgress - CollapseTakeoverStart) /
        (CollapseTakeoverEnd - CollapseTakeoverStart)).coerceIn(0f, 1f)
    val compactAlpha = takeover
    val normalAlpha = 1f - takeover

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                // 常规模式：标题 + ID
                Column(Modifier.alpha(normalAlpha)) {
                    Text(
                        text = if (userName.isNotBlank()) {
                            stringResource(R.string.user_works_title, userName)
                        } else {
                            stringResource(R.string.user_works_title, userId.toString())
                        },
                        color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "ID: $userId",
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 紧凑模式：小头像 + 昵称
                Row(
                    modifier = Modifier
                        .alpha(compactAlpha)
                        .align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniAvatar(avatarUrl = avatarUrl, dark = dark, size = 28.dp)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = userName.ifBlank { userId.toString() },
                        color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            var anchorHeightPx by remember { mutableIntStateOf(0) }
            Box(
                Modifier.onSizeChanged { anchorHeightPx = it.height },
            ) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.detail_more),
                        tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (menuExpanded) {
                    UserWorksMoreMenu(
                        anchorHeightPx = anchorHeightPx,
                        dark = dark,
                        onDismiss = { menuExpanded = false },
                        onCopyLink = {
                            menuExpanded = false
                            onCopyLink()
                        },
                        onOpenBrowser = {
                            menuExpanded = false
                            onOpenBrowser()
                        },
                    )
                }
            }
        }
        // 玻璃分隔线
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(if (dark) Color(0x1AFFFFFF) else Color(0x14000000)),
        )
    }
}

/** 更多操作：锚定在顶栏按钮下方的玻璃风格小弹窗，与作品详情页「更多」弹窗同款（弹窗顶缘紧贴按钮底缘 +8dp） */
@Composable
private fun UserWorksMoreMenu(
    anchorHeightPx: Int,
    dark: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(18.dp)
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, anchorHeightPx + with(density) { 8.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .shadow(14.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x55000000))
                .clip(shape)
                .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
                .border(
                    BorderStroke(0.5.dp, if (dark) Color(0x59FFFFFF) else Color(0x59C8C2B8)),
                    shape,
                )
                .padding(vertical = 6.dp),
        ) {
            UserWorksMoreMenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = if (dark) LoginTextPrimaryDark else AccentDark,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = stringResource(R.string.detail_copy_link),
                dark = dark,
                onClick = onCopyLink,
            )
            UserWorksMoreMenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.OpenInBrowser,
                        contentDescription = null,
                        tint = if (dark) LoginTextPrimaryDark else AccentDark,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = stringResource(R.string.detail_open_browser),
                dark = dark,
                onClick = onOpenBrowser,
            )
        }
    }
}

@Composable
private fun UserWorksMoreMenuRow(
    icon: @Composable () -> Unit,
    label: String,
    dark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(
            text = label,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 13.sp,
        )
    }
}

/** 小圆形头像（顶栏紧凑模式） */
@Composable
private fun MiniAvatar(
    avatarUrl: String?,
    dark: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) LoginTextFaintDark else LoginTextFaintLight)
            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}

/** 作者头部卡片：页头 banner 图（视差）+ 头像/昵称/作品数（随滚动上移缩放）+ 关注按钮 */
@Composable
private fun UserWorksHeaderCard(
    pageInfo: UserPageInfo?,
    userName: String,
    userId: Long,
    fallbackAvatarUrl: String?,
    collapseProgress: Float,
    followed: Boolean,
    followSending: Boolean,
    loggedIn: Boolean,
    onToggleFollow: () -> Unit,
    dark: Boolean,
) {
    val headerUrl = pageInfo?.headerUrl
    val avatarUrl = pageInfo?.avatarUrl ?: fallbackAvatarUrl
    val name = userName
        .ifBlank { pageInfo?.userName.orEmpty() }
        .ifBlank { userId.toString() }
    val workCount = pageInfo?.workCount
    val shape = RoundedCornerShape(18.dp)
    val density = LocalDensity.current

    // 折叠派生动画值（graphicsLayer 驱动，不触发重组）
    val contentLiftPx = with(density) { (HeaderCardHeight * collapseProgress).toPx() }
    val avatarScale = 1f - 0.55f * collapseProgress
    val contentAlpha = 1f - ((collapseProgress - 0.5f) / 0.4f).coerceIn(0f, 1f)
    val textAlpha = (1f - collapseProgress / 0.55f).coerceIn(0f, 1f)
    val imageAlpha = 1f - collapseProgress * 0.9f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderCardHeight)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x33000000),
            )
            .clip(shape)
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x26FFFFFF) else Color(0x66FFFFFF)),
                shape,
            ),
    ) {
        // 页头图：视差层（预先加高，滚动时缓慢下移，速度慢于卡片）
        if (headerUrl != null) {
            AsyncImage(
                model = headerUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeaderCardHeight + HeaderParallax)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = collapseProgress * with(density) { HeaderParallax.toPx() }
                        alpha = imageAlpha
                    },
                contentScale = ContentScale.Crop,
            )
        } else {
            // 无页头图
            val bgImageUrl = pageInfo?.bgImageUrl
            val bgColor = pageInfo?.bgColorHex?.let(::parseHexColor)
            when {
                bgImageUrl != null -> AsyncImage(
                    model = bgImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = imageAlpha },
                    contentScale = ContentScale.Crop,
                )
                bgColor != null -> Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = imageAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(bgColor, bgColor.copy(alpha = 0.72f)),
                            ),
                        ),
                )
                else -> Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = imageAlpha }
                        .background(
                            Brush.verticalGradient(
                                if (dark) listOf(StarSkyTopDark, StarSkyMidDark, StarSkyBottomDark)
                                else listOf(StarSkyTopLight, StarSkyMidLight, StarSkyBottomLight),
                            ),
                        ),
                )
            }
        }
        // 底部渐变遮罩保证文字可读性（亮色下适当减淡，避免底部发黑）
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = imageAlpha }
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, if (dark) Color(0xCC000000) else Color(0xA6000000)),
                    ),
                ),
        )
        // 信息行：随滚动上移 + 缩放，最后淡出让顶栏接管
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 12.dp, bottom = 14.dp)
                .graphicsLayer {
                    translationY = -contentLiftPx
                    alpha = contentAlpha
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 大头像（白色描边，随折叠缩至 ~29dp）
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = avatarScale
                        scaleY = avatarScale
                        transformOrigin = TransformOrigin(0f, 1f)
                    }
                    .clip(CircleShape)
                    .background(if (dark) LoginTextFaintDark else LoginTextFaintLight)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = textAlpha },
            ) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("ID: ")
                        append(userId)
                        if (workCount != null) {
                            append(" · ")
                            append(stringResource(R.string.user_works_count, workCount))
                        }
                    },
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 关注按钮（玻璃胶囊）
            if (loggedIn) {
                Spacer(Modifier.width(8.dp))
                FollowGlassButton(
                    followed = followed,
                    sending = followSending,
                    onClick = onToggleFollow,
                )
            }
        }
    }
}

/** 玻璃质感胶囊关注按钮：已关注=半透明玻璃，未关注=实白强调 */
@Composable
private fun FollowGlassButton(
    followed: Boolean,
    sending: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val bgColor = if (followed) Color(0x30FFFFFF) else Color(0xF2FFFFFF)
    val contentColor = if (followed) Color.White else Color(0xFF2C2C2C)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(0.5.dp, Color(0x66FFFFFF)), shape)
            .clickable(enabled = !sending, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = contentColor,
                strokeWidth = 1.5.dp,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = stringResource(if (followed) R.string.detail_followed else R.string.detail_follow),
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UserWorksGrid(
    state: UserWorksUiState,
    gridState: LazyStaggeredGridState,
    collapseProgress: Float,
    dark: Boolean,
    isTablet: Boolean,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onToggleFollow: () -> Unit,
    onWorkClick: (Work) -> Unit,
) {
    LaunchedEffect(gridState, state.works.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !state.endReached && !state.loadingMore && state.loadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = if (isTablet) StaggeredGridCells.Adaptive(220.dp) else StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        // 作者头部卡片（折叠式），仅在解析到页头信息后显示
        if (state.pageInfo != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                UserWorksHeaderCard(
                    pageInfo = state.pageInfo,
                    userName = state.userName,
                    userId = state.userId,
                    fallbackAvatarUrl = state.works.firstOrNull()?.authorAvatarUrl,
                    collapseProgress = collapseProgress,
                    followed = state.followed,
                    followSending = state.followSending,
                    loggedIn = state.loggedIn,
                    onToggleFollow = onToggleFollow,
                    dark = dark,
                )
            }
        }
        items(state.works, key = { it.id }) { work ->
            WorkCard(
                work = work,
                isFavorite = work.id.toString() in state.favoriteIds,
                onToggleFavorite = { onToggleFavorite(work) },
                onClick = { onWorkClick(work) },
                dark = dark,
            )
        }
        when {
            state.loadMoreErrorRes != null -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    UserWorksLoadMoreError(errorRes = state.loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            state.loadingMore -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            state.endReached -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_more),
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserWorksErrorState(
    errorRes: Int,
    onRetry: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(errorRes),
            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (dark) LoginCardDark else LoginCardLight)
                .border(
                    BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                    shape,
                )
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_retry),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun UserWorksLoadMoreError(
    errorRes: Int,
    onRetry: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .clip(shape)
            .background(if (dark) LoginCardDark else LoginCardLight)
            .border(
                BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                shape,
            )
            .clickable(onClick = onRetry)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(errorRes),
            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.home_retry),
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
