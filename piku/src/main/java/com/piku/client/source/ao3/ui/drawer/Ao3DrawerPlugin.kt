package com.piku.client.source.ao3.ui.drawer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.piku.client.R
import com.piku.client.core.model.WorkSource
import com.piku.client.core.ui.drawer.DrawerContribution
import com.piku.client.core.ui.drawer.DrawerEntry
import com.piku.client.core.ui.drawer.DrawerScope
import com.piku.client.core.ui.drawer.DrawerSlot
import com.piku.client.core.ui.drawer.SourceDrawerPlugin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ao3DrawerPlugin @Inject constructor() : SourceDrawerPlugin {

    override val source = WorkSource.AO3

    /** AO3 不过登录：账号区空着，只给一条圈层入口（它独有的资料库概念） */
    @Composable
    override fun contributions(scope: DrawerScope): List<DrawerContribution> = listOf(
        DrawerContribution(
            slot = DrawerSlot.Library,
            entries = listOf(
                DrawerEntry(
                    icon = Icons.Outlined.Interests,
                    label = stringResource(R.string.ao3_scope_title),
                    onClick = { scope.openScopes(source) },
                ),
            ),
        ),
    )
}
