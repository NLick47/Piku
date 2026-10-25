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

    /** 成人开关不参与 AO3 的任何过滤（站点对访客全开放），纯文字站也没有图片线路可调 */
    override val ownsAdultRow: Boolean = true

    override val ownsImageRouteRow: Boolean = true

    /** 登录后 AO3 没有投稿/收藏这类站内动作可做；账号头由登录插件自己撑起来，这里只加圈层入口 */
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
