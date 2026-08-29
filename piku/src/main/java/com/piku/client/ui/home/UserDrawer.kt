package com.piku.client.ui.home

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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.piku.client.domain.model.ThemeMode
import com.piku.client.domain.model.UserProfile
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.themedSwitchColors

@Composable
fun UserDrawer(
    drawerState: DrawerState,
    userProfile: UserProfile?,
    adultEnabled: Boolean,
    themeMode: ThemeMode,
    customBackgroundPath: String?,
    language: AppLanguage,
    currentVersion: String,
    updateAvailable: Boolean,
    onToggleAdult: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onThemeClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onTagsClick: () -> Unit,
    onFollowUsersClick: () -> Unit,
    onProfileClick: () -> Unit,
    /** 登录后点头像/名字区域进入自己的个人主页 */
    onProfileOpen: () -> Unit,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    dark: Boolean,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerPanel(
                userProfile = userProfile,
                adultEnabled = adultEnabled,
                themeMode = themeMode,
                customBackgroundPath = customBackgroundPath,
                language = language,
                currentVersion = currentVersion,
                updateAvailable = updateAvailable,
                onToggleAdult = onToggleAdult,
                onSettingsClick = onSettingsClick,
                onAboutClick = onAboutClick,
                onThemeClick = onThemeClick,
                onBackgroundClick = onBackgroundClick,
                onHistoryClick = onHistoryClick,
                onCollectionClick = onCollectionClick,
                onTagsClick = onTagsClick,
                onFollowUsersClick = onFollowUsersClick,
                onProfileClick = onProfileClick,
                onProfileOpen = onProfileOpen,
                onLoginClick = onLoginClick,
                onLogout = onLogout,
                dark = dark,
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
    adultEnabled: Boolean,
    themeMode: ThemeMode,
    customBackgroundPath: String?,
    language: AppLanguage,
    currentVersion: String,
    updateAvailable: Boolean,
    onToggleAdult: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onThemeClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onTagsClick: () -> Unit,
    onFollowUsersClick: () -> Unit,
    onProfileClick: () -> Unit,
    onProfileOpen: () -> Unit,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    val divider = if (dark) LoginCardBorderDark else LoginCardBorderLight
    // 图标统一用主题自适应单色：浅色近黑、深色近白，保证两种主题下都清晰可见
    val iconAccent = if (dark) LoginTextPrimaryDark else AccentDark

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
            userProfile = userProfile,
            onProfileOpen = onProfileOpen,
            onLoginClick = onLoginClick,
            dark = dark,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
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
            if (userProfile != null) {
                DrawerMenuRow(
                    icon = Icons.Outlined.Group,
                    label = stringResource(R.string.menu_follow_users),
                    onClick = onFollowUsersClick,
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
                label = stringResource(R.string.menu_settings),
                onClick = onSettingsClick,
                dark = dark,
                accent = iconAccent,
            )
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            color = divider,
        )
        Spacer(Modifier.height(6.dp))
        // 退出登录
        if (userProfile != null) {
            DrawerMenuRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                label = stringResource(R.string.logout),
                onClick = onLogout,
                dark = dark,
                accent = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B),
                showChevron = false,
            )
        }
    }
}

@Composable
private fun DrawerHeader(
    userProfile: UserProfile?,
    onProfileOpen: () -> Unit,
    onLoginClick: () -> Unit,
    dark: Boolean,
) {
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val blobPurple = if (dark) Color(0x409A7FC9) else Color(0x4D9A7FC9)
    val blobPink = if (dark) Color(0x30D8A8B8) else Color(0x3DD8A8B8)
    val ring = if (dark) Color(0x66FFFFFF) else AccentDark.copy(alpha = 0.5f)

    // 已登录：点头部进入自己的个人主页；未登录：点头部进入登录
    val loggedIn = userProfile != null
    val headerClickable = if (loggedIn) userProfile?.uid != null else true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = headerClickable) {
                if (loggedIn) onProfileOpen() else onLoginClick()
            }
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
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.5.dp, ring), CircleShape)
                    .padding(3.dp),
            ) {
                UserAvatar(
                    avatarUrl = userProfile?.avatarUrl,
                    onClick = {},
                    dark = dark,
                    size = 54.dp,
                )
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                val displayName = userProfile?.name
                Text(
                    text = when {
                        !displayName.isNullOrBlank() -> displayName
                        loggedIn -> stringResource(R.string.account_logged_in)
                        else -> stringResource(R.string.account_logged_out)
                    },
                    color = primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (userProfile?.uid != null) {
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = "ID: ${userProfile.uid}",
                        color = faint,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!loggedIn) {
                    Spacer(Modifier.size(3.dp))
                    // 未登录提示：引导用户点击头像/此处登录
                    Text(
                        text = stringResource(R.string.drawer_login_hint),
                        color = if (dark) LoginTextPrimaryDark else AccentDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = faint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
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
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
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
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
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
