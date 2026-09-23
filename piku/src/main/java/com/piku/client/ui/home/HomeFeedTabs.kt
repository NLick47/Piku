package com.piku.client.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout

@Composable
internal fun FeedTabRow(
    feedTab: FeedTab,
    onSelectFeedTab: (FeedTab) -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = PikuLayout.ScreenInset,
                end = PikuLayout.ScreenInset,
                top = 4.dp,
                bottom = 6.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedTabItem(
            text = stringResource(R.string.home_tab_hot),
            active = feedTab == FeedTab.HOT,
            onClick = { onSelectFeedTab(FeedTab.HOT) },
        )
        FeedTabItem(
            text = stringResource(R.string.home_tab_latest),
            active = feedTab == FeedTab.LATEST,
            onClick = { onSelectFeedTab(FeedTab.LATEST) },
        )
        FeedTabItem(
            text = stringResource(R.string.home_tab_follow),
            active = feedTab == FeedTab.FOLLOW,
            onClick = { onSelectFeedTab(FeedTab.FOLLOW) },
        )
        FeedTabItem(
            text = stringResource(R.string.home_tab_random),
            active = feedTab == FeedTab.RANDOM,
            onClick = { onSelectFeedTab(FeedTab.RANDOM) },
        )
    }
}

/** 选中态 = 字重 + 颜色 + 一条与文字同宽的短横线；横线宽度按标签实测，换语言也不会长短不一 */
@Composable
private fun FeedTabItem(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val textColor by animateColorAsState(
        targetValue = if (active) PikuColors.accent else PikuColors.textSecondary,
        animationSpec = tween(durationMillis = 200),
        label = "tabTextColor",
    )
    var labelWidthPx by remember { mutableIntStateOf(0) }
    val indicatorWidth by animateIntAsState(
        targetValue = if (active) labelWidthPx else 0,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabIndicatorWidth",
    )
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            onTextLayout = { labelWidthPx = it.size.width },
        )
        Spacer(Modifier.height(1.dp))
        Box(
            modifier = Modifier
                .height(TabIndicatorHeight)
                .width(with(density) { indicatorWidth.toDp() })
                .clip(RoundedCornerShape(TabIndicatorHeight / 2))
                .background(PikuColors.accent),
        )
    }
}

/** 与正文笔画同重量级：再粗就比 14sp 的字还重，比旧版（2dp × 18dp）更轻 */
private val TabIndicatorHeight = 1.5.dp
