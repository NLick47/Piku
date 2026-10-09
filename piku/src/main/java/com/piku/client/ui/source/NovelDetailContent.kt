package com.piku.client.ui.source

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import com.piku.client.domain.model.WorkStats
import com.piku.client.ui.common.feedThumbUrl
import com.piku.client.ui.detail.HeadlineTranslateChip
import com.piku.client.ui.navigation.sharedWorkBounds
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout
import com.piku.client.ui.theme.WorkCardPlaceholderDark

@Composable
internal fun NovelDetailContent(
    work: Work,
    detail: WorkDetail,
    stats: WorkStats?,
    dark: Boolean,
    language: AppLanguage,
    topInset: Dp,
    /** 由页面持有的滚动状态：回顶悬浮按钮据此显隐，与插画分支共用同一个 */
    scrollState: ScrollState = rememberScrollState(),
    /** 封面共享元素 key：与 NovelWorkCard 封面配对做 hero 形变；空串 = 不参与过渡 */
    sharedKey: String = "",
    customTags: Set<String>,
    novelBodyLoading: Boolean,
    onReadClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onToggleCustomTag: (String) -> Unit,
    onAuthorClick: () -> Unit,
    isFavorite: Boolean,
    cloudSynced: Boolean,
    onBookmarkToggle: () -> Unit,
    onBookmarkLongPress: () -> Unit,
    followed: Boolean,
    showFollow: Boolean,
    followSending: Boolean,
    followQuiet: Boolean = false,
    onFollowClick: () -> Unit,
    showTranslation: Boolean,
    translating: Boolean,
    onToggleTranslation: () -> Unit,
    onRetranslate: () -> Unit,
    showTranslatedTags: Boolean,
    tagsTranslating: Boolean,
    onToggleTagsTranslation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(
                start = PikuLayout.ScreenInset,
                end = PikuLayout.ScreenInset,
                bottom = 40.dp,
            ),
    ) {
        Spacer(Modifier.height(topInset))
        AsyncImage(
            model = feedThumbUrl(work.thumbnailUrl),
            contentDescription = detail.title,
            colorFilter = PikuColors.tameWhiteFilter,
            modifier = Modifier
                .sharedWorkBounds(sharedKey)
                .width(140.dp)
                .aspectRatio(NOVEL_DETAIL_COVER_ASPECT)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(PikuLayout.CardCorner))
                .background(if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = detail.translated?.title
                    ?.takeIf { showTranslation && it.isNotBlank() }
                    ?: detail.title,
                color = PikuColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp,
                modifier = Modifier.weight(1f),
            )
            // 恒显：没有译文时它是「点了去翻」的入口，与插画详情标题行同款
            HeadlineTranslateChip(
                shown = showTranslation,
                translating = translating,
                onClick = onToggleTranslation,
                onLongClick = onRetranslate,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        AuthorLine(
            detail = detail,
            stats = stats,
            onAuthorClick = onAuthorClick,
            dark = dark,
            followed = followed,
            showFollow = showFollow,
            followSending = followSending,
            followQuiet = followQuiet,
            onFollowClick = onFollowClick,
        )
        Spacer(Modifier.height(10.dp))
        // 收藏开关与作品详情页同一颗星标胶囊，且恒在——数据缺失时只藏两侧纯展示格
        NovelStatsRow(
            stats = stats,
            work = work,
            language = language,
            dark = dark,
            isFavorite = isFavorite,
            cloudSynced = cloudSynced,
            onBookmarkToggle = onBookmarkToggle,
            onBookmarkLongPress = onBookmarkLongPress,
        )
        MetaLine(stats = stats)
        Spacer(Modifier.height(14.dp))
        ReadButton(
            loading = novelBodyLoading,
            dark = dark,
            onClick = onReadClick,
            modifier = Modifier.fillMaxWidth(),
        )
        DescriptionBlock(detail = detail, dark = dark, showTranslation = showTranslation)
        TagsBlock(
            detail = detail,
            customTags = customTags,
            dark = dark,
            showTranslation = showTranslatedTags,
            tagsTranslating = tagsTranslating,
            onTagClick = onTagClick,
            onToggleCustomTag = onToggleCustomTag,
            onToggleTranslation = onToggleTagsTranslation,
        )
    }
}

private const val NOVEL_DETAIL_COVER_ASPECT = 0.75f

/** 浏览 / 篇幅两格纯展示，第三格是收藏胶囊；小说没有点赞，篇幅顶上它的位置 */
@Composable
private fun NovelStatsRow(
    stats: WorkStats?,
    work: Work,
    language: AppLanguage,
    dark: Boolean,
    isFavorite: Boolean,
    cloudSynced: Boolean,
    onBookmarkToggle: () -> Unit,
    onBookmarkLongPress: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (stats?.hasCounts == true) {
            StatCell(
                icon = Icons.Outlined.Visibility,
                value = compactCount(stats.views, language),
                label = stringResource(R.string.pixiv_stat_views),
                modifier = Modifier.weight(1f),
            )
        }
        if (work.textLength > 0) {
            Text(
                text = stringResource(R.string.pixiv_novel_length, work.textLength),
                color = PikuColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BookmarkStatButton(
                count = stats?.bookmarks ?: 0,
                language = language,
                favorited = isFavorite,
                cloudSynced = cloudSynced,
                dark = dark,
                onToggle = onBookmarkToggle,
                onLongPress = onBookmarkLongPress,
            )
        }
    }
}

@Composable
private fun ReadButton(
    loading: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(PikuColors.controlAccent)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (loading) R.string.pixiv_novel_loading else R.string.pixiv_novel_read,
            ),
            color = PikuColors.surface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
