package com.piku.client.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.SegmentedTrackDark
import com.piku.client.ui.theme.SegmentedTrackLight

private val TrackShape = RoundedCornerShape(50)
private val TrackPadding = 3.dp
private val ItemGap = 2.dp
private val TrackHeight = 38.dp

/**
 * 等分胶囊分段控件：玻璃轨道 + 滑动的 accent 指示器。
 * 供需要「横向切换一组互斥筛选项」的页面共用（搜索页 tab、浏览记录时间范围等）。
 * 需要父容器给出有限宽度，不支持放在横向滚动容器里。
 */
@Composable
fun PikuSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (labels.isEmpty()) return
    val index = selectedIndex.coerceIn(0, labels.lastIndex)
    val reduced = rememberReducedMotion()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .clip(TrackShape)
            .background(if (LocalDarkTheme.current) SegmentedTrackDark else SegmentedTrackLight)
            .border(BorderStroke(0.5.dp, PikuColors.border), TrackShape),
    ) {
        val itemWidth =
            (maxWidth - TrackPadding * 2 - ItemGap * (labels.size - 1)) / labels.size
        val indicatorX by animateDpAsState(
            targetValue = TrackPadding + (itemWidth + ItemGap) * index.toFloat(),
            animationSpec = if (reduced) {
                tween(0)
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            },
            label = "segmentIndicatorX",
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorX, y = TrackPadding)
                .width(itemWidth)
                .height(TrackHeight - TrackPadding * 2)
                .clip(TrackShape)
                .background(PikuColors.accent),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TrackPadding),
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            labels.forEachIndexed { i, label ->
                SegmentItem(
                    text = label,
                    selected = i == index,
                    dense = labels.size >= 5,
                    enabled = enabled,
                    onClick = { onSelect(i) },
                    animationMillis = motionDuration(reduced, 180),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SegmentItem(
    text: String,
    selected: Boolean,
    dense: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    animationMillis: Int,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) PikuColors.surface else PikuColors.textSecondary,
        animationSpec = tween(durationMillis = animationMillis),
        label = "segmentTextColor",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(TrackShape)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (dense) 12.sp else 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
