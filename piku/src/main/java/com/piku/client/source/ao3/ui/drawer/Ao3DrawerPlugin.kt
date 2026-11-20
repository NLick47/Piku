package com.piku.client.source.ao3.ui.drawer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.core.model.WorkSource
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.ui.drawer.DrawerContribution
import com.piku.client.core.ui.drawer.DrawerEntry
import com.piku.client.core.ui.drawer.DrawerScope
import com.piku.client.core.ui.drawer.DrawerSlot
import com.piku.client.core.ui.drawer.SourceDrawerPlugin
import com.piku.client.core.ui.theme.LocalDarkTheme
import com.piku.client.source.ao3.ui.bookmarks.Ao3BookmarkSettingsSheet
import com.piku.client.source.ao3.ui.bookmarks.shortLabelRes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ao3DrawerPlugin @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : SourceDrawerPlugin {

    override val source = WorkSource.AO3

    /** 成人开关不参与 AO3 的任何过滤（站点对访客全开放），纯文字站也没有图片线路可调 */
    override val ownsAdultRow: Boolean = true

    override val ownsImageRouteRow: Boolean = true

    @Composable
    override fun contributions(scope: DrawerScope): List<DrawerContribution> {
        val mirror by settingsRepository.ao3BookmarkMirror.collectAsStateWithLifecycle()
        val dark = LocalDarkTheme.current

        val settings = if (scope.account?.loggedIn == true) {
            listOf(
                DrawerEntry(
                    icon = Icons.Outlined.BookmarkAdd,
                    label = stringResource(R.string.menu_ao3_bookmark_sync),
                    trailing = stringResource(mirror.shortLabelRes()),
                    onClick = {
                        scope.openOverlay(closeDrawer = false) { onDismiss, _ ->
                            Ao3BookmarkSettingsSheet(
                                selected = mirror,
                                onSelect = { mode ->
                                    settingsRepository.setAo3BookmarkMirror(mode)
                                    onDismiss()
                                },
                                onDismiss = onDismiss,
                                dark = dark,
                            )
                        }
                    },
                ),
            )
        } else {
            emptyList()
        }

        return buildList {
            add(
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
            if (settings.isNotEmpty()) add(DrawerContribution(DrawerSlot.SettingsExpanded, settings))
        }
    }
}
