package com.piku.client.ui.follow

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.theme.AccentPurple
import com.piku.client.ui.theme.GlassCardBorderDark
import com.piku.client.ui.theme.GlassCardBorderLight
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
import kotlinx.coroutines.flow.distinctUntilChanged

/** 我的关注列表页：展示关注的创作者，可跳转其作品页或在行内取消关注 */
@Composable
fun FollowUsersScreen(
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onUserClick: (FollowUser) -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: FollowUsersViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val feedbackMessage = state.actionFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            snackbarHostState.showSnackbar(feedbackMessage)
            viewModel.clearFeedback()
        }
    }

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
            FollowTopBar(
                title = stringResource(R.string.follow_users_title),
                count = if (state.total > 0) {
                    stringResource(R.string.follow_users_count, state.total)
                } else null,
                onBack = onBack,
                dark = dark,
            )
            when {
                state.followNeedLogin -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        FollowLoginPrompt(onLogin = onLoginClick, dark = dark)
                    }
                }
                state.loading && state.users.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
                state.errorRes != null && state.users.isEmpty() -> {
                    val errorRes = state.errorRes
                    FollowErrorState(errorRes = errorRes ?: R.string.home_error_parse, onRetry = viewModel::retry, dark = dark)
                }
                state.users.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.follow_users_empty),
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
                else -> {
                    FollowUserList(
                        state = state,
                        dark = dark,
                        onUserClick = onUserClick,
                        onUnfollow = viewModel::unfollow,
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
private fun FollowTopBar(
    title: String,
    count: String?,
    onBack: () -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count != null) {
                Text(
                    text = count,
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun FollowUserList(
    state: FollowUsersUiState,
    dark: Boolean,
    onUserClick: (FollowUser) -> Unit,
    onUnfollow: (Long) -> Unit,
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
            FollowUserRow(
                user = user,
                unfollowing = user.userId in state.unfollowingIds,
                unfollowed = user.userId in state.unfollowedIds,
                dark = dark,
                onClick = { onUserClick(user) },
                onUnfollow = { onUnfollow(user.userId) },
            )
        }
        when {
            state.loadMoreErrorRes != null -> {
                item {
                    FollowLoadMoreError(errorRes = state.loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            state.loadingMore -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            state.endReached && state.users.size >= 30 -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
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
private fun FollowUserRow(
    user: FollowUser,
    unfollowing: Boolean,
    unfollowed: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    onUnfollow: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    GlassCard(
        dark = dark,
        shape = shape,
        modifier = Modifier
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
                Text(
                    text = user.name,
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "ID: ${user.userId}",
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            FollowPillButton(
                unfollowed = unfollowed,
                sending = unfollowing,
                dark = dark,
                onClick = onUnfollow,
            )
        }
    }
}

/**
 * 玻璃质感胶囊关注按钮：
 * - 已关注（默认）：柔和红玻璃，点击取消关注
 * - 已取消：中性玻璃，点击重新关注
 * - 操作中：转圈 + 禁用
 */
@Composable
private fun FollowPillButton(
    unfollowed: Boolean,
    sending: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        label = "followPillPress",
    )

    val bgColor = when {
        sending -> if (dark) Color(0x14FFFFFF) else Color(0x0D2C2C2C)
        unfollowed -> if (dark) Color(0x14FFFFFF) else Color(0x0D2C2C2C)
        else -> if (dark) Color(0x14E08A8A) else Color(0x0FC24B4B)
    }
    val borderColor = when {
        sending -> if (dark) Color(0x26FFFFFF) else Color(0x1A2C2C2C)
        unfollowed -> if (dark) Color(0x33FFFFFF) else Color(0x242C2C2C)
        else -> if (dark) Color(0x33E08A8A) else Color(0x26C24B4B)
    }
    val contentColor = when {
        sending -> if (dark) LoginTextFaintDark else LoginTextFaintLight
        unfollowed -> if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
        else -> if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B)
    }

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(0.5.dp, borderColor), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor.copy(alpha = 0.18f)),
                enabled = !sending,
                onClick = onClick,
            )
            .padding(horizontal = 15.dp, vertical = 7.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = contentColor,
                strokeWidth = 1.5.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.follow_user_unfollow),
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Crossfade(targetState = unfollowed, label = "followPillState") { alreadyUnfollowed ->
                Text(
                    text = stringResource(
                        if (alreadyUnfollowed) R.string.follow_user_refollow else R.string.follow_user_unfollow
                    ),
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FollowLoginPrompt(
    onLogin: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.follow_users_login),
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
                .clickable(onClick = onLogin)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.drawer_login_hint),
                color = if (dark) LoginTextPrimaryDark else AccentPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FollowErrorState(
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
private fun FollowLoadMoreError(
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
