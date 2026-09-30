package com.piku.client.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.detail.TranslateChip
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagFlow(
    tags: List<String>,
    customTags: Set<String>,
    dark: Boolean,
    onTagClick: (String) -> Unit,
    onToggleCustomTag: (String) -> Unit,
    /**
     * 实际渲染的文字（译文态时为译文），与 [tags] 下标一一对应。
     * 点击筛选/收藏仍使用 [tags] 的原文，否则按译文去搜会搜不到。
     */
    displayTags: List<String> = tags,
    /** 尾部附加内容（原/译 chip） */
    trailing: (@Composable () -> Unit)? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEachIndexed { index, tag ->
            val label = displayTags.getOrElse(index) { tag }
            val added = tag in customTags
            val shape = RoundedCornerShape(12.dp)
            // 加入/移除时的图标弹跳
            val tagIconScale = remember { Animatable(1f) }
            // 首次组合不播动画：否则每次进详情页，所有标签的 + 号会同时弹跳一次
            var skipInitialBounce by remember { mutableStateOf(true) }
            LaunchedEffect(added) {
                if (skipInitialBounce) {
                    skipInitialBounce = false
                    return@LaunchedEffect
                }
                tagIconScale.snapTo(0.5f)
                tagIconScale.animateTo(1.25f, tween(140, easing = LinearOutSlowInEasing))
                tagIconScale.animateTo(1f, tween(100))
            }
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(PikuColors.surfaceSoft)
                    .border(
                        BorderStroke(
                            0.5.dp,
                            if (added) {
                                PikuColors.accent
                            } else {
                                PikuColors.border
                            },
                        ),
                        shape,
                    )
                    .clickable { onTagClick(tag) }
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#$label",
                    color = if (dark) LoginTextSecondaryDark else AccentDark,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Spacer(Modifier.width(2.dp))
                // 加入/移出个人标签：已添加显示 ✓，未添加显示 +
                // 外层 32dp 是点击热区（明显大于视觉尺寸，密集排版里更容易按中），
                // 内层 20dp 才是看到的圆底，视觉密度保持不变
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onToggleCustomTag(tag) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (added) {
                                    if (dark) {
                                        LoginTextPrimaryDark.copy(alpha = 0.16f)
                                    } else {
                                        AccentDark.copy(alpha = 0.12f)
                                    }
                                } else {
                                    Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (added) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = stringResource(
                                if (added) R.string.detail_tag_remove_from_my_tags
                                else R.string.detail_tag_add_to_my_tags,
                            ),
                            tint = if (added) {
                                PikuColors.accent
                            } else {
                                PikuColors.textSecondary
                            },
                            modifier = Modifier
                                .size(12.dp)
                                .graphicsLayer {
                                    scaleX = tagIconScale.value
                                    scaleY = tagIconScale.value
                                },
                        )
                    }
                }
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.padding(top = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}

/**
 * 标签当前是否显示译文：译文标签与原文一一对应，数量对不上（旧译文/部分失败）就整体回原文。
 * 两个源的详情页共用，避免各写一份判定。
 */
internal fun tagsTranslationShown(
    showTranslation: Boolean,
    tags: List<String>,
    translatedTags: List<String>?,
): Boolean = showTranslation && translatedTags?.size == tags.size

/**
 * 标签区那颗「原/译」（含翻译中转圈）：标签非空即出——
 * 有译文切原/译，无译文点了去翻。
 *
 * 芯片表达的是「当前实际显示的是译文」，而不是「想要的显示态」：
 * 跟随后者会让没翻出的作品挂一颗「原」，点了却无译文可回。
 * 两个源共用同一份实现（poipiku 那版为准），容器各自选：poipiku 走 TagFlow 的
 * trailing（支持加个人标签），pixiv 走 FlowRow 末项（pixiv 加不了个人标签）。
 */
@Composable
internal fun TagsTranslateChip(
    shown: Boolean,
    translating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        TranslateChip(
            showTranslation = shown,
            onClick = onClick,
            modifier = Modifier.alpha(if (translating) 0.4f else 1f),
        )
        if (translating) {
            Spacer(Modifier.width(4.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = PikuColors.textSecondary,
            )
        }
    }
}

/**
 * 标题行那颗「原/译」——详情页正文（标题/简介/作者简介）的唯一翻译入口：
 * 短按 = 有译文就切原/译，没译文就现翻；翻译中 chip 弱化并在右侧转圈；
 * 长按 = 换模型重翻（原顶栏图标的长按功能，随图标一并挪到这里）。
 *
 * 与 [TagsTranslateChip] 共用 [TranslateChip] 的文案与配色，语义一致：
 * 显示「译」= 点了去翻/切到译文，显示「原」= 当前是译文。
 */
@Composable
internal fun HeadlineTranslateChip(
    shown: Boolean,
    translating: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        TranslateChip(
            showTranslation = shown,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.alpha(if (translating) 0.4f else 1f),
        )
        if (translating) {
            Spacer(Modifier.width(4.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = PikuColors.textSecondary,
            )
        }
    }
}
