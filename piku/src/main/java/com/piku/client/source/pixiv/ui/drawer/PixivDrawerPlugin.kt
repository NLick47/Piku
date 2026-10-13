package com.piku.client.source.pixiv.ui.drawer
import com.piku.client.core.ui.drawer.DrawerContribution
import com.piku.client.core.ui.drawer.DrawerEntry
import com.piku.client.core.ui.drawer.DrawerScope
import com.piku.client.core.ui.drawer.DrawerSlot
import com.piku.client.core.ui.drawer.SourceDrawerPlugin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.piku.client.R
import com.piku.client.core.model.WorkSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivDrawerPlugin @Inject constructor() : SourceDrawerPlugin {

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
                        onClick = { scope.openFollowUsers(source) },
                    ),
                ),
            ),
        )
    }
}
