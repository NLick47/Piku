package com.piku.client.source.poipiku.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuSheetTitle
import com.piku.client.core.ui.theme.PikuColors

/** poipiku 收藏同步档位：只在本地 / 收藏时同步到账号书签 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoipikuBookmarkSettingsSheet(
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
        scrollable = true,
    ) {
        PikuSheetTitle(text = stringResource(R.string.poipiku_bookmark_sync_title))
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.poipiku_bookmark_sync_hint),
            color = PikuColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(14.dp))
        OptionRow(
            text = stringResource(R.string.poipiku_bookmark_sync_auto),
            selected = selected,
            onClick = { onSelect(true) },
        )
        Spacer(Modifier.height(4.dp))
        OptionRow(
            text = stringResource(R.string.poipiku_bookmark_sync_local),
            selected = !selected,
            onClick = { onSelect(false) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = PikuColors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
