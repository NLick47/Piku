package com.piku.client.ui.search

/** 搜索页待机态：我的标签 / 搜索历史 / 热门标签瀑布流 */


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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.source.SourceTrendingTag
import com.piku.client.ui.source.ProportionalWorkCard
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.WorkCardPlaceholderDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdleContent(
    history: List<String>,
    popularTags: List<String>,
    customTags: List<String>,
    trending: List<SourceTrendingTag>,
    pluginHint: Boolean,
    onSelect: (String) -> Unit,
    onSelectCustomTag: (String) -> Unit,
    onManageTags: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    dark: Boolean,
) {
    val title = PikuColors.textSecondary
    val label = PikuColors.textFaint
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
                color = label,
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
        if (trending.isNotEmpty()) {
            // 插件源的热门标签墙：代表作缩略图一排可滑，点按即搜
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
            TrendingGrid(tags = trending, onSelect = onSelect)
        } else {
            Text(
                text = stringResource(R.string.search_hot_tags),
                color = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            if (popularTags.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hot_tags_empty),
                    color = label,
                    fontSize = 12.sp,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    popularTags.forEach { tag ->
                        TagPill(
                            text = "#$tag",
                            active = false,
                            onClick = { onSelect("#$tag") },
                            dark = dark,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(if (pluginHint) R.string.search_hint_pixiv else R.string.search_hint),
            color = label,
            fontSize = 11.sp,
        )
    }
}

/** 热门标签瀑布流：两列按原图比例排（数量不多，全部铺开不横滑），点按直接检索该标签 */
@Composable
private fun TrendingGrid(
    tags: List<SourceTrendingTag>,
    onSelect: (String) -> Unit,
) {
    val columns = remember(tags) { splitTrendingColumns(tags.take(12)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        columns.forEach { columnTags ->
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                columnTags.forEach { (rank, tag) ->
                    TrendingCard(rank = rank + 1, tag = tag, onSelect = onSelect)
                }
            }
        }
    }
}

/** 两列拆分：按估算高度贪心放入较矮的一列，长图不挤同侧（缺尺寸的按方图估） */
private fun splitTrendingColumns(
    tags: List<SourceTrendingTag>,
): List<List<Pair<Int, SourceTrendingTag>>> {
    var leftLoad = 0f
    var rightLoad = 0f
    val left = mutableListOf<Pair<Int, SourceTrendingTag>>()
    val right = mutableListOf<Pair<Int, SourceTrendingTag>>()
    tags.forEachIndexed { index, tag ->
        val load = 1f / trendingAspect(tag)
        if (leftLoad <= rightLoad) {
            left.add(index to tag)
            leftLoad += load
        } else {
            right.add(index to tag)
            rightLoad += load
        }
    }
    return listOf(left, right)
}

/** 与 ProportionalWorkCard 同款比例上下界，缺尺寸退回方图 */
private fun trendingAspect(tag: SourceTrendingTag): Float =
    if (tag.width <= 0 || tag.height <= 0) {
        1f
    } else {
        (tag.width.toFloat() / tag.height.toFloat()).coerceIn(0.56f, 1.8f)
    }

@Composable
private fun TrendingCard(
    rank: Int,
    tag: SourceTrendingTag,
    onSelect: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(trendingAspect(tag))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = { onSelect(tag.name) }),
    ) {
        AsyncImage(
            model = tag.thumbnailUrl,
            contentDescription = tag.translatedName ?: tag.name,
            colorFilter = PikuColors.tameWhiteFilter,
            modifier = Modifier
                .fillMaxSize()
                .background(if (LocalDarkTheme.current) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
            contentScale = ContentScale.Crop,
        )
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
            text = "$rank",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 6.dp),
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
    val label = PikuColors.textFaint
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
                color = label,
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
                    else -> if (dark) Color(0x40FFFFFF) else Color(0xE6FFFFFF)
                },
            )
            .border(
                BorderStroke(
                    0.5.dp,
                    when {
                        active -> PikuColors.accent
                        else -> PikuColors.border
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
                active -> if (dark) LoginBackgroundDark else Color.White
                else -> if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
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
            .background(if (dark) Color(0x40FFFFFF) else Color(0xE6FFFFFF))
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x47FFFFFF) else Color(0x66A09A92)),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyword,
            color = if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A),
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
                tint = if (dark) LoginTextSecondaryDark else LoginTextFaintLight,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
