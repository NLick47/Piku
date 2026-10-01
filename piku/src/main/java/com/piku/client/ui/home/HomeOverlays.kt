package com.piku.client.ui.home

import androidx.compose.runtime.Composable

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.piku.client.domain.model.Work
import com.piku.client.ui.collection.CollectionScreen
import com.piku.client.ui.follow.BlockUsersScreen
import com.piku.client.ui.follow.FollowUsersScreen
import com.piku.client.ui.history.HistoryScreen
import com.piku.client.ui.home.drawer.AccountsScreen
import com.piku.client.ui.home.drawer.SourceAccountRow
import com.piku.client.ui.tags.TagScreen

/**
 * 抽屉功能页的承载：账号、浏览记录、收藏、标签、关注/屏蔽列表。
 * 都是全屏 Dialog 浮层（独立窗口天然挡住首页点击），返回时由宿主重新拉出抽屉。
 */
@Composable
internal fun HomeOverlays(
    showAccountsPage: Boolean,
    onAccountsBack: () -> Unit,
    onAccountsLogin: (SourceAccountRow) -> Unit,
    showHistoryPage: Boolean,
    onHistoryBack: () -> Unit,
    showCollectionPage: Boolean,
    onCollectionBack: () -> Unit,
    showTagsPage: Boolean,
    onTagsBack: () -> Unit,
    showFollowUsersPage: Boolean,
    onFollowUsersBack: () -> Unit,
    showBlockUsersPage: Boolean,
    onBlockUsersBack: () -> Unit,
    onWorkClick: (Work) -> Unit,
    onLoginClick: () -> Unit,
    onProfileOpen: (Long, String) -> Unit,
    state: HomeUiState,
    dark: Boolean,
) {
    val fullScreenProps = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
        dismissOnClickOutside = false,
    )

    if (showAccountsPage) {
        Dialog(onDismissRequest = onAccountsBack, properties = fullScreenProps) {
            AccountsScreen(onBack = onAccountsBack, onLogin = onAccountsLogin, dark = dark)
        }
    }
    if (showHistoryPage) {
        Dialog(onDismissRequest = onHistoryBack, properties = fullScreenProps) {
            HistoryScreen(onBack = onHistoryBack, onWorkClick = { onWorkClick(it) })
        }
    }
    if (showCollectionPage) {
        Dialog(onDismissRequest = onCollectionBack, properties = fullScreenProps) {
            CollectionScreen(
                onBack = onCollectionBack,
                onWorkClick = { onWorkClick(it) },
                onAuthorClick = { work -> onProfileOpen(work.authorId, work.authorName) },
            )
        }
    }
    if (showTagsPage) {
        Dialog(onDismissRequest = onTagsBack, properties = fullScreenProps) {
            TagScreen(onBack = onTagsBack, onWorkClick = { onWorkClick(it) })
        }
    }
    if (showFollowUsersPage) {
        Dialog(onDismissRequest = onFollowUsersBack, properties = fullScreenProps) {
            FollowUsersScreen(
                onBack = onFollowUsersBack,
                onLoginClick = onLoginClick,
                onUserClick = { user -> onProfileOpen(user.userId, user.name) },
            )
        }
    }
    if (showBlockUsersPage) {
        Dialog(onDismissRequest = onBlockUsersBack, properties = fullScreenProps) {
            BlockUsersScreen(
                onBack = onBlockUsersBack,
                onLoginClick = onLoginClick,
                onUserClick = { user -> onProfileOpen(user.userId, user.name) },
            )
        }
    }
}
