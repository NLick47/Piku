package com.piku.client.ui.home

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.data.repository.SyncResult
import com.piku.client.data.repository.SyncState
import com.piku.client.data.repository.TestConnectionState
import com.piku.client.ui.common.GlassCard
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.StatusSyncing
import com.piku.client.ui.theme.themedSwitchColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ──────────────────────── 尺寸常量 ────────────────────────

private val PagePaddingH = 20.dp
private val CardCorner = 20.dp
private val FieldCorner = 14.dp
private val PrimaryButtonHeight = 52.dp
private val SecondaryButtonHeight = 48.dp

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
    val focusManager: FocusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    BackHandler(enabled = keyboard?.let { true } ?: true) {
        // 软键盘显示时按返回先收键盘，避免用户输入到一半被退出页面
        if (keyboard != null) {
            keyboard.hide()
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }

    val primary = PikuColors.textPrimary
    val secondary = PikuColors.textSecondary
    val faint = PikuColors.textFaint
    val accent = PikuColors.controlAccent
    val border = PikuColors.border

    var passwordVisible by remember { mutableStateOf(false) }

    val urlInvalid = url.isNotBlank() &&
        !url.startsWith("http://", ignoreCase = true) &&
        !url.startsWith("https://", ignoreCase = true)
    val configValid = url.isNotBlank() && username.isNotBlank() && !urlInvalid

    val isSyncing = syncState == SyncState.SYNCING
    val isTesting = testConnectionState == TestConnectionState.TESTING
    val actionsEnabled = enabled && configValid && !isSyncing && !isTesting
    // 测试成功的展示窗口内禁用测试按钮，避免手滑重复测试触发限流
    val testEnabled = actionsEnabled && testConnectionState != TestConnectionState.SUCCESS

    // 测试结果 Snackbar 提示
    LaunchedEffect(testConnectionState) {
        when (testConnectionState) {
            TestConnectionState.SUCCESS -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.webdav_connection_success),
                )
                delay(1500L)
                onClearTestResult()
            }
            TestConnectionState.FAILED -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.webdav_connection_failed),
                )
                delay(1500L)
                onClearTestResult()
            }
            else -> {}
        }
    }
    // 修改任一配置项时清掉上一次的测试结果。
    // 用 snapshotFlow + debounce 替代 key-based LaunchedEffect：
    // - debounce 500ms 避免连续输入时反复 clear，与新一轮 TESTING 形成竞态
    // - distinctUntilChanged 保证真实变化才触发，配置项空串初始化不会清空 SUCCESS
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(url, username, password) }
            .drop(1)
            .debounce(500L)
            .distinctUntilChanged()
            .collect {
                if (testConnectionState == TestConnectionState.SUCCESS ||
                    testConnectionState == TestConnectionState.FAILED
                ) {
                    onClearTestResult()
                }
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
                Spacer(Modifier.height(4.dp))

                SyncHeroCard(
                    enabled = enabled,
                    syncState = syncState,
                    syncResult = syncResult,
                    lastSyncAt = lastSyncAt,
                    dark = dark,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    onCheckedChange = onEnabledChange,
                )

                Spacer(Modifier.height(20.dp))
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

                Spacer(Modifier.height(18.dp))

                PrimaryActionButton(
                    onClick = onSyncNow,
                    enabled = actionsEnabled,
                    running = isSyncing,
                    dark = dark,
                    icon = Icons.Outlined.Sync,
                    idleLabel = stringResource(R.string.webdav_sync_now),
                    runningLabel = stringResource(R.string.webdav_syncing),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                SecondaryActionButton(
                    onClick = onTestConnection,
                    enabled = testEnabled,
                    running = isTesting,
                    dark = dark,
                    accent = accent,
                    primary = primary,
                    icon = Icons.Outlined.Link,
                    idleLabel = stringResource(R.string.webdav_test_connection),
                    runningLabel = stringResource(R.string.webdav_testing),
                    modifier = Modifier.fillMaxWidth(),
                )

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

                Spacer(Modifier.height(24.dp))

                HelpFooter(faint = faint, secondary = secondary, accent = accent)

                Spacer(Modifier.height(28.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ──────────────────────── 顶栏 ────────────────────────

/** 与历史/收藏等抽屉子页一致的顶栏：返回键 + 标题，直接落在页面底色上 */
@Composable
private fun SettingsTopBar(
    title: String,
    primary: Color,
    onBack: () -> Unit,
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
            text = title,
            color = primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ──────────────────────── 状态总览卡 ────────────────────────

/**
 * 启用开关 + 实时同步状态合二为一的总览卡：
 * 图标与底色随状态变化（关闭/已开启/同步中/成功/失败），
 * 副标题直接展示上次同步时间或失败原因，成功时附统计 chips。
 */
@Composable
private fun SyncHeroCard(
    enabled: Boolean,
    syncState: SyncState,
    syncResult: SyncResult?,
    lastSyncAt: Long,
    dark: Boolean,
    primary: Color,
    secondary: Color,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val successColor = if (dark) FollowDark else FollowLight
    val errorColor = PikuColors.error
    val isSyncing = syncState == SyncState.SYNCING
    val isFailed = syncState == SyncState.FAILED
    val isSuccess = !isSyncing && !isFailed && syncResult?.state == SyncState.SUCCESS

    val iconBg by animateColorAsState(
        targetValue = when {
            isSyncing -> StatusSyncing.copy(alpha = if (dark) 0.20f else 0.13f)
            isFailed -> errorColor.copy(alpha = if (dark) 0.20f else 0.12f)
            isSuccess -> successColor.copy(alpha = if (dark) 0.20f else 0.12f)
            enabled -> accent.copy(alpha = if (dark) 0.22f else 0.13f)
            else -> if (dark) Color(0x12FFFFFF) else Color(0x08000000)
        },
        label = "heroIconBg",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            isSyncing -> StatusSyncing
            isFailed -> errorColor
            isSuccess -> successColor
            enabled -> accent
            else -> secondary
        },
        label = "heroIconTint",
    )

    val title = when {
        isSyncing -> stringResource(R.string.webdav_syncing)
        isFailed -> stringResource(R.string.webdav_sync_failed)
        isSuccess -> stringResource(R.string.webdav_sync_success)
        enabled -> stringResource(R.string.webdav_state_on)
        else -> stringResource(R.string.webdav_state_off)
    }

    val lastSyncText = if (lastSyncAt > 0) {
        val date = remember(lastSyncAt) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSyncAt))
        }
        stringResource(R.string.webdav_last_sync, date)
    } else {
        stringResource(R.string.webdav_never_synced)
    }
    val subtitle = when {
        isFailed -> syncResult?.error?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.webdav_test_failed_hint)
        enabled -> lastSyncText
        else -> stringResource(R.string.webdav_enable_hint)
    }

    val stats = remember(syncResult) {
        val r = syncResult ?: return@remember emptyList()
        if (r.state != SyncState.SUCCESS) return@remember emptyList()
        buildList {
            if (r.backedUpWorks > 0) add(StatItem(R.string.webdav_stat_backup, r.backedUpWorks))
        }
    }

    GlassCard(dark = dark, shape = RoundedCornerShape(CardCorner), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = StatusSyncing,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = if (isFailed) errorColor else secondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = if (isFailed) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onCheckedChange,
                    colors = themedSwitchColors(dark),
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
                        .padding(top = 12.dp),
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

// ──────────────────────── 服务器配置 ────────────────────────

/** 输入框配色：透明容器 + 细边框 + 浮动标签，与旧版一致 */
@Composable
private fun webDavFieldColors(
    dark: Boolean,
    accent: Color,
    border: Color,
    primary: Color,
    secondary: Color,
    faint: Color,
): TextFieldColors {
    val errorColor = PikuColors.error
    return OutlinedTextFieldDefaults.colors(
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
        focusedTrailingIconColor = accent,
        unfocusedTrailingIconColor = secondary,
        errorTrailingIconColor = errorColor,
        focusedSupportingTextColor = errorColor,
        unfocusedSupportingTextColor = secondary,
        errorSupportingTextColor = errorColor,
    )
}

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
    val fieldColors = webDavFieldColors(dark, accent, border, primary, secondary, faint)
    val fieldTextStyle = TextStyle(fontSize = 14.sp, color = primary)

    GlassCard(dark = dark, shape = RoundedCornerShape(CardCorner), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
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

/** 主操作：与登录页同款的渐变玻璃按钮，按压缩放反馈 */
@Composable
private fun PrimaryActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    running: Boolean,
    dark: Boolean,
    icon: ImageVector,
    idleLabel: String,
    runningLabel: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "primaryScale",
    )
    val ink = if (dark) Color(0xFF1C1A18) else Color.White

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled || running) 1f else 0.45f
            }
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x40000000),
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (dark) listOf(Color(0xFFF2F2F2), Color(0xFFC7C7C7))
                    else listOf(Color(0xFF3A3A3A), Color(0xFF141414)),
                ),
            )
            .clickable(
                enabled = enabled && !running,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .height(PrimaryButtonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = ink,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (running) runningLabel else idleLabel,
                color = ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 次操作：描边玻璃按钮
 */
@Composable
private fun SecondaryActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    running: Boolean,
    dark: Boolean,
    accent: Color,
    primary: Color,
    icon: ImageVector,
    idleLabel: String,
    runningLabel: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "secondaryScale",
    )
    val contentColor = if (running) accent else primary
    val label = if (running) runningLabel else idleLabel

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled || running) 1f else 0.45f
            }
            .clip(shape)
            .border(BorderStroke(1.dp, accent.copy(alpha = if (dark) 0.34f else 0.28f)), shape)
            .clickable(
                enabled = enabled && !running,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .height(SecondaryButtonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ──────────────────────── 底部帮助 ────────────────────────

@Composable
private fun HelpFooter(faint: Color, secondary: Color, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    ) {
        Row(
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
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.webdav_nutstore_hint),
            fontSize = 11.sp,
            color = accent.copy(alpha = 0.8f),
            lineHeight = 16.sp,
        )
    }
}
