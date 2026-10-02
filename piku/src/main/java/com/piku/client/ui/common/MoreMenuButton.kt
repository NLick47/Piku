package com.piku.client.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.piku.client.R
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors

data class MoreMenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun MoreMenuButton(
    groups: List<List<MoreMenuAction>>,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier.onSizeChanged { anchorHeightPx = it.height },
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.detail_more),
                tint = PikuColors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            MenuPopup(
                groups = groups.map { group ->
                    group.map { action ->
                        MenuPopupItem(
                            label = action.label,
                            icon = action.icon,
                            enabled = action.enabled,
                            onClick = {
                                expanded = false
                                action.onClick()
                            },
                        )
                    }
                },
                dark = LocalDarkTheme.current,
                anchorHeightPx = anchorHeightPx,
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
fun quietFollowMenuActions(
    followed: Boolean,
    followQuiet: Boolean,
    followSending: Boolean,
    onQuietFollow: () -> Unit,
    onMakePublic: () -> Unit,
    onUnfollow: () -> Unit,
): List<MoreMenuAction> = buildList {
    when {
        !followed -> add(
            MoreMenuAction(
                label = stringResource(R.string.detail_follow_quiet),
                icon = Icons.Outlined.VisibilityOff,
                enabled = !followSending,
                onClick = onQuietFollow,
            ),
        )
        followQuiet -> add(
            MoreMenuAction(
                label = stringResource(R.string.detail_follow_to_public),
                icon = Icons.Outlined.Visibility,
                enabled = !followSending,
                onClick = onMakePublic,
            ),
        )
        else -> add(
            MoreMenuAction(
                label = stringResource(R.string.detail_follow_to_quiet),
                icon = Icons.Outlined.VisibilityOff,
                enabled = !followSending,
                onClick = onQuietFollow,
            ),
        )
    }
    if (followed) {
        add(
            MoreMenuAction(
                label = stringResource(R.string.follow_user_unfollow),
                icon = Icons.Outlined.PersonRemove,
                enabled = !followSending,
                onClick = onUnfollow,
            ),
        )
    }
}
