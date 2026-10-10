package com.piku.client.ui.source

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.FavoriteHeartBurst
import com.piku.client.ui.common.WorkAiBadge
import com.piku.client.ui.common.feedThumbUrl
import com.piku.client.ui.navigation.sharedWorkBounds
import com.piku.client.ui.navigation.workSharedKey
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout
import com.piku.client.ui.theme.SoftBorderLight
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardBorderDark
import com.piku.client.ui.theme.WorkCardPlaceholderDark

// 卡片比例上下界 长图不让单卡吃掉整屏 宽图不至于压成一条
private const val MIN_CARD_ASPECT = 0.56f
private const val MAX_CARD_ASPECT = 1.8f

// 原作宽高换算卡片宽高比 缺尺寸退回方图
internal fun cardAspectRatio(work: Work): Float {
    if (work.thumbWidth <= 0 || work.thumbHeight <= 0) return 1f
    return (work.thumbWidth.toFloat() / work.thumbHeight.toFloat())
        .coerceIn(MIN_CARD_ASPECT, MAX_CARD_ASPECT)
}

/** 作品真实宽高比 缺尺寸返回 null 详情图区拿它摆首帧高度 不夹卡片那套上下界 */
internal fun Work.thumbAspectOrNull(): Float? =
    if (thumbWidth > 0 && thumbHeight > 0) thumbWidth.toFloat() / thumbHeight else null

// 按原图比例排版的卡片 给带尺寸的源用 不裁方 外观同 WorkCard 但无页数角标
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProportionalWorkCard(
    work: Work,
    onToggleFavorite: (Work) -> Unit,
    onClick: (Work) -> Unit,
    dark: Boolean,
    /** 作者主页上每张卡片都挂着同一个作者，那一行是纯噪声；关掉换成页数 */
    showAuthor: Boolean = true,
) {
    val shape = RoundedCornerShape(PikuLayout.CardCorner)
    var heartVisible by remember { mutableStateOf(false) }

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
                // 双击收藏：心形爆裂给即时反馈
                onDoubleClick = {
                    heartVisible = true
                    onToggleFavorite(work)
                },
            ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = feedThumbUrl(work.thumbnailUrl),
                contentDescription = work.title,
                colorFilter = PikuColors.tameWhiteFilter,
                modifier = Modifier
                    .sharedWorkBounds(workSharedKey(work.authorId, work.id))
                    .fillMaxWidth()
                    .aspectRatio(cardAspectRatio(work))
                    .background(if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
                contentScale = ContentScale.Crop,
            )
            if (work.ai) {
                WorkAiBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
            FavoriteHeartBurst(
                visible = heartVisible,
                onFinished = { heartVisible = false },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = PikuLayout.CardPadding,
                    end = PikuLayout.CardPadding,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
        ) {
            Text(
                text = work.title,
                color = PikuColors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (showAuthor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = work.authorAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = work.authorName,
                        color = PikuColors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.home_image_count, work.imageCount),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
