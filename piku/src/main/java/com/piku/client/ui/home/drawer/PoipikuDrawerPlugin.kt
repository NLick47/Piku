package com.piku.client.ui.home.drawer

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.data.repository.ProfileRepository
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.UserProfile
import com.piku.client.ui.follow.BlockUsersScreen
import com.piku.client.ui.follow.FollowUsersScreen
import com.piku.client.ui.follow.PoipikuFollowUsersViewModel
import com.piku.client.ui.profile.ProfileEditSheet
import com.piku.client.ui.publish.PublishScreen
import com.piku.client.ui.theme.LocalDarkTheme
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoipikuDrawerPlugin @Inject constructor(
    private val profileRepository: ProfileRepository,
) : SourceDrawerPlugin {

    override val source = WorkSource.POIPIKU

    @Composable
    override fun contributions(scope: DrawerScope): List<DrawerContribution> {
        val loggedIn = scope.account?.loggedIn == true
        val profile by profileRepository.userProfile.collectAsStateWithLifecycle()

        val account = buildList {
            if (loggedIn) {
                add(
                    DrawerEntry(
                        icon = Icons.Outlined.PostAdd,
                        label = stringResource(R.string.menu_publish),
                        onClick = {
                            scope.openOverlay { onDismiss, onClose ->
                                PoipikuPublishOverlay(
                                    profile = profile,
                                    onWorkOpen = scope::openWork,
                                    onDismiss = onDismiss,
                                    onClose = onClose,
                                )
                            }
                        },
                    ),
                )
            }
            if (profile?.profileUrl != null) {
                add(
                    DrawerEntry(
                        icon = Icons.Outlined.Person,
                        label = stringResource(R.string.menu_edit_profile),
                        onClick = {
                            // 资料编辑是盖在抽屉上的 sheet：不收抽屉，关掉原地返回
                            scope.openOverlay(closeDrawer = false) { onDismiss, _ ->
                                val context = LocalContext.current
                                ProfileEditSheet(
                                    profile = profile,
                                    dark = LocalDarkTheme.current,
                                    onOpenPublicProfile = {
                                        val url = profile?.profileUrl
                                        if (url != null) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                    onDismiss = onDismiss,
                                )
                            }
                        },
                    ),
                )
            }
        }

        val library = if (loggedIn) {
            listOf(
                DrawerEntry(
                    icon = Icons.Outlined.Group,
                    label = stringResource(R.string.menu_follow_users),
                    onClick = {
                        scope.openOverlay { onDismiss, _ ->
                            FullscreenDrawerOverlay(onDismiss = onDismiss) {
                                FollowUsersScreen(
                                    viewModel = hiltViewModel<PoipikuFollowUsersViewModel>(),
                                    onBack = onDismiss,
                                    onLoginClick = scope::openLogin,
                                    onUserClick = { user -> scope.openAuthorProfile(user.userId, user.name) },
                                )
                            }
                        }
                    },
                ),
                DrawerEntry(
                    icon = Icons.Outlined.Block,
                    label = stringResource(R.string.menu_block_users),
                    onClick = {
                        scope.openOverlay { onDismiss, _ ->
                            FullscreenDrawerOverlay(onDismiss = onDismiss) {
                                BlockUsersScreen(
                                    onBack = onDismiss,
                                    onLoginClick = scope::openLogin,
                                    onUserClick = { user -> scope.openAuthorProfile(user.userId, user.name) },
                                )
                            }
                        }
                    },
                ),
            )
        } else {
            emptyList()
        }

        return buildList {
            if (account.isNotEmpty()) add(DrawerContribution(DrawerSlot.Account, account))
            if (library.isNotEmpty()) add(DrawerContribution(DrawerSlot.Library, library))
        }
    }
}

/**
 * 发布完成跳作品详情要用的最小 Work 投影：详情页会自己按 id 回源拉全量数据。
 * 拿不到 uid 时给 null（与旧行为一致：不跳详情，留在首页）。
 */
private fun buildPublishedWork(workId: Long, profile: UserProfile?): Work? {
    val uid = profile?.uid?.toLongOrNull() ?: return null
    return Work(
        id = workId,
        authorId = uid,
        authorName = profile.name.orEmpty(),
        authorAvatarUrl = null,
        categoryCd = 0,
        categoryName = "",
        title = "",
        thumbnailUrl = "",
        imageCount = 0,
        r18 = false,
    )
}

@Composable
private fun PoipikuPublishOverlay(
    profile: UserProfile?,
    onWorkOpen: (Work) -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        PublishScreen(
            initialDraftId = -1L,
            onBack = onDismiss,
            onPublished = { workId ->
                // 只关浮层不回抽屉：紧接着跳进发布的作品详情（拿不到 uid 就留在首页）
                onClose()
                buildPublishedWork(workId, profile)?.let(onWorkOpen)
            },
        )
    }
}
