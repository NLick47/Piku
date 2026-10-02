package com.piku.client.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.ui.common.PikuBottomSheet
import com.piku.client.ui.common.PikuSheetSubtitle
import com.piku.client.ui.common.PikuSheetTitle
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.AccentSolid
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginBackgroundLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.StarDark
import com.piku.client.ui.theme.StarLight
import com.piku.client.ui.theme.ShadowAmbient
import com.piku.client.ui.theme.ShadowSpot

/** 「默认」徽标底：玻璃上的弱化选中色 */
internal val DefaultBadgeBgDark = Color(0x22FFFFFF)
internal val DefaultBadgeBgLight = Color(0x142C2C2C)

/**
 * 收藏面板的 pixiv 镜像区块：动作项写的是「会发生什么」，用户不需要先理解档位概念。
 * null = 该作品没有 pixiv 云端可操作（非 pixiv 源/未登录/小说），整个区块不出现。
 */
internal data class PixivMirrorActions(
    val favorited: Boolean,
    val cloudSynced: Boolean,
    /** 云端请求在途：动作行禁用防连点，状态行尾转圈——网慢也有「正在做」的观感 */
    val pending: Boolean,
    /** 未收藏时的两个入口：同步收藏到 pixiv / 仅收藏到本 App */
    val onSyncFavorite: () -> Unit,
    val onLocalFavorite: () -> Unit,
    /** 已收藏未同步：补同步到 pixiv */
    val onResync: () -> Unit,
    /** 已同步：取消同步，保留本地 */
    val onUnsync: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoriteSheet(
    folders: List<FavoriteFolder>,
    selectedFolderIds: Set<Long>,
    dark: Boolean,
    onToggleFolder: (Long) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
    pixivMirror: PixivMirrorActions? = null,
) {
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var creatingNew by rememberSaveable { mutableStateOf(false) }

    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
        scrollable = true,
    ) {
            PikuSheetTitle(text = stringResource(R.string.detail_favorite_sheet_title))
            Spacer(Modifier.height(6.dp))
            PikuSheetSubtitle(text = stringResource(R.string.detail_favorite_sheet_hint))
            if (pixivMirror != null) {
                Spacer(Modifier.height(14.dp))
                PikuSheetSubtitle(text = stringResource(R.string.detail_pixiv_mirror_section))
                Spacer(Modifier.height(6.dp))
                if (!pixivMirror.favorited) {
                    PixivMirrorRow(
                        icon = Icons.Outlined.CloudUpload,
                        label = stringResource(R.string.detail_sync_favorite_pixiv),
                        dark = dark,
                        enabled = !pixivMirror.pending,
                        onClick = pixivMirror.onSyncFavorite,
                    )
                    Spacer(Modifier.height(4.dp))
                    PixivMirrorRow(
                        icon = Icons.Outlined.Smartphone,
                        label = stringResource(R.string.detail_favorite_app_only),
                        dark = dark,
                        enabled = !pixivMirror.pending,
                        onClick = pixivMirror.onLocalFavorite,
                    )
                } else {
                    PixivMirrorRow(
                        icon = if (pixivMirror.cloudSynced) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                        label = stringResource(
                            if (pixivMirror.cloudSynced) {
                                R.string.detail_mirror_state_synced
                            } else {
                                R.string.detail_mirror_state_local
                            },
                        ),
                        dark = dark,
                        onClick = {},
                        enabled = false,
                        pending = pixivMirror.pending,
                    )
                    Spacer(Modifier.height(4.dp))
                    PixivMirrorRow(
                        icon = if (pixivMirror.cloudSynced) Icons.Outlined.CloudOff else Icons.Outlined.CloudUpload,
                        label = stringResource(
                            if (pixivMirror.cloudSynced) {
                                R.string.detail_unsync_keep_local
                            } else {
                                R.string.detail_resync_pixiv
                            },
                        ),
                        dark = dark,
                        enabled = !pixivMirror.pending,
                        onClick = if (pixivMirror.cloudSynced) pixivMirror.onUnsync else pixivMirror.onResync,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            folders.forEach { folder ->
                FavoriteFolderRow(
                    folder = folder,
                    selected = folder.id in selectedFolderIds,
                    onClick = { onToggleFolder(folder.id) },
                    dark = dark,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (creatingNew) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.detail_favorite_new_hint),
                            fontSize = 13.sp,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PikuColors.surfaceMuted,
                        unfocusedContainerColor = PikuColors.surfaceMuted,
                        focusedBorderColor = PikuColors.border,
                        unfocusedBorderColor = PikuColors.border,
                        cursorColor = PikuColors.controlAccent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                onCreateFolder(newFolderName.trim())
                                newFolderName = ""
                                creatingNew = false
                            }
                        },
                        enabled = newFolderName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (dark) LoginTextPrimaryDark else AccentSolid,
                            contentColor = if (dark) LoginBackgroundDark else Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.detail_favorite_create), fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = {
                            newFolderName = ""
                            creatingNew = false
                        },
                    ) {
                        Text(stringResource(R.string.detail_favorite_cancel), fontSize = 13.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { creatingNew = true }) {
                    Text(
                        text = stringResource(R.string.detail_favorite_new_folder),
                        color = if (dark) LoginTextSecondaryDark else LoginTextPrimaryLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
    }
}

/** pixiv 镜像区块的一行：动作项整行可点；状态行 [enabled] 为假只展示同步态，[pending] 时行尾转圈 */
@Composable
private fun PixivMirrorRow(
    icon: ImageVector,
    label: String,
    dark: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    pending: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (dark) ControlAccentDark.copy(alpha = 0.08f) else AccentDark.copy(alpha = 0.06f),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) PikuColors.controlAccent else PikuColors.textFaint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (enabled) PikuColors.textPrimary else PikuColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                color = PikuColors.controlAccent,
                strokeWidth = 1.5.dp,
            )
        }
    }
}

@Composable
private fun FavoriteFolderRow(
    folder: FavoriteFolder,
    selected: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                if (selected && dark) 4.dp else 0.dp,
                shape,
                ambientColor = ShadowAmbient,
                spotColor = ShadowSpot,
            )
            .clip(shape)
            .background(
                when {
                    selected && dark -> ControlAccentDark.copy(alpha = 0.15f)
                    selected -> AccentDark.copy(alpha = 0.12f)
                    else -> Color.Transparent
                },
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) {
                        PikuColors.controlAccent
                    } else {
                        Color.Transparent
                    },
                ),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = if (selected) {
                if (dark) StarDark else StarLight
            } else {
                PikuColors.textSecondary
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = folder.name,
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (folder.isDefault) {
            Spacer(Modifier.width(6.dp))
            DefaultFolderBadge(dark = dark)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = folder.workCount.toString(),
            color = PikuColors.textSecondary,
            fontSize = 12.sp,
        )
    }
}

/** 「默认」小徽标：标识快速收藏的落点收藏夹。 */
@Composable
internal fun DefaultFolderBadge(dark: Boolean) {
    Text(
        text = stringResource(R.string.collection_default_badge),
        color = PikuColors.textSecondary,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (dark) DefaultBadgeBgDark else DefaultBadgeBgLight)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
