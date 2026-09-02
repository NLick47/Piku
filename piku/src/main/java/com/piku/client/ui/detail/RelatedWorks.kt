package com.piku.client.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.PikuColors

internal const val COLS = 2
internal const val ROWS = 2
internal const val CARD_IMAGE_HEIGHT = 220
internal const val CARD_TEXT_HEIGHT = 60
internal const val CARD_HEIGHT = CARD_IMAGE_HEIGHT + CARD_TEXT_HEIGHT

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RelatedWorksSection(
    works: List<Work>,
    dark: Boolean,
    onClick: (Long, Long, String) -> Unit,
) {
    val perPage = COLS * ROWS
    val pages = (works.size + perPage - 1) / perPage
    val pagerState = rememberPagerState(pageCount = { pages })
    Column {
        Text(
            text = stringResource(R.string.detail_related_works_count, works.size),
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Box {
            HorizontalPager(
                state = pagerState,
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RelatedWorkRow(works, page * perPage, dark, onClick)
                    RelatedWorkRow(works, page * perPage + COLS, dark, onClick)
                }
            }
            if (pages > 1) {
                Text(
                    text = stringResource(
                        R.string.detail_image_index,
                        pagerState.currentPage + 1,
                        pages,
                    ),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (pages > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages) { page ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    page == pagerState.currentPage && dark -> ControlAccentDark
                                    page == pagerState.currentPage -> AccentDark
                                    dark -> LoginTextFaintDark
                                    else -> LoginTextFaintLight
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedWorkRow(
    works: List<Work>,
    startIndex: Int,
    dark: Boolean,
    onClick: (Long, Long, String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (startIndex until startIndex + COLS).forEach { index ->
            if (index < works.size) {
                RelatedWorkCard(
                    work = works[index],
                    dark = dark,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(
                    Modifier
                        .weight(1f)
                        .height(CARD_HEIGHT.dp),
                )
            }
        }
    }
}

@Composable
private fun RelatedWorkCard(
    work: Work,
    dark: Boolean,
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
                .height(CARD_IMAGE_HEIGHT.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(PikuColors.surfaceSoft),
        ) {
            // 底层：同图铺满格子 + 放大 + 模糊 + 压暗，把固定尺寸的留白变成毛玻璃衬底
            AsyncImage(
                model = work.thumbnailUrl,
                contentDescription = null,
                colorFilter = PikuColors.tameWhiteFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.15f)
                    .blur(24.dp),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (dark) Color(0x59000000) else Color(0x40000000)),
            )
            // 顶层：原图完整呈现，不裁剪
            AsyncImage(
                model = work.thumbnailUrl,
                contentDescription = work.title,
                colorFilter = PikuColors.tameWhiteFilter,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
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
                        .background(Color(0x99000000))
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
                        .background(Color(0x99000000))
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
