package com.piku.client.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.ErrorRedDark
import com.piku.client.ui.theme.ErrorRedLight
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
import com.piku.client.ui.theme.FollowTintDark
import com.piku.client.ui.theme.FollowTintLight
import com.piku.client.ui.theme.GlassBarBgDark
import com.piku.client.ui.theme.GlassBarBgLight
import com.piku.client.ui.theme.GuideHintBgDark
import com.piku.client.ui.theme.GuideHintBgLight
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.ShadowAmbient
import com.piku.client.ui.theme.ShadowSpot
import com.piku.client.ui.theme.SoftBorderDark
import com.piku.client.ui.theme.SoftBorderLight
import com.piku.client.ui.theme.StarDark
import com.piku.client.ui.theme.StarLight
import com.piku.client.ui.theme.StarTintDark
import com.piku.client.ui.theme.StarTintLight
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GUIDE_HINT_MILLIS = 6_000L

@Composable
internal fun DetailBottomBar(
    isFavorite: Boolean,
    reactionCount: Int,
    reacted: Boolean,
    followed: Boolean,
    onFavoriteClick: () -> Unit,
    onFavoriteLongPress: () -> Unit,
    onReactionClick: () -> Unit,
    onFollowClick: () -> Unit,
    dark: Boolean,
    onCopyLink: () -> Unit,
    onCopyDescription: () -> Unit,
    onOpenBrowser: () -> Unit,
    /** 配置了可用翻译模型时传入，更多菜单里会出现"换模型重翻" */
    onOpenModelPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pill = RoundedCornerShape(24.dp)
    // 收藏时的星标弹跳
    val favoriteScale = remember { Animatable(1f) }
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            favoriteScale.snapTo(1.35f)
            favoriteScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(10.dp, pill, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                .clip(pill)
                .background(if (dark) GlassBarBgDark else GlassBarBgLight)
                .border(
                    BorderStroke(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight),
                    pill,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBarAction(
                onClick = onFavoriteClick,
                onLongPress = onFavoriteLongPress,
                active = isFavorite,
                activeTint = if (dark) StarTintDark else StarTintLight,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.detail_favorite),
                    tint = if (isFavorite) {
                        if (dark) StarDark else StarLight
                    } else {
                        PikuColors.textSecondary
                    },
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = favoriteScale.value
                            scaleY = favoriteScale.value
                        },
                )
            }
            DetailBarAction(
                onClick = onFollowClick,
                active = followed,
                activeTint = if (dark) FollowTintDark else FollowTintLight,
            ) {
                Icon(
                    imageVector = if (followed) Icons.Filled.Person else Icons.Outlined.PersonAdd,
                    contentDescription = stringResource(R.string.detail_follow),
                    tint = if (followed) {
                        if (dark) FollowDark else FollowLight
                    } else {
                        PikuColors.textSecondary
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            ReactionAction(
                count = reactionCount,
                reacted = reacted,
                dark = dark,
                onClick = onReactionClick,
            )
            Box {
                DetailBarAction(
                    onClick = { menuExpanded = true },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.detail_more),
                        tint = PikuColors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (menuExpanded) {
                    MoreMenuPopup(
                        dark = dark,
                        onDismiss = { menuExpanded = false },
                        onCopyLink = {
                            menuExpanded = false
                            onCopyLink()
                        },
                        onCopyDescription = {
                            menuExpanded = false
                            onCopyDescription()
                        },
                        onOpenBrowser = {
                            menuExpanded = false
                            onOpenBrowser()
                        },
                        onOpenModelPicker = onOpenModelPicker?.let { picker ->
                            {
                                menuExpanded = false
                                picker()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBarAction(
    onClick: () -> Unit,
    active: Boolean = false,
    activeTint: Color = Color.Transparent,
    onLongPress: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        label = "barActionPress",
    )
    val interaction = if (onLongPress != null) {
        // 长按手势：单击在抬手时立即触发（无延迟），按住超过阈值触发长按并吞掉单击
        Modifier.tapOrLongPress(
            onTap = onClick,
            onLongPress = onLongPress,
            onPressChanged = { pressed = it },
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = Modifier.size(44.dp),
    ) {
        Box(
            modifier = interaction
                .matchParentSize()
                .clip(CircleShape)
                .background(if (active) activeTint else Color.Transparent),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        ) {
            icon()
        }
    }
}

@Composable
private fun ReactionAction(
    count: Int,
    reacted: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val redTint = if (dark) ErrorRedDark else ErrorRedLight
    val bg by animateColorAsState(
        targetValue = if (reacted) {
            redTint.copy(alpha = if (dark) 0.22f else 0.14f)
        } else {
            Color.Transparent
        },
        label = "reactionPillBg",
    )
    Row(
        modifier = Modifier
            // 可撑大：固定 size 会把 44dp 当上限传给内容，数字会被挤没
            .defaultMinSize(minWidth = 44.dp)
            .height(36.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (reacted) {
                    Modifier.border(BorderStroke(0.5.dp, redTint.copy(alpha = 0.45f)), shape)
                } else {
                    Modifier
                },
            )
                .clickable(onClick = onClick)
                .padding(
                    // 心形字形自带留白，起点少给 2dp 两侧才均衡
                    start = if (reacted) 8.dp else 0.dp,
                    end = if (reacted) 10.dp else 0.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(R.string.detail_reaction_title),
            tint = if (reacted) redTint else PikuColors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatReactionCount(count),
                color = if (reacted) redTint else PikuColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** 反应数紧凑展示：≥1000 缩写为 k 形式，去掉多余的 .0 */
private fun formatReactionCount(count: Int): String {
    if (count < 1000) return count.toString()
    val base = count / 1000f
    val num = if (base >= 100f) {
        base.toInt().toString()
    } else {
        "%.1f".format(Locale.US, base).trimEnd('0').trimEnd('.')
    }
    return "${num}k"
}

/** 底部菜单一次性新手引导：首次进入详情页时在菜单上方短暂展示按钮含义 */
@Composable
internal fun BottomBarGuideHint(
    dark: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "guideHintAlpha",
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(GUIDE_HINT_MILLIS)
        visible = false
        delay(220)
        onDismiss()
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 72.dp)
            .graphicsLayer { this.alpha = alpha }
            .shadow(6.dp, shape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
            .clip(shape)
            .background(if (dark) GuideHintBgDark else GuideHintBgLight)
            .border(
                BorderStroke(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight),
                shape,
            )
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GuideHintItem(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (dark) StarDark else StarLight,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = stringResource(R.string.detail_favorite),
                dark = dark,
            )
            GuideHintItem(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = PikuColors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = stringResource(R.string.detail_follow),
                dark = dark,
            )
            GuideHintItem(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = PikuColors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = stringResource(R.string.detail_reaction),
                dark = dark,
            )
            GuideHintItem(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = null,
                        tint = PikuColors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = stringResource(R.string.detail_more),
                dark = dark,
            )
        }
    }
}

@Composable
private fun GuideHintItem(
    icon: @Composable () -> Unit,
    label: String,
    dark: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = PikuColors.textPrimary,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

/**
 * 单击 + 长按统一手势：
 * - 单击：抬手时立即触发（不像 combinedClickable 那样等长按超时）
 * - 长按：按住超过系统阈值触发，同时吞掉后续抬手事件，避免再触发单击
 */
private fun Modifier.tapOrLongPress(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPressChanged: (Boolean) -> Unit,
): Modifier = composed {
    val viewConfiguration = LocalViewConfiguration.current
    val gestureScope = rememberCoroutineScope()
    pointerInput(onTap, onLongPress) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPressChanged(true)
            var longPressFired = false
            val job = gestureScope.launch {
                delay(viewConfiguration.longPressTimeoutMillis)
                longPressFired = true
                down.consume()
                onLongPress()
            }
            val up = waitForUpOrCancellation()
            job.cancel()
            when {
                longPressFired -> if (up != null) up.consume()
                up != null -> {
                    up.consume()
                    onTap()
                }
            }
            onPressChanged(false)
        }
    }
}
