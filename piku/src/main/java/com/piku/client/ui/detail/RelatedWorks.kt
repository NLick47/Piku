package com.piku.client.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.piku.client.ui.common.isAnimatedImage
import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.ui.theme.OverlayScrimHeavy
import com.piku.client.ui.theme.PikuColors

private val COLUMN_WIDTH = 160.dp
private val GAP = 10.dp
internal const val CARD_TEXT_HEIGHT = 60

@Composable
internal fun RelatedWorksSection(
    works: List<Work>,
    onClick: (Long, Long, String) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.detail_related_works_count, works.size),
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth / COLUMN_WIDTH).toInt().coerceIn(2, 4)
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                works.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        row.forEach { work ->
                            RelatedWorkCard(
                                work = work,
                                onClick = onClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedWorkCard(
    work: Work,
    onClick: (Long, Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .shadow(elevation = 3.dp, shape = shape)
            .background(PikuColors.surface)
            .border(
                BorderStroke(0.5.dp, PikuColors.border),
                shape,
            )
            .clickable { onClick(work.authorId, work.id, work.thumbnailUrl) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(PikuColors.surfaceSoft),
        ) {
            AsyncImage(
                model = work.thumbnailUrl,
                contentDescription = work.title,
                colorFilter = PikuColors.tameWhiteFilter,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (isAnimatedImage(work.thumbnailUrl)) {
                Text(
                    // 格式名，各语言写法一致，不走 i18n
                    text = "GIF",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OverlayScrimHeavy)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (work.imageCount > 1) {
                Text(
                    text = "${work.imageCount}",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OverlayScrimHeavy)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(
            Modifier
                .padding(horizontal = 8.dp, vertical = 7.dp)
                .height(CARD_TEXT_HEIGHT.dp),
        ) {
            if (work.title.isNotBlank()) {
                Text(
                    text = work.title,
                    color = PikuColors.textPrimary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                text = work.authorName,
                color = PikuColors.textSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
