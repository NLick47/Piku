package com.piku.client.ui.home.drawer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.piku.client.R
import com.piku.client.data.source.PixivFollowsSource
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.ui.follow.FollowUsersScreen
import com.piku.client.ui.follow.PixivFollowUsersViewModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivDrawerPlugin @Inject constructor(
    private val follows: PixivFollowsSource,
) : SourceDrawerPlugin {

    override val source = WorkSource.PIXIV

    override val ownsAdultRow: Boolean = true

    override val ownsBookmarkMirrorRow: Boolean = true

    @Composable
    override fun contributions(scope: DrawerScope): List<DrawerContribution> {
        if (scope.account?.loggedIn != true) return emptyList()

        return listOf(
            DrawerContribution(
                slot = DrawerSlot.Library,
                entries = listOf(
                    DrawerEntry(
                        icon = Icons.Outlined.Group,
                        label = stringResource(R.string.menu_follow_users),
                        onClick = {
                            scope.openOverlay { onDismiss, _ ->
                                FullscreenDrawerOverlay(onDismiss = onDismiss) {
                                    FollowUsersScreen(
                                        viewModel = hiltViewModel<PixivFollowUsersViewModel>(),
                                        onBack = onDismiss,
                                        onLoginClick = scope::openLogin,
                                        onUserClick = { user ->
                                            // 去向由插件自己声明；形态由外壳按源挑页面
                                            when (val open = follows.userPage(user)) {
                                                is SourceAuthorOpen.External -> scope.openExternal(open.url)
                                                SourceAuthorOpen.NativeDetail, SourceAuthorOpen.NativeProfile ->
                                                    scope.openAuthorProfile(source, user.userId, user.name)
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    ),
                ),
            ),
        )
    }
}
