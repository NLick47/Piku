package com.piku.client.ui.detail

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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.piku.client.data.local.ShareTargets
import com.piku.client.ui.theme.PikuColors

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

    // 通透拟态：面板底半透明透出后方页面，卡片用浅色罩衫托底保证层级，
    // 只收敛在这个面板内，不动全局 MenuPopupBg 等主题 token
    val sheetBg = if (dark) Color(0xDE262421) else Color(0xEDFFFFFF)
    val cardBg = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.65f)
    val cardBorder = PikuColors.border.copy(alpha = 0.5f)
    val dividerColor = PikuColors.border.copy(alpha = 0.5f)

    fun dismissAndThen(action: () -> Unit) {
        action()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, top = 6.dp),
        ) {
            // 顶部拖拽手柄
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PikuColors.textSecondary.copy(alpha = 0.3f)),
                )
            }

            // 标题行：下滑/点遮罩/返回键关闭，不再放关闭按钮
            Text(
                text = stringResource(R.string.detail_share_title),
                color = PikuColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )

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
