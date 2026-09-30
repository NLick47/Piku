package com.piku.client.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.ImageRouteMode
import com.piku.client.domain.model.ThemeMode
import com.piku.client.domain.model.UserProfile
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.themedSwitchColors

@Composable
fun UserDrawer(
    drawerState: DrawerState,
    userProfile: UserProfile?,
    /** poipiku（主源）的登录态：抽屉里 poipiku 专属入口用它，与头部显示的源无关 */
    loggedIn: Boolean,
    /** 头部显示的那一个账号 = 当前首页源的账号；null = 还没算出来（画骨架） */
    headerAccount: SourceAccountRow?,
    adultEnabled: Boolean,
    themeMode: ThemeMode,
    imageRouteMode: ImageRouteMode,
    customBackgroundPath: String?,
    language: AppLanguage,
    currentVersion: String,
    updateAvailable: Boolean,
    onToggleAdult: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onThemeClick: () -> Unit,
    onImageRouteClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onTagsClick: () -> Unit,
    onFollowUsersClick: () -> Unit,
    onBlockUsersClick: () -> Unit = {},
    onProfileClick: () -> Unit,
    onPublishClick: () -> Unit = {},
    onLoginClick: () -> Unit,
    /** 头部左侧：主源进个人主页、其它源进账号页、未登录进对应源的登录页 */
    onHeaderClick: () -> Unit,
    /** 头部右侧 chevron 与菜单行：进账号管理页 */
    onAccountsClick: () -> Unit,
    /** 底部账号动作（作用于当前源）：未登录去登录、已登录去断开 */
    onAccountAction: () -> Unit = {},
    onAvatarClick: () -> Unit,
    dark: Boolean,
    gesturesEnabled: Boolean = true,
    aiTranslateEnabled: Boolean = false,
    historyRetentionDays: Int = 30,
    onAiTranslateClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onRetentionClick: () -> Unit = {},
    onWebDavClick: () -> Unit = {},
    /** 首页源：当前源的文案资源与换源入口 */
    homeSourceLabelRes: Int = R.string.home_source_poipiku,
    onHomeSourceClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var settingsExpanded by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerPanel(
                userProfile = userProfile,
                loggedIn = loggedIn,
                headerAccount = headerAccount,
                adultEnabled = adultEnabled,
                themeMode = themeMode,
                imageRouteMode = imageRouteMode,
                customBackgroundPath = customBackgroundPath,
                language = language,
                currentVersion = currentVersion,
                updateAvailable = updateAvailable,
                onToggleAdult = onToggleAdult,
                onSettingsClick = {
                    settingsExpanded = !settingsExpanded
                    onSettingsClick()
                },
                onAboutClick = onAboutClick,
                onThemeClick = onThemeClick,
                onImageRouteClick = onImageRouteClick,
                onBackgroundClick = onBackgroundClick,
                onHistoryClick = onHistoryClick,
                onCollectionClick = onCollectionClick,
                onTagsClick = onTagsClick,
                onFollowUsersClick = onFollowUsersClick,
                onBlockUsersClick = onBlockUsersClick,
                onProfileClick = onProfileClick,
                onPublishClick = onPublishClick,
                onLoginClick = onLoginClick,
                onHeaderClick = onHeaderClick,
                onAccountsClick = onAccountsClick,
                onAccountAction = onAccountAction,
                onAvatarClick = onAvatarClick,
                dark = dark,
                settingsExpanded = settingsExpanded,
                aiTranslateEnabled = aiTranslateEnabled,
                historyRetentionDays = historyRetentionDays,
                onAiTranslateClick = onAiTranslateClick,
                onLanguageClick = onLanguageClick,
                onRetentionClick = onRetentionClick,
                onWebDavClick = onWebDavClick,
                homeSourceLabelRes = homeSourceLabelRes,
                onHomeSourceClick = onHomeSourceClick,
            )
        },
        scrimColor = if (dark) Color(0xB3000000) else Color(0x99000000),
        gesturesEnabled = gesturesEnabled,
        content = content,
    )
}

@Composable
private fun DrawerPanel(
    userProfile: UserProfile?,
    loggedIn: Boolean,
    headerAccount: SourceAccountRow?,
    adultEnabled: Boolean,
    themeMode: ThemeMode,
    imageRouteMode: ImageRouteMode,
    customBackgroundPath: String?,
    language: AppLanguage,
    currentVersion: String,
    updateAvailable: Boolean,
    onToggleAdult: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onThemeClick: () -> Unit,
    onImageRouteClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onTagsClick: () -> Unit,
    onFollowUsersClick: () -> Unit,
    onBlockUsersClick: () -> Unit = {},
    onProfileClick: () -> Unit,
    onPublishClick: () -> Unit = {},
    onLoginClick: () -> Unit,
    onHeaderClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onAccountAction: () -> Unit,
    onAvatarClick: () -> Unit,
    dark: Boolean,
    settingsExpanded: Boolean,
    aiTranslateEnabled: Boolean,
    historyRetentionDays: Int,
    onAiTranslateClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onRetentionClick: () -> Unit,
    onWebDavClick: () -> Unit,
    homeSourceLabelRes: Int,
    onHomeSourceClick: () -> Unit,
) {
    val primary = PikuColors.textPrimary
    val faint = PikuColors.textFaint
    val divider = PikuColors.border
    val iconAccent = PikuColors.accent

    // 断开是破坏性动作，先确认；文案由该源自己的插件给
    var confirmDisconnect by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(304.dp)
            .graphicsLayer {
                shadowElevation = 20.dp.toPx()
                shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp)
                clip = true
            }
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(Color(0xFF23211F), Color(0xFF262031))
                    else listOf(Color(0xFFFAF8F5), Color(0xFFF1EDF6)),
                ),
            )
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x33FFFFFF) else Color(0x66FFFFFF)),
                RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        DrawerHeader(
            row = headerAccount,
            onHeaderClick = onHeaderClick,
            onAccountsClick = onAccountsClick,
            onAvatarClick = onAvatarClick,
            dark = dark,
        )
        val scrollState = rememberScrollState()
        LaunchedEffect(settingsExpanded) {
            if (settingsExpanded) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            DrawerMenuRow(
                icon = Icons.Outlined.ManageAccounts,
                label = stringResource(R.string.account_title),
                onClick = onAccountsClick,
                dark = dark,
                accent = iconAccent,
            )
            Spacer(Modifier.height(2.dp))
            if (loggedIn) {
                Spacer(Modifier.height(2.dp))
                DrawerMenuRow(
                    icon = Icons.Outlined.PostAdd,
                    label = stringResource(R.string.menu_publish),
                    onClick = onPublishClick,
                    dark = dark,
                    accent = iconAccent,
                )
            }
            if (userProfile?.profileUrl != null) {
                Spacer(Modifier.height(2.dp))
                DrawerMenuRow(
                    icon = Icons.Outlined.Person,
                    label = stringResource(R.string.menu_edit_profile),
                    onClick = onProfileClick,
                    dark = dark,
                    accent = iconAccent,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 22.dp),
                color = divider,
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel(
                text = stringResource(R.string.menu_section_library),
                color = faint,
            )
            DrawerMenuRow(
                icon = Icons.Outlined.History,
                label = stringResource(R.string.menu_history),
                onClick = onHistoryClick,
                dark = dark,
                accent = iconAccent,
            )
            DrawerMenuRow(
                icon = Icons.Outlined.BookmarkBorder,
                label = stringResource(R.string.menu_collection),
                onClick = onCollectionClick,
                dark = dark,
                accent = iconAccent,
            )
            DrawerMenuRow(
                icon = Icons.Outlined.Tag,
                label = stringResource(R.string.menu_my_tags),
                onClick = onTagsClick,
                dark = dark,
                accent = iconAccent,
            )
            if (loggedIn) {
                DrawerMenuRow(
                    icon = Icons.Outlined.Group,
                    label = stringResource(R.string.menu_follow_users),
                    onClick = onFollowUsersClick,
                    dark = dark,
                    accent = iconAccent,
                )
                DrawerMenuRow(
                    icon = Icons.Outlined.Block,
                    label = stringResource(R.string.menu_block_users),
                    onClick = onBlockUsersClick,
                    dark = dark,
                    accent = iconAccent,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 22.dp),
                color = divider,
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel(
                text = stringResource(R.string.menu_section_settings),
                color = faint,
            )
            DrawerMenuRow(
                icon = Icons.Outlined.SwapHoriz,
                label = stringResource(R.string.home_source),
                trailing = stringResource(homeSourceLabelRes),
                onClick = onHomeSourceClick,
                dark = dark,
                accent = iconAccent,
            )
            AdultContentRow(
                adultEnabled = adultEnabled,
                onToggleAdult = onToggleAdult,
                dark = dark,
                accent = iconAccent,
            )
            DrawerMenuRow(
                icon = Icons.Outlined.DarkMode,
                label = stringResource(R.string.menu_theme),
                trailing = stringResource(themeMode.labelRes()),
                onClick = onThemeClick,
                dark = dark,
                accent = iconAccent,
            )
            DrawerMenuRow(
                icon = Icons.Outlined.Wallpaper,
                label = stringResource(R.string.menu_background),
                trailing = stringResource(
                    if (customBackgroundPath != null) {
                        R.string.background_state_custom
                    } else {
                        R.string.background_state_default
                    },
                ),
                onClick = onBackgroundClick,
                dark = dark,
                accent = iconAccent,
            )
            DrawerMenuRow(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.menu_about),
                onClick = onAboutClick,
                dark = dark,
                accent = iconAccent,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (updateAvailable) {
                            UpdateChip(
                                text = stringResource(R.string.update_status_available),
                                dark = dark,
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            text = "v$currentVersion",
                            color = faint,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
            DrawerMenuRow(
                icon = Icons.Outlined.Settings,
                label = if (settingsExpanded) stringResource(R.string.detail_show_less)
                else stringResource(R.string.menu_settings),
                onClick = onSettingsClick,
                dark = dark,
                accent = iconAccent,
                showChevron = false,
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = faint,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                rotationZ = if (settingsExpanded) 90f else 0f
                            },
                    )
                },
            )
            if (settingsExpanded) {
                Column {
                    DrawerMenuRow(
                        icon = Icons.Outlined.GTranslate,
                        label = stringResource(R.string.menu_ai_translate),
                        trailing = if (aiTranslateEnabled) stringResource(R.string.ai_translate_state_on)
                        else stringResource(R.string.ai_translate_state_off),
                        onClick = onAiTranslateClick,
                        dark = dark,
                        accent = iconAccent,
                    )
                    DrawerMenuRow(
                        icon = Icons.Outlined.Translate,
                        label = stringResource(R.string.menu_language),
                        trailing = stringResource(language.labelRes()),
                        onClick = onLanguageClick,
                        dark = dark,
                        accent = iconAccent,
                    )
                    DrawerMenuRow(
                        icon = Icons.Outlined.CloudSync,
                        label = stringResource(R.string.menu_webdav_sync),
                        onClick = onWebDavClick,
                        dark = dark,
                        accent = iconAccent,
                    )
                    DrawerMenuRow(
                        icon = Icons.Outlined.DeleteSweep,
                        label = stringResource(R.string.menu_history_retention),
                        trailing = retentionLabel(historyRetentionDays),
                        onClick = onRetentionClick,
                        dark = dark,
                        accent = iconAccent,
                    )
                    DrawerMenuRow(
                        icon = Icons.Outlined.AltRoute,
                        label = stringResource(R.string.menu_image_route),
                        trailing = stringResource(imageRouteMode.labelRes()),
                        onClick = onImageRouteClick,
                        dark = dark,
                        accent = iconAccent,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            color = divider,
        )
        Spacer(Modifier.height(6.dp))
        // 当前源的账号动作：放在抽屉底部，和以前 poipiku 的退出登录同一个位置，
        // 但现在它作用于**当前显示的源**——看 pixiv 时这里退出的是 pixiv
        headerAccount?.let { row ->
            val loggedIn = row.loggedIn
            val canLogIn = !loggedIn && row.loginRoute != null
            if (loggedIn || canLogIn) {
                DrawerMenuRow(
                    icon = if (loggedIn) {
                        Icons.AutoMirrored.Outlined.Logout
                    } else {
                        Icons.Outlined.Login
                    },
                    label = stringResource(if (loggedIn) R.string.logout else R.string.login_button),
                    onClick = { if (loggedIn) confirmDisconnect = true else onAccountAction() },
                    dark = dark,
                    accent = if (loggedIn) PikuColors.error else iconAccent,
                    showChevron = false,
                )
            }
        }
    }

    val confirming = headerAccount
    if (confirmDisconnect && confirming != null) {
        val message = confirming.logoutMessageRes
        if (message != null) {
            ConfirmDestructiveDialog(
                title = stringResource(R.string.logout),
                message = stringResource(message, stringResource(confirming.labelRes)),
                confirmLabel = stringResource(R.string.logout_confirm),
                onConfirm = {
                    confirmDisconnect = false
                    onAccountAction()
                },
                onDismiss = { confirmDisconnect = false },
                dark = dark,
            )
        }
    }
}

@Composable
private fun DrawerHeader(
    row: SourceAccountRow?,
    onHeaderClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    dark: Boolean,
) {
    val faint = PikuColors.textFaint
    val titleColor = PikuColors.textPrimary
    val blobPurple = if (dark) Color(0x409A7FC9) else Color(0x4D9A7FC9)
    val blobPink = if (dark) Color(0x30D8A8B8) else Color(0x3DD8A8B8)
    val ring = if (dark) Color(0x66FFFFFF) else AccentDark.copy(alpha = 0.5f)

    // 三态：未登录 / 骨架（已登录但资料未到，或行还没算出来）/ 就绪
    val loggedIn = row?.loggedIn == true
    val skeleton = row == null || row.pending
    val account = row?.account
    // 各源平权：副标题一律「源名 · 账号标识」，不因为谁是"主源"就省掉源名
    val sourceName = row?.let { stringResource(it.labelRes) }.orEmpty()
    val subtitle = if (loggedIn && !skeleton) {
        listOfNotNull(
            sourceName.takeIf { it.isNotBlank() },
            account?.account?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").takeIf { it.isNotBlank() }
    } else {
        null
    }
    val canLogIn = row?.loginRoute != null

    val pulseState = if (skeleton) rememberSkeletonPulse() else null
    val skelColor = faint.copy(alpha = 0.16f + 0.2f * (pulseState?.value ?: 0f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blobPurple, Color.Transparent),
                    center = Offset(size.width * 0.92f, 0f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.92f, 0f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blobPink, Color.Transparent),
                    center = Offset(0f, size.height * 0.85f),
                    radius = size.width * 0.55f,
                ),
                radius = size.width * 0.55f,
                center = Offset(0f, size.height * 0.85f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧＝"这个人"：主源进个人主页，其它源进账号页，未登录进对应源的登录页
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !skeleton) { onHeaderClick() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.5.dp, ring), CircleShape)
                        .padding(3.dp),
                ) {
                    if (skeleton) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(skelColor),
                        )
                    } else {
                        val avatarUrl = account?.avatarUrl
                        val hasAvatar = !avatarUrl.isNullOrBlank() &&
                            !avatarUrl.contains("default_user")
                        UserAvatar(
                            avatarUrl = avatarUrl,
                            onClick = { if (hasAvatar) onAvatarClick() },
                            dark = dark,
                            size = 54.dp,
                        )
                    }
                }
                Spacer(Modifier.width(15.dp))
                when {
                    skeleton -> Column(Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .height(15.dp)
                                .fillMaxWidth(0.55f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(skelColor),
                        )
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .height(11.dp)
                                .fillMaxWidth(0.35f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(skelColor),
                        )
                    }

                    loggedIn -> Column(Modifier.weight(1f)) {
                        Text(
                            text = account?.displayName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.account_logged_in),
                            color = titleColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.size(3.dp))
                            Text(
                                text = subtitle,
                                color = faint,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    else -> Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.account_source_logged_out, sourceName),
                            color = titleColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 没有登录页的源别给一个点了没反应的提示
                        if (canLogIn) {
                            Spacer(Modifier.size(3.dp))
                            Text(
                                text = stringResource(R.string.drawer_login_hint),
                                color = PikuColors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // 右侧＝账号页：左侧管"这个人是谁"，它管"还有谁"
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAccountsClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.account_title),
                    tint = faint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 骨架占位的呼吸动画 */
@Composable
private fun rememberSkeletonPulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "drawerHeaderSkeleton")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drawerHeaderPulse",
    )
}

@Composable
private fun SectionLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
    )
}

@Composable
private fun DrawerMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    dark: Boolean,
    accent: Color,
    showChevron: Boolean = true,
    trailing: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val primary = PikuColors.textPrimary
    val faint = PikuColors.textFaint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = if (dark) 0.22f else 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(
            text = label,
            color = primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        } else if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                color = faint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = faint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}

private fun ImageRouteMode.labelRes(): Int = when (this) {
    ImageRouteMode.AUTO -> R.string.image_route_auto
    ImageRouteMode.DIRECT -> R.string.image_route_direct
    ImageRouteMode.RELAY -> R.string.image_route_relay
}

fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_follow_system
    AppLanguage.ZH -> R.string.language_zh
    AppLanguage.EN -> R.string.language_en
    AppLanguage.JA -> R.string.language_ja
}

@Composable
private fun AdultContentRow(
    adultEnabled: Boolean,
    onToggleAdult: () -> Unit,
    dark: Boolean,
    accent: Color,
) {
    val primary = PikuColors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggleAdult)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = if (dark) 0.22f else 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(
            text = stringResource(R.string.menu_adult_content),
            color = primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = adultEnabled,
            onCheckedChange = { onToggleAdult() },
            colors = themedSwitchColors(dark),
        )
    }
}

@Composable
private fun UpdateChip(
    text: String,
    dark: Boolean,
) {
    val color = if (dark) Color(0xFF9A7FC9) else Color(0xFF5E4B8B)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = if (dark) 0.22f else 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun retentionLabel(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.retention_7d
        30 -> R.string.retention_30d
        90 -> R.string.retention_90d
        else -> R.string.retention_forever
    },
)
