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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.HomeFrameIcon
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailTopBar(
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    dark: Boolean,
    /** 滚过图区后淡入的作品标题（调用方已按原/译状态取好文案） */
    title: String = "",
    titleVisible: Boolean = false,
    /** 有译文时按钮高亮，点击变为整页原/译切换 */
    translationAvailable: Boolean = false,
    showTranslation: Boolean = false,
    translating: Boolean = false,
    /** 配置了 key 就常驻显示：未翻译时点击即翻 */
    canTranslate: Boolean = false,
    /** 顶栏翻译按钮：切换原文/译文 */
    onTranslateClick: () -> Unit = {},
    /** 顶栏翻译按钮：打开"换模型重翻"选择器 */
    onOpenModelPicker: () -> Unit = {},
) {
    // 用透明度做淡入淡出而不是 AnimatedVisibility：
    // 后者在 Row 作用域内会和 RowScope 的同名扩展产生接收者歧义
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible && title.isNotBlank()) 1f else 0f,
        animationSpec = tween(150),
        label = "topBarTitleAlpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
            .drawBehind {
                drawLine(
                    color = if (dark) PillBorderDark.copy(alpha = 0.6f) else PillBorderLight,
                    start = Offset(0f, size.height - 0.5.dp.toPx()),
                    end = Offset(size.width, size.height - 0.5.dp.toPx()),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.detail_back),
                tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onHomeClick) {
            Icon(
                imageVector = HomeFrameIcon,
                contentDescription = stringResource(R.string.detail_home),
                tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
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
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha },
            )
        }
        if (canTranslate || translationAvailable || translating) {
            // 纯图标按钮：翻译图标的表意已经足够清楚，不展开任何文字说明，
            // 状态只靠图标颜色区分（译文态蓝色 / 翻译中弱化 / 原文态常规色）。
            // 短按 = 原/译切换；长按 = 打开"换模型重翻"选择器（每次都可选，不记默认）。
            // 用 combinedClickable 让长按与短按各自触发、互不串扰。
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            // 按压反馈只作用在图标上（轻微缩放；暗色下额外提亮到纯白）。
            // 默认的涟漪会把深色顶栏上的整块圆底刷亮一下，很显脏
            val pressScale by animateFloatAsState(
                targetValue = if (pressed) 0.88f else 1f,
                label = "translateButtonPress",
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !translating,
                        onClick = onTranslateClick,
                        onLongClick = onOpenModelPicker,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = stringResource(
                        when {
                            translating -> R.string.detail_translating
                            showTranslation -> R.string.detail_show_original
                            else -> R.string.detail_show_translation
                        },
                    ),
                    tint = when {
                        translating -> if (dark) LoginTextFaintDark else LoginTextFaintLight
                        showTranslation -> Color(0xFF4FC3F7)
                        // 暗色下按下去提亮到纯白（LoginTextPrimaryDark #E8E4DE 再往上只有白色了）。
                        // 亮色主题下不改色：图标本来就是深色 #2C2C2C，往浅改反而像禁用态，
                        // 按压反馈交给下面的缩放即可
                        pressed && dark -> Color.White
                        else -> if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        },
                )
            }
        }
    }
}

/**
 * 单字段"原/译"切换 chip（方案 C）。
 * 用独立小按钮而不是长按手势：文本区域的长按已经归属"选中复制"，
 * 抢占会破坏复制描述这类刚需操作。
 */
@Composable
internal fun TranslateChip(
    showTranslation: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (dark) ControlAccentDark else AccentDark
    val off = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight
    Text(
        text = stringResource(
            if (showTranslation) R.string.detail_chip_original else R.string.detail_chip_translate,
        ),
        color = if (showTranslation) accent else off,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (showTranslation) accent.copy(alpha = if (dark) 0.20f else 0.12f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
