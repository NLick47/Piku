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
fun TextWorkBlurb(
    work: Work,
    onToggleFavorite: (Work) -> Unit,
    onClick: (Work) -> Unit,
    dark: Boolean,
    onLongClick: ((Work) -> Unit)? = null,
    /** 阅读进度；null 表示不显示（首页/搜索等没有"读到哪"语义的场景） */
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
            .fillMaxWidth()
            .shadow(
                elevation = if (dark) 0.dp else 4.dp,
                shape = shape,
                ambientColor = Color(0x14000000),
                spotColor = Color(0x24000000),
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
            .padding(
                start = PikuLayout.CardPadding + 2.dp,
                end = PikuLayout.CardPadding + 2.dp,
                top = PikuLayout.CardPadding - 2.dp,
                bottom = PikuLayout.CardPadding,
            ),
    ) {
        WorkBlurbHeader(
            work = work,
            heartVisible = heartVisible,
            heartScale = { heartScale.value },
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = work.title,
            color = PikuColors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (work.summary.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                // 源里的分段/换行在卡片上压成一段：留着空行会让三行里只装得下两行话加一串省略号
                text = remember(work.summary) { work.summary.replace(WHITESPACE_RUN, " ").trim() },
                color = PikuColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (work.tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = work.tags.take(BLURB_TAG_LIMIT).joinToString(" · "),
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = listOf(work.authorName, work.categoryName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (work.textLength > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.work_word_count, formatCount(work.textLength)),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            CardProgressLine(progress = progress, dark = dark, labelFontSize = 11.sp)
        }
    }
}

@Composable
private fun WorkBlurbHeader(
    work: Work,
    heartVisible: Boolean,
    heartScale: () -> Float,
) {
    val hasChips = work.inProgress || work.r18
    if (!hasChips && work.likes <= 0 && !heartVisible) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 小字轻量标记：不上胶囊底——两颗圆角块压在卡片首行太重，标签行已经给过底色
            if (work.inProgress) {
                Text(
                    text = stringResource(R.string.work_status_ongoing),
                    color = PikuColors.textFaint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            if (work.r18) {
                if (work.inProgress) Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.work_rating_adult),
                    color = AdultChipColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            if (heartVisible) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = PikuColors.accent,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            scaleX = heartScale()
                            scaleY = heartScale()
                        },
                )
            } else if (work.likes > 0) {
                // 热度是选文的第三只眼（简介说了是什么、标签说了给谁看，kudos 说了多少人认可）
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = PikuColors.textFaint,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = compactCount(work.likes),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(1.dp))
    }
}

internal fun formatCount(count: Int): String = String.format(java.util.Locale.US, "%,d", count)

internal fun compactCount(count: Int): String {
    fun scaled(divisor: Int, suffix: String): String {
        val whole = count / divisor
        val frac = (count % divisor) / (divisor / 10)
        return if (frac == 0) "$whole$suffix" else "$whole.${frac}$suffix"
    }
    return when {
        count >= 1_000_000 -> scaled(1_000_000, "M")
        count >= 1_000 -> scaled(1_000, "k")
        else -> count.toString()
    }
}

/** 简介里的连续空白（含换行）压成空格用 */
private val WHITESPACE_RUN = Regex("""\s+""")

/** 卡片上最多摆几条标签：整行放得下三条短标签，再多就挤掉了可读性 */
private const val BLURB_TAG_LIMIT = 3

private val AdultChipColor = Color(0xFFC0392B)
