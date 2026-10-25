package com.piku.client.core.ui.novelcache

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuSheetSubtitle
import com.piku.client.core.ui.common.PikuSheetTitle
import com.piku.client.core.ui.theme.LocalDarkTheme
import com.piku.client.core.ui.theme.PikuColors

/**
 * 详情页的离线缓存按钮：紧挨「开始阅读」，不单独占一行。
 * 未缓存点一下开始缓存；已缓存点开底部面板看详情、更新、固定、删除。
 */
@Composable
internal fun NovelCacheActionChip(
    state: NovelCacheUiState,
    onAction: (NovelCacheAction) -> Unit,
    modifier: Modifier = Modifier,
    /** 紧凑模式：poipiku 的正文预览角上与「全屏阅读」并排，只留图标 */
    compact: Boolean = false,
) {
    if (state is NovelCacheUiState.Hidden) return
    var sheetOpen by remember { mutableStateOf(false) }
    val cached = state as? NovelCacheUiState.Cached
    val hasUpdate = cached != null && cached.updateCount > 0
    val label = chipLabel(state)
    val tint = when {
        state is NovelCacheUiState.Failed -> PikuColors.error
        cached == null || hasUpdate -> PikuColors.accent
        else -> PikuColors.textSecondary
    }
    val shape = RoundedCornerShape(if (compact) 14.dp else 22.dp)

    Box(modifier) {
        Row(
            modifier = Modifier
                .height(if (compact) 28.dp else 44.dp)
                .clip(shape)
                .background(PikuColors.surfaceSoft)
                .border(0.5.dp, PikuColors.border, shape)
                .clickable(enabled = state !is NovelCacheUiState.Caching) {
                    if (cached != null) sheetOpen = true else onAction(NovelCacheAction.Cache)
                }
                .padding(horizontal = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (state is NovelCacheUiState.Caching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = PikuColors.textSecondary,
                    strokeWidth = 1.5.dp,
                )
            } else {
                Icon(
                    imageVector = chipIcon(state),
                    contentDescription = if (compact) label else null,
                    tint = tint,
                    modifier = Modifier.size(15.dp),
                )
            }
            if (!compact) {
                Text(
                    text = label,
                    color = tint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (compact && hasUpdate) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(PikuColors.error),
            )
        }
    }

    if (sheetOpen && cached != null) {
        NovelCacheSheet(
            cached = cached,
            onDismiss = { sheetOpen = false },
            onAction = { action ->
                sheetOpen = false
                onAction(action)
            },
        )
    }
}

@Composable
private fun chipLabel(state: NovelCacheUiState): String = when (state) {
    NovelCacheUiState.Hidden -> ""
    NovelCacheUiState.NotCached -> stringResource(R.string.novel_cache_button_cache)
    NovelCacheUiState.Caching -> stringResource(R.string.novel_cache_button_caching)
    NovelCacheUiState.Failed -> stringResource(R.string.common_retry)
    is NovelCacheUiState.Cached -> if (state.updateCount > 0) {
        stringResource(R.string.novel_cache_button_update)
    } else {
        stringResource(R.string.novel_cache_button_cached)
    }
}

private fun chipIcon(state: NovelCacheUiState): ImageVector = when (state) {
    is NovelCacheUiState.Cached -> Icons.Outlined.DownloadDone
    is NovelCacheUiState.Failed -> Icons.Outlined.Refresh
    else -> Icons.Outlined.Download
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NovelCacheSheet(
    cached: NovelCacheUiState.Cached,
    onDismiss: () -> Unit,
    onAction: (NovelCacheAction) -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val size = novelCacheSize(cached.byteSize)
    val time = novelCacheTime(now, cached.fetchedAt)
    val status = if (cached.chapterCount > 1) {
        stringResource(R.string.novel_cache_sheet_status_chapters, cached.chapterCount, size, time)
    } else {
        stringResource(R.string.novel_cache_sheet_status_single, size, time)
    }

    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = LocalDarkTheme.current,
    ) {
        PikuSheetTitle(stringResource(R.string.novel_cache_title))
        Spacer(Modifier.height(4.dp))
        PikuSheetSubtitle(status)
        if (cached.updateCount > 0) {
            Text(
                text = stringResource(R.string.novel_cache_sheet_updates, cached.updateCount),
                color = PikuColors.accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = PikuColors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
        SheetAction(
            icon = Icons.Outlined.Download,
            label = stringResource(R.string.novel_cache_menu_update),
            tint = PikuColors.accent,
            onClick = { onAction(NovelCacheAction.Update) },
        )
        SheetAction(
            icon = Icons.Outlined.DeleteOutline,
            label = stringResource(R.string.novel_cache_menu_delete),
            tint = PikuColors.error,
            onClick = { onAction(NovelCacheAction.Delete) },
        )
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = PikuColors.textPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}
