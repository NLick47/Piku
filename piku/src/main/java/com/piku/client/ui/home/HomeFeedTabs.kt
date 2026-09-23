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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout

/** 标签行总高（上下内边距 + 文字 + 选中横线）：浮层头部按它估高，给网格留出头部的位置 */
internal val GlassHeaderTabRowHeight = 43.dp

@Composable
internal fun FeedTabRow(
    feedTab: FeedTab,
    onSelectFeedTab: (FeedTab) -> Unit,
    dark: Boolean,
    /** 非空时在行尾显示分类入口（只有「最新」源需要分类） */
    categoryLabel: String? = null,
    categoryActive: Boolean = false,
    onCategoryClick: () -> Unit = {},
    /** 自定义背景下按图取色的标签用色，null 表示跟随主题 */
    tabColors: FeedTabColors? = null,
) {
    val colors = tabColors ?: FeedTabColors.default()
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
            colors = colors,
        )
        FeedTabItem(
            text = stringResource(R.string.home_tab_latest),
            active = feedTab == FeedTab.LATEST,
            onClick = { onSelectFeedTab(FeedTab.LATEST) },
            colors = colors,
        )
        FeedTabItem(
            text = stringResource(R.string.home_tab_follow),
            active = feedTab == FeedTab.FOLLOW,
            onClick = { onSelectFeedTab(FeedTab.FOLLOW) },
            colors = colors,
        )
        FeedTabItem(
            text = stringResource(R.string.home_tab_random),
            active = feedTab == FeedTab.RANDOM,
            onClick = { onSelectFeedTab(FeedTab.RANDOM) },
            colors = colors,
        )
        if (categoryLabel != null) {
            Spacer(Modifier.weight(1f))
            CategoryEntry(
                label = categoryLabel,
                active = categoryActive,
                onClick = onCategoryClick,
                colors = colors,
            )
        }
    }
}

/**
 * 分类入口与标签同排：同一套字号与选中横线，未筛选时只有一行淡字，
 * 不再用白底胶囊压在头图上；选中非「全部」时整段换强调色 + 横线，一眼看出正在筛选。
 */
@Composable
private fun CategoryEntry(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    colors: FeedTabColors,
) {
    val textColor by animateColorAsState(
        targetValue = if (active) colors.active else colors.inactive,
        animationSpec = tween(durationMillis = 200),
        label = "categoryTextColor",
    )
    var labelWidthPx by remember { mutableIntStateOf(0) }
    val indicatorWidth by animateIntAsState(
        targetValue = if (active) labelWidthPx else 0,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "categoryIndicatorWidth",
    )
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.onSizeChanged { labelWidthPx = it.width },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                style = LocalTextStyle.current.copy(shadow = tabTextShadow(colors.shadow)),
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.home_category_select),
                tint = textColor,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.height(1.dp))
        Box(
            modifier = Modifier
                .height(TabIndicatorHeight)
                .width(with(density) { indicatorWidth.toDp() })
                .clip(RoundedCornerShape(TabIndicatorHeight / 2))
                .background(colors.active),
        )
    }
}

/** 选中态 = 字重 + 颜色 + 一条与文字同宽的短横线；横线宽度按标签实测，换语言也不会长短不一 */
@Composable
private fun FeedTabItem(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    colors: FeedTabColors,
) {
    val textColor by animateColorAsState(
        targetValue = if (active) colors.active else colors.inactive,
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
            style = LocalTextStyle.current.copy(shadow = tabTextShadow(colors.shadow)),
        )
        Spacer(Modifier.height(1.dp))
        Box(
            modifier = Modifier
                .height(TabIndicatorHeight)
                .width(with(density) { indicatorWidth.toDp() })
                .clip(RoundedCornerShape(TabIndicatorHeight / 2))
                .background(colors.active),
        )
    }
}

/** 与正文笔画同重量级：再粗就比 14sp 的字还重，比旧版（2dp × 18dp）更轻 */
private val TabIndicatorHeight = 1.5.dp

internal const val TAB_LUMA_THRESHOLD = 0.5f

internal data class FeedTabColors(
    val inactive: Color,
    val active: Color,
    /** 文字软阴影：底图明暗不均（亮肤色、白蕾丝）时靠它保底，null 表示不加 */
    val shadow: Color? = null,
) {
    companion object {
        /** 未设自定义背景时的默认取色：跟随主题 */
        @Composable
        fun default(): FeedTabColors = FeedTabColors(
            inactive = PikuColors.textSecondary,
            active = PikuColors.accent,
        )
    }
}

/**
 * [bandLuma] 为 null 表示没量到（标签行不在图上/图读不出来），退回 [FeedTabColors.default] 跟随主题。
 */
internal fun feedTabColors(
    hasCustomBackground: Boolean,
    bandLuma: Float?,
): FeedTabColors? {
    if (!hasCustomBackground) return null
    val luma = bandLuma ?: return null
    val onLight = luma > TAB_LUMA_THRESHOLD
    return FeedTabColors(
        // 未选中：与底同侧但压低对比，选中：拉满对比；压在照片上时对比度的上限就是黑白，别客气
        inactive = if (onLight) Color(0xD92C2C2C) else Color(0xE6EDEAE4),
        active = if (onLight) Color(0xFF000000) else Color(0xFFFFFFFF),
        // 与文字反号的软阴影：底图局部突然变亮/变暗时仍有轮廓
        shadow = if (onLight) Color(0x80FFFFFF) else Color(0x80000000),
    )
}

/** 把 [FeedTabColors.shadow] 折成 Compose 文字阴影（null 返回 null） */
internal fun tabTextShadow(shadow: Color?): Shadow? =
    shadow?.let { Shadow(color = it, offset = Offset(0f, 2f), blurRadius = 8f) }
