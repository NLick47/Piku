package com.piku.client.ui.search

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.domain.model.FollowUser
import com.piku.client.ui.common.FollowPillButton
import com.piku.client.ui.common.GlassCard
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.LoginPrompt
import com.piku.client.ui.common.UserAvatar
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

/** 作者搜索页：@用户名/ID 前缀进入，展示匹配的用户卡片，可点击进作者主页或行内关注 */
@Composable
fun UserSearchScreen(
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onUserClick: (FollowUser) -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: UserSearchViewModel = hiltViewModel()
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
            SearchTopBar(
                title = stringResource(R.string.search_users_title, state.keyword),
                onBack = onBack,
                dark = dark,
            )
            when {
                state.searchNeedLogin -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoginPrompt(
                            message = stringResource(R.string.search_users_login),
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
                    SearchErrorState(
                        errorRes = errorRes ?: R.string.home_error_parse,
                        onRetry = viewModel::retry,
                        dark = dark,
                    )
                }
                state.users.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.search_users_empty),
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
                else -> {
                    SearchUserList(
                        state = state,
                        dark = dark,
                        onUserClick = onUserClick,
                        onToggleFollow = viewModel::toggleFollow,
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
private fun SearchTopBar(
    title: String,
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
        Text(
            text = title,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchUserList(
    state: UserSearchUiState,
    dark: Boolean,
    onUserClick: (FollowUser) -> Unit,
    onToggleFollow: (Long) -> Unit,
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
            val followed = state.followOverrides[user.userId] ?: user.followed
            SearchUserRow(
                user = user,
                followed = followed,
                followSending = user.userId in state.followPendingIds,
                dark = dark,
                onClick = { onUserClick(user) },
                onToggleFollow = { onToggleFollow(user.userId) },
            )
        }
        when {
            state.loadMoreErrorRes != null -> {
                item {
                    SearchLoadMoreError(errorRes = state.loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
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
private fun SearchUserRow(
    user: FollowUser,
    followed: Boolean,
    followSending: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
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
                unfollowed = !followed,
                sending = followSending,
                dark = dark,
                onClick = onToggleFollow,
            )
        }
    }
}

@Composable
private fun SearchErrorState(
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
private fun SearchLoadMoreError(
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