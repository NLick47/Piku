package com.piku.client.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.domain.model.UserProfile
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.delay

/**
 * 应用内「编辑资料」底部弹层：修改昵称 + 更换头像，
 * 替代网页端 MyEditSettingPcV.jsp（不再自动跳转浏览器）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    profile: UserProfile?,
    dark: Boolean,
    onOpenPublicProfile: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable(profile?.name) { mutableStateOf(profile?.name ?: "") }
    val busy = state.savingName || state.uploadingAvatar

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.updateAvatar(uri)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            delay(300)
            onDismiss()
        }
    }
    LaunchedEffect(state.errorRes) {
        if (state.errorRes != null) {
            delay(4000)
            viewModel.consumeError()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.profile_edit_title),
                color = PikuColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.profile_edit_hint),
                color = PikuColors.textFaint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            // 头像区
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .border(
                            BorderStroke(
                                1.5.dp,
                                if (dark) Color(0x66FFFFFF) else AccentDark.copy(alpha = 0.5f),
                            ),
                            CircleShape,
                        )
                        .padding(4.dp),
                ) {
                    UserAvatar(
                        avatarUrl = profile?.avatarUrl,
                        onClick = {},
                        dark = dark,
                        size = 80.dp,
                    )
                }
                if (state.uploadingAvatar) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (dark) Color(0xCC2A2A2A) else Color(0xE6FFFFFF))
                            .border(
                                BorderStroke(1.dp, AccentDark.copy(alpha = 0.6f)),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AccentDark,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !busy,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = AccentDark,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.profile_edit_change_avatar),
                    color = AccentDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(12.dp))

            // 昵称区
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(16) },
                enabled = !busy,
                singleLine = true,
                label = {
                    Text(stringResource(R.string.profile_edit_name_label))
                },
                supportingText = {
                    Text(
                        text = stringResource(
                            R.string.profile_edit_name_char_count,
                            name.length,
                        ),
                        color = PikuColors.textFaint,
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = PikuColors.textPrimary,
                    fontSize = 14.sp,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            state.errorRes?.let { res ->
                Text(
                    text = stringResource(res),
                    color = PikuColors.error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(6.dp))

            val nameValid = name.trim().length in 3..16
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (nameValid && !busy) AccentDark else AccentDark.copy(alpha = 0.45f))
                    .clickable(enabled = !busy && nameValid) {
                        viewModel.updateNickName(name)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.savingName) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.profile_edit_save),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy, onClick = onOpenPublicProfile)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = PikuColors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.profile_edit_view_public),
                    color = PikuColors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
