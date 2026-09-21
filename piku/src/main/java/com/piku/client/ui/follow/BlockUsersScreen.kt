package com.piku.client.ui.follow

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.piku.client.ui.common.FeedbackHost
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.domain.model.FollowUser
import com.piku.client.ui.common.GlassCard
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.LoginPrompt
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun BlockUsersScreen(
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onUserClick: (FollowUser) -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: BlockUsersViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    FeedbackHost(channel = viewModel.feedback, snackbarHostState = snackbarHostState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            BlockTopBar(
                count = if (state.users.isNotEmpty()) {
                    stringResource(R.string.block_users_count, state.users.size)
                } else null,
                onBack = onBack,
                dark = dark,
            )
            when {
                state.needLogin -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoginPrompt(
                            message = stringResource(R.string.block_users_login),
                            onLogin = onLoginClick,
                            dark = dark,
                        )
                    }
                }
                state.loading && state.users.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
                state.errorRes != null && state.users.isEmpty() -> {
                    val errorRes = state.errorRes
                    BlockErrorState(errorRes = errorRes ?: R.string.home_error_parse, onRetry = viewModel::retry, dark = dark)
                }
                state.users.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Block,
                                contentDescription = null,
                                tint = PikuColors.textFaint,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.block_users_empty),
                                color = PikuColors.textSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }
                else -> {
                    BlockUserList(
                        state = state,
                        dark = dark,
                        onUserClick = onUserClick,
                        onUnblock = viewModel::unblock,
                        onLoadMore = viewModel::loadMore,
                        onRetryLoadMore = viewModel::retryLoadMore,
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
private fun BlockTopBar(
    count: String?,
    onBack: () -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
            .border(BorderStroke(0.5.dp, PikuColors.border))
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PikuBackButton(
            onClick = onBack,
            dark = dark,
            contentDescription = stringResource(R.string.back),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.block_users_title),
                color = PikuColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count != null) {
                Text(
                    text = count,
                    color = PikuColors.textFaint,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun BlockUserList(
    state: BlockUsersUiState,
    dark: Boolean,
    onUserClick: (FollowUser) -> Unit,
    onUnblock: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.users.size) {
        snapshotFlow {
            val info = listState.layoutInfo
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.users, key = { it.userId }) { user ->
            BlockUserRow(
                user = user,
                unblocking = user.userId in state.unblockingIds,
                dark = dark,
                onClick = { onUserClick(user) },
                onUnblock = { onUnblock(user.userId) },
                modifier = Modifier.animateItem(),
            )
        }
        when {
            state.loadMoreErrorRes != null -> {
                item {
                    BlockLoadMoreError(errorRes = state.loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            state.loadingMore -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockUserRow(
    user: FollowUser,
    unblocking: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    GlassCard(
        dark = dark,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(avatarUrl = user.avatarUrl, onClick = onClick, dark = dark, size = 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                // 本地名单可能只有 ID（如从缺昵称的上下文屏蔽）：主文案兜底显示 ID
                Text(
                    text = user.name.ifBlank { "ID: ${user.userId}" },
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.name.isNotBlank()) {
                    Text(
                        text = "ID: ${user.userId}",
                        color = PikuColors.textFaint,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            BlockPillButton(
                unblocking = unblocking,
                dark = dark,
                onClick = onUnblock,
            )
        }
    }
}

/** 解除屏蔽按钮：与关注列表的 FollowPillButton 同款胶囊样式 */
@Composable
private fun BlockPillButton(
    unblocking: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val bg = if (dark) Color(0x30FFFFFF) else Color(0x14262421)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(BorderStroke(0.5.dp, PikuColors.border), shape)
            .clickable(enabled = !unblocking, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (unblocking) {
            LoaderDots(dark = dark)
        } else {
            Text(
                text = stringResource(R.string.block_users_unblock),
                color = PikuColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun BlockErrorState(
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
            color = PikuColors.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(shape)
                .background(PikuColors.surface)
                .border(
                    BorderStroke(0.5.dp, PikuColors.border),
                    shape,
                )
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_retry),
                color = PikuColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BlockLoadMoreError(
    errorRes: Int,
    onRetry: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .clip(shape)
            .background(PikuColors.surface)
            .border(
                BorderStroke(0.5.dp, PikuColors.border),
                shape,
            )
            .clickable(onClick = onRetry)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(errorRes),
            color = PikuColors.textFaint,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.home_retry),
            color = PikuColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
