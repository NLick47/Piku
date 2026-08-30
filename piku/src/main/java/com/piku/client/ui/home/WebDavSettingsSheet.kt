package com.piku.client.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.data.repository.SyncResult
import com.piku.client.data.repository.SyncState
import com.piku.client.data.repository.TestConnectionState
import com.piku.client.ui.common.GlassCard
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.ErrorRedDark
import com.piku.client.ui.theme.ErrorRedLight
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.StatusSyncing
import com.piku.client.ui.theme.themedSwitchColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ──────────────────────── 尺寸常量 ────────────────────────

private val PagePaddingH = 20.dp
private val CardCorner = 20.dp
private val FieldCorner = 14.dp
private val ActionHeight = 50.dp

/** 动作按钮的三态结果（无 / 成功 / 失败） */
private enum class ActionResult { NONE, SUCCESS, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSettingsScreen(
    url: String,
    username: String,
    password: String,
    enabled: Boolean,
    lastSyncAt: Long,
    syncResult: SyncResult?,
    syncState: SyncState,
    testConnectionState: TestConnectionState,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onClearTestResult: () -> Unit,
    onSyncNow: () -> Unit,
    onBack: () -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    BackHandler { onBack() }

    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val secondary = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    val accent = if (dark) ControlAccentDark else AccentDark
    val border = if (dark) LoginCardBorderDark else LoginCardBorderLight

    var passwordVisible by remember { mutableStateOf(false) }

    val urlInvalid = url.isNotBlank() &&
        !url.startsWith("http://", ignoreCase = true) &&
        !url.startsWith("https://", ignoreCase = true)
    val configValid = url.isNotBlank() && username.isNotBlank() && !urlInvalid

    val isSyncing = syncState == SyncState.SYNCING
    val isTesting = testConnectionState == TestConnectionState.TESTING
    val actionsEnabled = enabled && configValid && !isSyncing && !isTesting

    // 测试成功后短暂停留再复位，便于用户看清结果
    LaunchedEffect(testConnectionState) {
        if (testConnectionState == TestConnectionState.SUCCESS) {
            delay(2200L)
            onClearTestResult()
        }
    }
    // 修改任一配置项时清掉上一次的测试结果
    LaunchedEffect(url, username, password) {
        if (testConnectionState == TestConnectionState.SUCCESS ||
            testConnectionState == TestConnectionState.FAILED
        ) {
            onClearTestResult()
        }
    }

    Box(
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
            SettingsTopBar(title = stringResource(R.string.webdav_settings_title), primary = primary, onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = PagePaddingH)) {
                Spacer(Modifier.height(2.dp))

                SyncStatusStrip(
                    syncState = syncState,
                    syncResult = syncResult,
                    lastSyncAt = lastSyncAt,
                    dark = dark,
                    primary = primary,
                    secondary = secondary,
                )

                Spacer(Modifier.height(20.dp))

                EnableCard(
                    enabled = enabled,
                    dark = dark,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    onCheckedChange = onEnabledChange,
                )

                Spacer(Modifier.height(22.dp))
                SheetSectionLabel(text = stringResource(R.string.webdav_server_config), color = secondary)
                Spacer(Modifier.height(8.dp))

                ServerConfigCard(
                    url = url,
                    username = username,
                    password = password,
                    passwordVisible = passwordVisible,
                    urlInvalid = urlInvalid,
                    dark = dark,
                    primary = primary,
                    secondary = secondary,
                    faint = faint,
                    accent = accent,
                    border = border,
                    onPasswordVisibleChange = { passwordVisible = it },
                    onUrlChange = onUrlChange,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionButton(
                        onClick = onTestConnection,
                        enabled = actionsEnabled,
                        running = isTesting,
                        result = when (testConnectionState) {
                            TestConnectionState.SUCCESS -> ActionResult.SUCCESS
                            TestConnectionState.FAILED -> ActionResult.FAILED
                            else -> ActionResult.NONE
                        },
                        filled = false,
                        dark = dark,
                        primary = primary,
                        accent = accent,
                        icon = Icons.Outlined.Link,
                        idleLabel = stringResource(R.string.webdav_test_connection),
                        loadingLabel = stringResource(R.string.webdav_testing),
                        successLabel = stringResource(R.string.webdav_connection_success),
                        errorLabel = stringResource(R.string.webdav_connection_failed),
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton(
                        onClick = onSyncNow,
                        enabled = actionsEnabled,
                        running = isSyncing,
                        result = when (syncState) {
                            SyncState.SUCCESS -> ActionResult.SUCCESS
                            SyncState.FAILED -> ActionResult.FAILED
                            else -> ActionResult.NONE
                        },
                        filled = true,
                        dark = dark,
                        primary = primary,
                        accent = accent,
                        icon = Icons.Outlined.Sync,
                        idleLabel = stringResource(R.string.webdav_sync_now),
                        loadingLabel = stringResource(R.string.webdav_syncing),
                        successLabel = stringResource(R.string.webdav_sync_success),
                        errorLabel = stringResource(R.string.webdav_sync_failed),
                        modifier = Modifier.weight(1f),
                    )
                }

                // 同步未开启时说明按钮为何不可点
                AnimatedVisibility(
                    visible = !enabled,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
                ) {
                    Text(
                        text = stringResource(R.string.webdav_disabled_action_hint),
                        color = faint,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 2.dp),
                    )
                }

                TestResultBanner(
                    testConnectionState = testConnectionState,
                    dark = dark,
                    primary = primary,
                    secondary = secondary,
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = faint,
                        modifier = Modifier
                            .size(15.dp)
                            .offset(y = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.webdav_help),
                        color = secondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// ──────────────────────── 顶栏 ────────────────────────

@Composable
private fun SettingsTopBar(
    title: String,
    primary: Color,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 4.dp)
            .height(52.dp),
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
            text = title,
            color = primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ──────────────────────── 同步状态条 ────────────────────────

/**
 * 紧凑状态条：直接落在页面底色上，不再额外套一层卡片，
 * 避免亮色模式下出现一整块比页面更白的底板。
 */
@Composable
private fun SyncStatusStrip(
    syncState: SyncState,
    syncResult: SyncResult?,
    lastSyncAt: Long,
    dark: Boolean,
    primary: Color,
    secondary: Color,
) {
    val tone = when (syncState) {
        SyncState.SYNCING -> StatusSyncing
        SyncState.SUCCESS -> if (dark) FollowDark else FollowLight
        SyncState.FAILED -> if (dark) ErrorRedDark else ErrorRedLight
        SyncState.IDLE -> secondary
    }

    val statusText = when (syncState) {
        SyncState.SYNCING -> stringResource(R.string.webdav_syncing)
        SyncState.SUCCESS -> stringResource(R.string.webdav_sync_success)
        SyncState.FAILED -> stringResource(R.string.webdav_sync_failed)
        SyncState.IDLE -> if (lastSyncAt > 0) {
            stringResource(R.string.webdav_status_waiting)
        } else {
            stringResource(R.string.webdav_status_idle)
        }
    }

    val lastSyncText = if (lastSyncAt > 0) {
        val date = remember(lastSyncAt) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSyncAt))
        }
        stringResource(R.string.webdav_last_sync, date)
    } else {
        stringResource(R.string.webdav_never_synced)
    }

    val errorMessage = if (syncState == SyncState.FAILED) {
        syncResult?.error?.takeIf { it.isNotBlank() }
    } else {
        null
    }

    val stats = remember(syncState, syncResult) {
        val r = syncResult ?: return@remember emptyList()
        if (syncState != SyncState.SUCCESS) return@remember emptyList()
        buildList {
            if (r.newFolders > 0) add(StatItem(R.string.webdav_stat_folders, r.newFolders))
            if (r.newWorks > 0) add(StatItem(R.string.webdav_stat_works, r.newWorks))
            if (r.backedUpWorks > 0) add(StatItem(R.string.webdav_stat_backup, r.backedUpWorks))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (syncState == SyncState.SYNCING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = tone,
                    strokeWidth = 1.8.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tone),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = statusText,
                color = primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = lastSyncText,
                color = secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = tone,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 17.dp, top = 5.dp),
            )
        }

        AnimatedVisibility(
            visible = stats.isNotEmpty(),
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 17.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                stats.forEach { item ->
                    StatChip(
                        text = stringResource(item.labelRes, item.count),
                        dark = dark,
                        textColor = primary,
                    )
                }
            }
        }
    }
}

private data class StatItem(
    val labelRes: Int,
    val count: Int,
)

@Composable
private fun StatChip(
    text: String,
    dark: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (dark) Color(0x14FFFFFF) else Color(0x0A000000))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ──────────────────────── 启用开关 ────────────────────────

@Composable
private fun EnableCard(
    enabled: Boolean,
    dark: Boolean,
    primary: Color,
    secondary: Color,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val iconBg by animateColorAsState(
        targetValue = if (enabled) {
            accent.copy(alpha = if (dark) 0.22f else 0.13f)
        } else {
            if (dark) Color(0x12FFFFFF) else Color(0x08000000)
        },
        label = "enableIconBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (enabled) accent else secondary,
        label = "enableIconTint",
    )

    GlassCard(dark = dark, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudSync,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.webdav_enable),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.webdav_enable_hint),
                    color = secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
                colors = themedSwitchColors(dark),
            )
        }
    }
}

// ──────────────────────── 服务器配置 ────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerConfigCard(
    url: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    urlInvalid: Boolean,
    dark: Boolean,
    primary: Color,
    secondary: Color,
    faint: Color,
    accent: Color,
    border: Color,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
) {
    val errorColor = if (dark) ErrorRedDark else ErrorRedLight
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = border,
        disabledBorderColor = border,
        errorBorderColor = errorColor,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        cursorColor = accent,
        errorCursorColor = errorColor,
        focusedTextColor = primary,
        unfocusedTextColor = primary,
        disabledTextColor = faint,
        errorTextColor = primary,
        focusedLabelColor = accent,
        unfocusedLabelColor = secondary,
        disabledLabelColor = faint,
        errorLabelColor = errorColor,
        focusedPlaceholderColor = faint,
        unfocusedPlaceholderColor = faint,
        errorPlaceholderColor = faint,
        focusedLeadingIconColor = accent,
        unfocusedLeadingIconColor = secondary,
        errorLeadingIconColor = errorColor,
        focusedTrailingIconColor = accent,
        unfocusedTrailingIconColor = secondary,
        errorTrailingIconColor = errorColor,
        focusedSupportingTextColor = errorColor,
        unfocusedSupportingTextColor = secondary,
        errorSupportingTextColor = errorColor,
    )
    val fieldTextStyle = TextStyle(fontSize = 14.sp, color = primary)

    GlassCard(dark = dark, shape = RoundedCornerShape(CardCorner), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.webdav_url), fontSize = 13.sp) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.webdav_url_placeholder),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingText = if (urlInvalid) {
                    { Text(stringResource(R.string.webdav_url_invalid), fontSize = 11.sp) }
                } else {
                    null
                },
                isError = urlInvalid,
                singleLine = true,
                textStyle = fieldTextStyle,
                shape = RoundedCornerShape(FieldCorner),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.webdav_username), fontSize = 13.sp) },
                singleLine = true,
                textStyle = fieldTextStyle,
                shape = RoundedCornerShape(FieldCorner),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.webdav_password), fontSize = 13.sp) },
                trailingIcon = {
                    IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.webdav_password_hide
                                else R.string.webdav_password_show,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                textStyle = fieldTextStyle,
                shape = RoundedCornerShape(FieldCorner),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ──────────────────────── 操作按钮 ────────────────────────

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    running: Boolean,
    result: ActionResult,
    filled: Boolean,
    dark: Boolean,
    primary: Color,
    accent: Color,
    icon: ImageVector,
    idleLabel: String,
    loadingLabel: String,
    successLabel: String,
    errorLabel: String,
    modifier: Modifier = Modifier,
) {
    val onAccent = if (dark) Color(0xFF1A1A1A) else Color.White
    val successColor = if (dark) FollowDark else FollowLight
    val errorColor = if (dark) ErrorRedDark else ErrorRedLight

    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "actionScale",
    )

    val resultColor = when (result) {
        ActionResult.SUCCESS -> successColor
        ActionResult.FAILED -> errorColor
        ActionResult.NONE -> accent
    }
    val bg by animateColorAsState(
        targetValue = when {
            !filled -> Color.Transparent
            running -> accent.copy(alpha = 0.82f)
            result != ActionResult.NONE -> resultColor
            else -> accent
        },
        label = "actionBg",
    )
    val fg by animateColorAsState(
        targetValue = when {
            filled -> onAccent
            running -> accent
            result != ActionResult.NONE -> resultColor
            else -> primary
        },
        label = "actionFg",
    )
    val stroke by animateColorAsState(
        targetValue = when {
            filled -> Color.Transparent
            running || result != ActionResult.NONE -> Color.Transparent
            else -> accent.copy(alpha = if (dark) 0.34f else 0.28f)
        },
        label = "actionStroke",
    )

    Box(
        modifier = modifier
            .height(ActionHeight)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, stroke), shape)
            .clickable(
                enabled = enabled && !running,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val contentAlpha = if (enabled) 1f else 0.42f
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = fg,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = when (result) {
                        ActionResult.SUCCESS -> Icons.Outlined.CheckCircle
                        ActionResult.FAILED -> Icons.Outlined.Error
                        ActionResult.NONE -> icon
                    },
                    contentDescription = null,
                    tint = fg.copy(alpha = contentAlpha),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = when {
                    running -> loadingLabel
                    result == ActionResult.SUCCESS -> successLabel
                    result == ActionResult.FAILED -> errorLabel
                    else -> idleLabel
                },
                color = fg.copy(alpha = contentAlpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ──────────────────────── 连接测试结果 ────────────────────────

@Composable
private fun TestResultBanner(
    testConnectionState: TestConnectionState,
    dark: Boolean,
    primary: Color,
    secondary: Color,
) {
    val ok = testConnectionState == TestConnectionState.SUCCESS
    val visible = testConnectionState == TestConnectionState.SUCCESS ||
        testConnectionState == TestConnectionState.FAILED

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
    ) {
        val tone = if (ok) {
            if (dark) FollowDark else FollowLight
        } else {
            if (dark) ErrorRedDark else ErrorRedLight
        }
        val shape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(shape)
                .background(tone.copy(alpha = if (dark) 0.14f else 0.09f))
                .border(BorderStroke(0.5.dp, tone.copy(alpha = if (dark) 0.30f else 0.22f)), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                contentDescription = null,
                tint = tone,
                modifier = Modifier
                    .size(17.dp)
                    .offset(y = 1.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (ok) R.string.webdav_connection_success else R.string.webdav_connection_failed,
                    ),
                    color = if (ok) primary else tone,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (!ok) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.webdav_test_failed_hint),
                        color = secondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

