package com.piku.client.source.ao3.ui.bookmarks

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
import com.piku.client.core.model.Ao3BookmarkMirror
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuSheetSubtitle
import com.piku.client.core.ui.common.PikuSheetTitle
import com.piku.client.core.ui.theme.PikuColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ao3BookmarkSettingsSheet(
    selected: Ao3BookmarkMirror,
    onSelect: (Ao3BookmarkMirror) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
        scrollable = true,
    ) {
        PikuSheetTitle(text = stringResource(R.string.ao3_bookmark_sync_title))
        Spacer(Modifier.height(6.dp))
        PikuSheetSubtitle(text = stringResource(R.string.ao3_bookmark_sync_hint))
        Spacer(Modifier.height(14.dp))
        Ao3BookmarkMirror.entries.forEach { mode ->
            OptionRow(
                text = stringResource(mode.labelRes()),
                selected = selected == mode,
                onClick = { onSelect(mode) },
            )
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

internal fun Ao3BookmarkMirror.labelRes(): Int = when (this) {
    Ao3BookmarkMirror.PUBLIC -> R.string.ao3_bookmark_sync_public
    Ao3BookmarkMirror.PRIVATE -> R.string.ao3_bookmark_sync_private
    Ao3BookmarkMirror.LOCAL_ONLY -> R.string.ao3_bookmark_sync_local
}

internal fun Ao3BookmarkMirror.shortLabelRes(): Int = when (this) {
    Ao3BookmarkMirror.PUBLIC -> R.string.ao3_bookmark_sync_short_public
    Ao3BookmarkMirror.PRIVATE -> R.string.ao3_bookmark_sync_short_private
    Ao3BookmarkMirror.LOCAL_ONLY -> R.string.ao3_bookmark_sync_short_local
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
