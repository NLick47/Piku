package com.piku.client.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.common.MoreMenuButton
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.quietFollowMenuActions
import com.piku.client.ui.theme.DetailHeaderSurfaceLight
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.HomeFrameIcon
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.ShadowAmbient
import com.piku.client.ui.theme.ShadowSpot
import com.piku.client.ui.theme.TranslateActiveBlue

/** 顶栏自身高度（不含状态栏）：48dp 控件 + 上下各 8dp，调用方据此给内容留出顶部内边距 */
internal val DETAIL_TOP_BAR_HEIGHT = 64.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailTopBar(
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
    /** 内容已滚到顶栏下面：底色加深，挡住穿过的内容 */
    scrolled: Boolean = false,
    /** 滚过图区后淡入的作品标题（调用方已按原/译状态取好文案） */
    title: String = "",
    titleVisible: Boolean = false,
    /** 有译文时按钮高亮，点击变为整页原/译切换 */
    showTranslation: Boolean = false,
    /** 文本通道可用就常驻显示：未翻译时点击即翻短字段 */
    /** 顶栏翻译按钮：切换原文/译文 */
    /** 顶栏翻译按钮：打开"换模型重翻"选择器 */
    showFollowMenu: Boolean = false,
    followed: Boolean = false,
    followQuiet: Boolean = false,
    followSending: Boolean = false,
    onQuietFollow: () -> Unit = {},
    onMakePublic: () -> Unit = {},
    onUnfollow: () -> Unit = {},
) {
    // 用透明度做淡入淡出而不是 AnimatedVisibility：
    // 后者在 Row 作用域内会和 RowScope 的同名扩展产生接收者歧义
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible && title.isNotBlank()) 1f else 0f,
        animationSpec = tween(150),
        label = "topBarTitleAlpha",
    )
    val surface = if (dark) GlassHeaderTintDark else DetailHeaderSurfaceLight
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0.96f else 0.72f,
        animationSpec = tween(180),
        label = "topBarSurfaceAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RectangleShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
            .background(surface.copy(alpha = surfaceAlpha))
            .statusBarsPadding()
            .height(DETAIL_TOP_BAR_HEIGHT)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PikuBackButton(
            onClick = onBack,
            dark = dark,
            contentDescription = stringResource(R.string.detail_back),
        )
        IconButton(onClick = onHomeClick) {
            Icon(
                imageVector = HomeFrameIcon,
                contentDescription = stringResource(R.string.detail_home),
                tint = PikuColors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        // 标题槽位恒定占满剩余宽度：右侧翻译按钮位置不会随标题显隐而左右跳动
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha },
            )
        }
        if (showFollowMenu) {
            MoreMenuButton(
                groups = listOf(
                    quietFollowMenuActions(
                        followed = followed,
                        followQuiet = followQuiet,
                        followSending = followSending,
                        onQuietFollow = onQuietFollow,
                        onMakePublic = onMakePublic,
                        onUnfollow = onUnfollow,
                    ),
                ),
            )
        }
    }
}

/**
 * 单字段"原/译"切换 chip（方案 C）。
 * 用独立小按钮而不是长按手势：文本区域的长按已经归属"选中复制"，
 * 抢占会破坏复制描述这类刚需操作。
 * 纯文字无底色无边框：选中 = 正文深色，未选中 = 极浅灰，只靠深浅对比表意；
 * clip 保留是为了让按压涟漪仍是圆角而非方块。
 */
@Composable
internal fun TranslateChip(
    showTranslation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 长按：换模型重翻（原顶栏图标的长按语义） */
    onLongClick: (() -> Unit)? = null,
) {
    Text(
        text = stringResource(
            if (showTranslation) R.string.detail_chip_original else R.string.detail_chip_translate,
        ),
        color = if (showTranslation) PikuColors.textPrimary else PikuColors.textFaint,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                },
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
