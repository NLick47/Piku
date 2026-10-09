package com.piku.client.ui.search

/** 搜索页待机态：我的标签 / 搜索历史 / 热门标签墙 */


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.source.SourceTrendingTag
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.GlassBarBgDark
import com.piku.client.ui.theme.GlassBarBgLight
import com.piku.client.ui.theme.OnAccentDark
import com.piku.client.ui.theme.SoftBorderDark
import com.piku.client.ui.theme.SoftBorderLight
import com.piku.client.ui.theme.WorkCardPlaceholderDark
import kotlin.math.roundToInt

/** 热门标签格子：目标宽度与高度、格子间距（三列排布按屏宽折算列数） */
private const val TRENDING_CELL_DP = 120
private const val TRENDING_GAP_DP = 8

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdleContent(
    history: List<String>,
    customTags: List<String>,
    trending: List<SourceTrendingTag>,
    pluginHint: Boolean,
    onSelect: (String) -> Unit,
    onSelectCustomTag: (String) -> Unit,
    onSelectTrending: (String) -> Unit,
    onManageTags: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    dark: Boolean,
) {
    val title = PikuColors.textSecondary
    // 空态/加载提示用次级字：faint 的暗色值(#5C5852)在近黑页面上只有 2.4:1，读不出来
    val hint = PikuColors.textSecondary
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
    ) {
        MyTagsRow(
            tags = customTags,
            activeTag = null,
            onSelect = onSelectCustomTag,
            onManage = onManageTags,
            dark = dark,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = title,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.search_recent),
                color = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = title,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.search_clear),
                        color = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.search_history_empty),
                color = hint,
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { keyword ->
                    SearchKeywordChip(
                        keyword = keyword,
                        onClick = { onSelect(keyword) },
                        onDelete = { onRemoveHistory(keyword) },
                        dark = dark,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        // 热门标签墙：代表作缩略图两列铺开，点按即搜（poipiku 与插件源同款渲染）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Whatshot,
                contentDescription = null,
                tint = title,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.search_hot_tags),
                color = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (trending.isEmpty()) {
            Text(
                text = stringResource(R.string.search_hot_tags_empty),
                color = hint,
                fontSize = 12.sp,
            )
        } else {
            TrendingGrid(tags = trending, onSelect = onSelectTrending)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(if (pluginHint) R.string.search_hint_pixiv else R.string.search_hint),
            color = hint,
            fontSize = 11.sp,
        )
    }
}

/** 热门标签墙：等宽等高的小格子铺开，图整张 Fit 居中含在格子里（不裁不切），点按直接检索该标签 */
@Composable
private fun TrendingGrid(
    tags: List<SourceTrendingTag>,
    onSelect: (String) -> Unit,
) {
    val columns = trendingColumns(LocalConfiguration.current.screenWidthDp)
    Column(verticalArrangement = Arrangement.spacedBy(TRENDING_GAP_DP.dp)) {
        tags.chunked(columns).forEach { rowTags ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TRENDING_GAP_DP.dp),
            ) {
                rowTags.forEach { tag ->
                    TrendingCard(
                        tag = tag,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 末行不足一列：补空位，格子宽度才和上面几行一致
                repeat(columns - rowTags.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 列数按屏宽折算：扣掉两侧 20dp 内边距后一格目标宽约 120dp，手机 3 列，平板跟着加列 */
internal fun trendingColumns(screenWidthDp: Int): Int =
    ((screenWidthDp - 40) / 120f).roundToInt().coerceAtLeast(3)

@Composable
private fun TrendingCard(
    tag: SourceTrendingTag,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(TRENDING_CELL_DP.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = { onSelect(tag.name) }),
    ) {
        if (tag.thumbnailUrl.isBlank()) {
            // 没有封面的标签照样进墙：中性占位而不是把它筛掉
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (LocalDarkTheme.current) WorkCardPlaceholderDark else PikuColors.surfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Label,
                    contentDescription = tag.translatedName ?: tag.name,
                    tint = PikuColors.textSecondary,
                    modifier = Modifier.size(28.dp),
                )
            }
        } else {
            AsyncImage(
                model = tag.thumbnailUrl,
                contentDescription = tag.translatedName ?: tag.name,
                colorFilter = PikuColors.tameWhiteFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (LocalDarkTheme.current) WorkCardPlaceholderDark else PikuColors.surfaceSoft),
                contentScale = ContentScale.Fit,
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )
        Text(
            text = "#${tag.name}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = 7.dp),
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyTagsRow(
    tags: List<String>,
    activeTag: String?,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = PikuColors.textSecondary
    val hint = PikuColors.textSecondary
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Label,
                contentDescription = null,
                tint = title,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.menu_my_tags),
                color = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onManage)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_manage_tags),
                    color = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.my_tags_empty),
                color = hint,
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    TagPill(
                        text = "#$tag",
                        active = tag == activeTag,
                        onClick = { onSelect(tag) },
                        dark = dark,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    active -> PikuColors.accent
                    else -> if (dark) GlassBarBgDark else GlassBarBgLight
                },
            )
            .border(
                BorderStroke(
                    0.5.dp,
                    when {
                        active -> PikuColors.accent
                        else -> if (dark) SoftBorderDark else SoftBorderLight
                    },
                ),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = when {
                active -> if (dark) OnAccentDark else Color.White
                else -> PikuColors.chipLabel
            },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

@Composable
private fun SearchKeywordChip(
    keyword: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (dark) GlassBarBgDark else GlassBarBgLight)
            .border(
                BorderStroke(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyword,
            color = PikuColors.chipLabel,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.search_delete),
                tint = PikuColors.textSecondary,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
