package com.piku.client.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.data.remote.GitHubRelease
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.themedSwitchColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSheet(
    currentVersion: String,
    autoCheckEnabled: Boolean,
    updateCheckState: UpdateCheckState,
    onToggleAutoCheck: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenFeedback: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    val primary = PikuColors.textPrimary
    val faint = PikuColors.textFaint
    val divider = PikuColors.border
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.menu_about),
                color = primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            AboutUpdateButton(
                state = updateCheckState,
                onCheckUpdate = onCheckUpdate,
                onOpenUpdate = onOpenUpdate,
                dark = dark,
            )
            Spacer(Modifier.height(4.dp))
            AboutToggleRow(
                text = stringResource(R.string.menu_auto_check_update),
                checked = autoCheckEnabled,
                onToggle = onToggleAutoCheck,
                dark = dark,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = divider)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.about_version, "v$currentVersion"),
                    color = faint,
                    fontSize = 12.sp,
                )
            }
            AboutLinkRow(
                text = stringResource(R.string.menu_github),
                onClick = onOpenGithub,
                dark = dark,
            )
            AboutLinkRow(
                text = stringResource(R.string.menu_feedback),
                onClick = onOpenFeedback,
                dark = dark,
            )
        }
    }
}

@Composable
private fun AboutUpdateButton(
    state: UpdateCheckState,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    dark: Boolean,
) {
    val green = if (dark) FollowDark else FollowLight
    val red = PikuColors.error
    val (targetBg, targetFg) = when (state) {
        UpdateCheckState.Checking -> AccentDark to Color.White
        UpdateCheckState.Latest -> green.copy(alpha = if (dark) 0.22f else 0.12f) to green
        UpdateCheckState.Failed -> red.copy(alpha = if (dark) 0.22f else 0.12f) to red
        else -> AccentDark to Color.White
    }
    val bg by animateColorAsState(targetBg, label = "updateBtnBg")
    val fg by animateColorAsState(targetFg, label = "updateBtnFg")
    val shape = RoundedCornerShape(16.dp)
    val clickable = state !is UpdateCheckState.Checking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(bg)
            .clickable(enabled = clickable) {
                when (state) {
                    UpdateCheckState.Checking -> Unit
                    is UpdateCheckState.Available -> onOpenUpdate()
                    else -> onCheckUpdate()
                }
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            UpdateCheckState.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            UpdateCheckState.Latest -> {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            else -> Unit
        }
        Text(
            text = when (val s = state) {
                UpdateCheckState.Checking -> stringResource(R.string.about_checking)
                UpdateCheckState.Latest -> stringResource(R.string.about_latest)
                is UpdateCheckState.Available -> stringResource(
                    R.string.about_update_download,
                    s.release.tagName,
                )
                UpdateCheckState.Failed -> stringResource(R.string.about_retry)
                UpdateCheckState.Idle -> stringResource(R.string.menu_check_update)
            },
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AboutToggleRow(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    dark: Boolean,
) {
    val primary = PikuColors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = themedSwitchColors(dark),
        )
    }
}

@Composable
private fun AboutLinkRow(
    text: String,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val faint = PikuColors.textFaint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = faint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = faint,
            modifier = Modifier.size(14.dp),
        )
    }
}
