package com.piku.client.ui.home

import androidx.compose.runtime.Composable

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.piku.client.domain.model.Work
import com.piku.client.ui.collection.CollectionScreen
import com.piku.client.ui.history.HistoryScreen
import com.piku.client.ui.home.drawer.AccountsScreen
import com.piku.client.ui.home.drawer.SourceAccountRow
import com.piku.client.ui.tags.TagScreen

/**
 * 抽屉通用功能页的承载：账号、浏览记录、收藏、标签。
 * 都是全屏 Dialog 浮层（独立窗口天然挡住首页点击），返回时由宿主重新拉出抽屉。
 * 关注/屏蔽列表是 poipiku 账号的能力，浮层随条目归了它的抽屉插件（PoipikuDrawerPlugin）。
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
    onWorkClick: (Work) -> Unit,
    /** 浮层里点作者：带上作品，去向由宿主按源分发 */
    onOpenAuthor: (Work) -> Unit,
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
            HistoryScreen(
                onBack = onHistoryBack,
                onWorkClick = { onWorkClick(it) },
            )
        }
    }
    if (showCollectionPage) {
        Dialog(onDismissRequest = onCollectionBack, properties = fullScreenProps) {
            CollectionScreen(
                onBack = onCollectionBack,
                onWorkClick = { onWorkClick(it) },
                onAuthorClick = onOpenAuthor,
            )
        }
    }
    if (showTagsPage) {
        Dialog(onDismissRequest = onTagsBack, properties = fullScreenProps) {
            TagScreen(onBack = onTagsBack, onWorkClick = { onWorkClick(it) })
        }
    }
}
