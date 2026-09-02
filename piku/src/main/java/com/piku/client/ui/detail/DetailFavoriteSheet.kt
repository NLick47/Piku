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
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.AccentSolid
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginBackgroundLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.StarDark
import com.piku.client.ui.theme.StarLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoriteSheet(
    folders: List<FavoriteFolder>,
    selectedFolderIds: Set<Long>,
    dark: Boolean,
    onToggleFolder: (Long) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var creatingNew by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else LoginBackgroundLight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.detail_favorite_sheet_title),
                color = PikuColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.detail_favorite_sheet_hint),
                color = PikuColors.textSecondary,
                fontSize = 12.sp,
            )
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
            .shadow(if (selected && dark) 4.dp else 0.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
            .clip(shape)
            .background(
                when {
                    selected && dark -> Color(0x26E0E0E0)
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
            .background(if (dark) Color(0x22FFFFFF) else Color(0x142C2C2C))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
