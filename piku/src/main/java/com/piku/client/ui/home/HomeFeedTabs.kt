package com.piku.client.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors

@Composable
internal fun FeedTabRow(
    feedTab: FeedTab,
    currentTag: String?,
    onSelectFeedTab: (FeedTab) -> Unit,
    onClearTag: () -> Unit,
    dark: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 250))
            .padding(top = 4.dp, bottom = 6.dp),
    ) {
        if (currentTag != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CurrentTagChip(
                    tag = currentTag,
                    onClear = onClearTag,
                    dark = dark,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedTabItem(
                text = stringResource(R.string.home_tab_hot),
                active = feedTab == FeedTab.HOT,
                onClick = { onSelectFeedTab(FeedTab.HOT) },
                dark = dark,
            )
            Spacer(Modifier.width(20.dp))
            FeedTabItem(
                text = stringResource(R.string.home_tab_latest),
                active = feedTab == FeedTab.LATEST,
                onClick = { onSelectFeedTab(FeedTab.LATEST) },
                dark = dark,
            )
            Spacer(Modifier.width(20.dp))
            FeedTabItem(
                text = stringResource(R.string.home_tab_follow),
                active = feedTab == FeedTab.FOLLOW,
                onClick = { onSelectFeedTab(FeedTab.FOLLOW) },
                dark = dark,
            )
            Spacer(Modifier.width(20.dp))
            FeedTabItem(
                text = stringResource(R.string.home_tab_random),
                active = feedTab == FeedTab.RANDOM,
                onClick = { onSelectFeedTab(FeedTab.RANDOM) },
                dark = dark,
            )
        }
    }
}

@Composable
private fun FeedTabItem(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val underlineWidth by animateDpAsState(
        targetValue = if (!active) 0.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabUnderline",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            active -> PikuColors.accent
            else -> if (dark) LoginTextSecondaryDark else Color(0xFF8A8A8A)
        },
        animationSpec = tween(durationMillis = 200),
        label = "tabTextColor",
    )
    val underlineColor by animateColorAsState(
        targetValue = PikuColors.accent,
        animationSpec = tween(durationMillis = 200),
        label = "tabUnderlineColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabScale",
    )
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(underlineWidth)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(underlineColor),
        )
    }
}

@Composable
private fun CurrentTagChip(
    tag: String,
    prefix: String = "#",
    onClear: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (dark) GlassCardBgDark else Color.White)
            .border(
                BorderStroke(0.5.dp, PikuColors.border),
                shape,
            )
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$prefix$tag",
            color = PikuColors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.home_tag_clear),
                tint = PikuColors.textFaint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
