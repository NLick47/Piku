package com.piku.client.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.data.local.BlockedUser
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.SwitchUncheckedTrackDark
import com.piku.client.ui.theme.SwitchUncheckedTrackLight

/**
 * 首页内容屏蔽面板：屏蔽标签（输入关键词）+ 屏蔽用户（详情页一键屏蔽后在此管理）。
 * 视觉与 WebDAV 设置面板同一模式：全屏 Dialog + 首页渐变背景 + 顶栏返回。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockedContentScreen(
    tags: List<String>,
    users: List<BlockedUser>,
    onAddTag: (String) -> Boolean,
    onRemoveTag: (String) -> Unit,
    onUnblockUser: (Long) -> Unit,
    onBack: () -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val primary = PikuColors.textPrimary
    val secondary = PikuColors.textSecondary
    val faint = PikuColors.textFaint
    var input by rememberSaveable { mutableStateOf("") }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager: FocusManager = LocalFocusManager.current
    BackHandler(enabled = keyboard?.let { true } ?: true) {
        if (keyboard != null) {
            keyboard.hide()
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = primary,
                    )
                }
                Text(
                    text = stringResource(R.string.menu_blocked_content),
                    color = primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.blocked_content_hint),
                    color = faint,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(20.dp))

                SheetSectionLabel(text = stringResource(R.string.blocked_tags_title), color = secondary)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.blocked_tags_add_hint),
                                color = faint,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = primary),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentDark.copy(alpha = 0.8f),
                            unfocusedBorderColor = PikuColors.border,
                            focusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
                            unfocusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
                            cursorColor = AccentDark,
                            focusedTextColor = primary,
                            unfocusedTextColor = primary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    val addEnabled = input.trim().removePrefix("#").trim().isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (addEnabled) AccentDark
                                else if (dark) SwitchUncheckedTrackDark else SwitchUncheckedTrackLight,
                            )
                            .clickable(enabled = addEnabled, onClick = {
                                if (onAddTag(input)) input = ""
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.my_tags_add),
                            tint = if (addEnabled) Color.White else faint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.blocked_tags_empty),
                        color = faint,
                        fontSize = 12.sp,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            BlockedChip(
                                label = tag,
                                onRemove = { onRemoveTag(tag) },
                                dark = dark,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                SheetSectionLabel(text = stringResource(R.string.blocked_users_title), color = secondary)
                Spacer(Modifier.height(8.dp))
                if (users.isEmpty()) {
                    Text(
                        text = stringResource(R.string.blocked_users_empty),
                        color = faint,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.blocked_users_hint),
                        color = faint,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        users.forEach { user ->
                            BlockedChip(
                                label = user.name.ifBlank { user.id.toString() },
                                onRemove = { onUnblockUser(user.id) },
                                dark = dark,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedChip(
    label: String,
    onRemove: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val chipBg = if (dark) GlassCardBgDark else Color.White
    val faint = PikuColors.textFaint
    Row(
        modifier = Modifier
            .clip(shape)
            .background(chipBg)
            .border(BorderStroke(0.5.dp, PikuColors.border), shape)
            .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.my_tags_delete),
                tint = faint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
