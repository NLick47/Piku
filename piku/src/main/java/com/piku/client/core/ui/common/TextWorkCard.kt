package com.piku.client.core.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.model.Work
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.core.ui.theme.PikuLayout
import com.piku.client.core.ui.theme.SoftBorderLight
import com.piku.client.core.ui.theme.WorkCardBgDark
import com.piku.client.core.ui.theme.WorkCardBorderDark
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextWorkCard(
    work: Work,
    onToggleFavorite: (Work) -> Unit,
    onClick: (Work) -> Unit,
    dark: Boolean,
    onLongClick: ((Work) -> Unit)? = null,
    progress: CardProgress? = null,
) {
    val shape = RoundedCornerShape(PikuLayout.CardCorner)
    var heartVisible by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0f) }

    LaunchedEffect(heartVisible) {
        if (heartVisible) {
            heartScale.snapTo(0f)
            heartScale.animateTo(1.3f, tween(120, easing = LinearOutSlowInEasing))
            heartScale.animateTo(1f, tween(80, easing = LinearOutSlowInEasing))
            delay(180)
            heartVisible = false
        }
    }

    Column(
        modifier = Modifier
            .shadow(
                elevation = if (dark) 0.dp else 6.dp,
                shape = shape,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x33000000),
            )
            .clip(shape)
            .background(if (dark) WorkCardBgDark else Color(0xE6FFFFFF))
            .border(
                BorderStroke(0.5.dp, if (dark) WorkCardBorderDark else SoftBorderLight),
                shape,
            )
            .combinedClickable(
                onClick = { onClick(work) },
                onLongClick = onLongClick?.let { handler -> { handler(work) } },
                onDoubleClick = {
                    heartVisible = true
                    onToggleFavorite(work)
                },
            )
            .padding(PikuLayout.CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 状态与分级：连载中给强调色（选文时要留意），已完结给中性色
            if (work.inProgress) {
                WorkTextChip(
                    text = stringResource(R.string.work_status_ongoing),
                    color = PikuColors.controlAccent,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (work.r18) {
                WorkTextChip(
                    text = stringResource(R.string.work_rating_adult),
                    color = Color(0xFFC0392B),
                )
            }
            Spacer(Modifier.weight(1f))
            if (heartVisible) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = PikuColors.accent,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = heartScale.value
                            scaleY = heartScale.value
                        },
                )
            }
        }
        Spacer(Modifier.height(if (work.inProgress || work.r18) 8.dp else 0.dp))
        Text(
            text = work.title,
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = work.authorName,
            color = PikuColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 同人是"这作品是什么"的第一信息（AO3 卡片形态）：有就占一行
        if (work.categoryName.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = work.categoryName,
                color = PikuColors.textFaint,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (work.textLength > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.work_word_count, formatCount(work.textLength)),
                color = PikuColors.textFaint,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        if (progress != null) {
            Spacer(Modifier.height(7.dp))
            CardProgressLine(progress = progress, dark = dark)
        }
    }
}

@Composable
private fun WorkTextChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
