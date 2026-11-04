package com.piku.client.core.reader

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.core.storage.media.ShareTargets
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuSheetTitle
import com.piku.client.core.ui.theme.DarkPikuColors
import com.piku.client.core.ui.theme.LocalPikuColors
import com.piku.client.core.ui.theme.LoginTextPrimaryDark
import com.piku.client.core.ui.theme.LoginTextSecondaryDark
import com.piku.client.core.ui.theme.NovelReaderTheme
import com.piku.client.core.ui.theme.PikuColors

private val WechatGreen = Color(0xFF07C160)
private val WechatGreenBg = Color(0x1507C160)
private val QqBlue = Color(0xFF12B7F5)
private val QqBlueBg = Color(0x1512B7F5)

private val CardShape = RoundedCornerShape(16.dp)
private val IconBoxShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageActionSheet(
    dark: Boolean,
    imageCount: Int,
    sharingImage: Boolean,
    sharingTargetPackage: String?,
    wechatInstalled: Boolean,
    qqInstalled: Boolean,
    wechatIcon: Drawable?,
    qqIcon: Drawable?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShareToWechat: () -> Unit,
    onShareToQQ: () -> Unit,
    onShareMore: () -> Unit,
    onSaveAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cardBg = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.65f)
    val cardBorder = PikuColors.border.copy(alpha = 0.5f)
    val dividerColor = PikuColors.border.copy(alpha = 0.5f)

    fun dismissAndThen(action: () -> Unit) {
        action()
        onDismiss()
    }

    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
        sheetState = sheetState,
    ) {
            // 标题行：下滑/点遮罩/返回键关闭，不再放关闭按钮
            PikuSheetTitle(text = stringResource(R.string.detail_share_title))

            // ---- 高频的保存置顶 ----
            Spacer(Modifier.height(12.dp))
            SectionLabel(text = stringResource(R.string.detail_save_section))
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(cardBg)
                    .border(0.5.dp, cardBorder, CardShape),
            ) {
                ActionRow(
                    title = stringResource(R.string.detail_action_save),
                    subtitle = stringResource(R.string.detail_save_current_sub),
                    iconBg = PikuColors.accent.copy(alpha = 0.1f),
                    onClick = { dismissAndThen(onSave) },
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = PikuColors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                if (imageCount > 1) {
                    CardDivider()
                    ActionRow(
                        title = stringResource(R.string.detail_action_save_all, imageCount),
                        subtitle = stringResource(R.string.detail_save_all_sub, imageCount),
                        iconBg = PikuColors.accent.copy(alpha = 0.1f),
                        onClick = { dismissAndThen(onSaveAll) },
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.SaveAlt,
                                contentDescription = null,
                                tint = PikuColors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }

            // ---- 低频的分享收敛为紧凑文字行 ----
            // 分享行点按不关面板：下载期间 loading 转圈在被点的行，拉起分享后才关；
            // 保存行还是点按即关（同步快照，无需等待）
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = dividerColor,
            )
            Spacer(Modifier.height(10.dp))
            SectionLabel(text = stringResource(R.string.detail_share_section))
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(cardBg)
                    .border(0.5.dp, cardBorder, CardShape),
            ) {
                val sharingWechat = sharingImage && sharingTargetPackage == ShareTargets.WECHAT
                val sharingQq = sharingImage && sharingTargetPackage == ShareTargets.QQ
                val sharingMore = sharingImage && sharingTargetPackage == null
                ActionRow(
                    title = stringResource(R.string.detail_share_to_wechat),
                    subtitle = null,
                    iconBg = if (wechatIcon != null) Color.Transparent else WechatGreenBg,
                    dimmed = !wechatInstalled,
                    enabled = !sharingImage,
                    dense = true,
                    trailingState = when {
                        sharingWechat -> TrailingState.Loading
                        !wechatInstalled -> TrailingState.Hint
                        else -> TrailingState.Chevron
                    },
                    onClick = onShareToWechat,
                    leading = {
                        AppIconOrGlyph(
                            icon = wechatIcon,
                            glyph = "微",
                            glyphColor = WechatGreen,
                            dense = true,
                        )
                    },
                )
                CardDivider()
                ActionRow(
                    title = stringResource(R.string.detail_share_to_qq),
                    subtitle = null,
                    iconBg = if (qqIcon != null) Color.Transparent else QqBlueBg,
                    dimmed = !qqInstalled,
                    enabled = !sharingImage,
                    dense = true,
                    trailingState = when {
                        sharingQq -> TrailingState.Loading
                        !qqInstalled -> TrailingState.Hint
                        else -> TrailingState.Chevron
                    },
                    onClick = onShareToQQ,
                    leading = {
                        AppIconOrGlyph(
                            icon = qqIcon,
                            glyph = "Q",
                            glyphColor = QqBlue,
                            dense = true,
                        )
                    },
                )
                CardDivider()
                ActionRow(
                    title = stringResource(R.string.detail_share_to_more),
                    subtitle = null,
                    iconBg = PikuColors.accent.copy(alpha = 0.1f),
                    enabled = !sharingImage,
                    dense = true,
                    trailingState = if (sharingMore) TrailingState.Loading else TrailingState.Chevron,
                    onClick = onShareMore,
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.IosShare,
                            contentDescription = null,
                            tint = PikuColors.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            if (sharingImage) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.detail_share_preparing),
                        color = PikuColors.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
    }
}

/** 有真实 App 图标就展示真图标，否则用品牌色字块兜底。 */
@Composable
private fun AppIconOrGlyph(icon: Drawable?, glyph: String, glyphColor: Color, dense: Boolean = false) {
    if (icon != null) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            modifier = Modifier
                .size(if (dense) 30.dp else 38.dp)
                .clip(IconBoxShape),
        )
    } else {
        Text(
            text = glyph,
            color = glyphColor,
            fontSize = if (dense) 13.sp else 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = PikuColors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = PikuColors.border.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

private enum class TrailingState { Chevron, Hint, Loading }

/**
 * 面板内统一的行样式：图标盒 + 标题（+ 可选副标题）+ 右侧尾缀。
 * 分享行用 [dense] 紧凑版（30dp 图标 + 更小字号行距），只是跳板，不占地方；
 * 保存行保持标准尺寸，保证高频操作好点。
 */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String?,
    iconBg: Color,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    dense: Boolean = false,
    trailingState: TrailingState = TrailingState.Chevron,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
) {
    val iconSize = if (dense) 30.dp else 38.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (dimmed || !enabled) 0.55f else 1f)
            .padding(horizontal = 12.dp, vertical = if (dense) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(IconBoxShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            leading()
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PikuColors.textPrimary,
                fontSize = if (dense) 13.sp else 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = PikuColors.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        when (trailingState) {
            TrailingState.Loading -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(if (dense) 14.dp else 16.dp),
            )
            TrailingState.Hint -> Text(
                text = stringResource(R.string.detail_share_not_installed),
                color = PikuColors.textFaint,
                fontSize = 11.sp,
            )
            TrailingState.Chevron -> Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = PikuColors.textFaint,
                modifier = Modifier.size(if (dense) 16.dp else 18.dp),
            )
        }
    }
}

private val ReaderFg = NovelReaderTheme.Ink.fg
private val ReaderFgMuted = LoginTextSecondaryDark
private val ReaderLine = LoginTextPrimaryDark.copy(alpha = 0.12f)

/** 阅读器内长按专用：与顶/底栏同色系的行式面板，详情页那份保持不变 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderImageActionSheet(
    imageCount: Int,
    sharingImage: Boolean,
    sharingTargetPackage: String?,
    wechatInstalled: Boolean,
    qqInstalled: Boolean,
    wechatIcon: Drawable?,
    qqIcon: Drawable?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSaveAll: () -> Unit,
    onShareToWechat: () -> Unit,
    onShareToQQ: () -> Unit,
    onShareMore: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    CompositionLocalProvider(LocalPikuColors provides DarkPikuColors) {
        PikuBottomSheet(
            onDismissRequest = onDismiss,
            dark = true,
            sheetState = sheetState,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_share_title),
                    color = ReaderFg,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.detail_fullscreen_close),
                    color = ReaderFgMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            ReaderActionRow(
                leading = { ReaderLeadingIcon(Icons.Outlined.Download) },
                title = stringResource(R.string.detail_action_save),
                subtitle = stringResource(R.string.detail_save_current_sub),
                onClick = onSave,
            )
            if (imageCount > 1) {
                ReaderActionRow(
                    leading = { ReaderLeadingIcon(Icons.Outlined.SaveAlt) },
                    title = stringResource(R.string.detail_action_save_all, imageCount),
                    onClick = onSaveAll,
                )
            }
            ReaderActionRow(
                leading = { ReaderAppIcon(wechatIcon, "微", WechatGreen) },
                title = stringResource(R.string.detail_share_to_wechat),
                enabled = wechatInstalled && !sharingImage,
                loading = sharingImage && sharingTargetPackage == ShareTargets.WECHAT,
                trailingHint = if (wechatInstalled) null else stringResource(R.string.detail_share_not_installed),
                onClick = onShareToWechat,
            )
            ReaderActionRow(
                leading = { ReaderAppIcon(qqIcon, "Q", QqBlue) },
                title = stringResource(R.string.detail_share_to_qq),
                enabled = qqInstalled && !sharingImage,
                loading = sharingImage && sharingTargetPackage == ShareTargets.QQ,
                trailingHint = if (qqInstalled) null else stringResource(R.string.detail_share_not_installed),
                onClick = onShareToQQ,
            )
            ReaderActionRow(
                leading = { ReaderLeadingIcon(Icons.Outlined.IosShare) },
                title = stringResource(R.string.detail_share_to_more),
                enabled = !sharingImage,
                loading = sharingImage && sharingTargetPackage == null,
                onClick = onShareMore,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ReaderLeadingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = ReaderFgMuted,
        modifier = Modifier.size(19.dp),
    )
}

/** 有真 App 图标就显示真图标，否则用品牌色字兜底 */
@Composable
private fun ReaderAppIcon(icon: Drawable?, glyph: String, glyphColor: Color) {
    if (icon != null) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp)),
        )
    } else {
        Text(
            text = glyph,
            color = glyphColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReaderActionRow(
    leading: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingHint: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .alpha(if (enabled) 1f else 0.5f),
            contentAlignment = Alignment.Center,
        ) {
            leading()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) ReaderFg else ReaderFgMuted,
                fontSize = 13.sp,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(text = subtitle, color = ReaderFgMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
        when {
            loading -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = ReaderFg,
                modifier = Modifier.size(16.dp),
            )
            trailingHint != null -> Text(text = trailingHint, color = ReaderFgMuted, fontSize = 11.sp)
            else -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ReaderFgMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = ReaderLine)
}
