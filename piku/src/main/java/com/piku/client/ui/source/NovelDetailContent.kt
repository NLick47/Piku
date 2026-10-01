package com.piku.client.ui.source

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
import androidx.compose.material.icons.outlined.FavoriteBorder
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
    customTags: Set<String>,
    novelBodyLoading: Boolean,
    onReadClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onToggleCustomTag: (String) -> Unit,
    onAuthorClick: () -> Unit,
    isFavorite: Boolean,
    onBookmarkToggle: () -> Unit,
    onBookmarkLongPress: () -> Unit,
    followed: Boolean,
    showFollow: Boolean,
    followSending: Boolean,
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
            .verticalScroll(rememberScrollState())
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
            isFavorite = isFavorite,
            dark = dark,
            followed = followed,
            showFollow = showFollow,
            followSending = followSending,
            onBookmarkToggle = onBookmarkToggle,
            onBookmarkLongPress = onBookmarkLongPress,
            onFollowClick = onFollowClick,
        )
        if (stats?.hasCounts == true || work.textLength > 0) {
            Spacer(Modifier.height(10.dp))
            NovelStatsRow(stats = stats, work = work, language = language)
        }
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

/** 浏览 / 收藏 / 篇幅；小说没有点赞，第三格给字数 */
@Composable
private fun NovelStatsRow(stats: WorkStats?, work: Work, language: AppLanguage) {
    val source = stats
    if (source == null && work.textLength <= 0) return
    Row(Modifier.fillMaxWidth()) {
        if (source != null && source.hasCounts) {
            StatCell(
                icon = Icons.Outlined.Visibility,
                value = compactCount(source.views, language),
                label = stringResource(R.string.pixiv_stat_views),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                icon = Icons.Outlined.FavoriteBorder,
                value = compactCount(source.bookmarks, language),
                label = stringResource(R.string.pixiv_stat_bookmarks),
                modifier = Modifier.weight(1f),
            )
            if (work.textLength <= 0) return@Row
        }
        Text(
            text = stringResource(R.string.pixiv_novel_length, work.textLength),
            color = PikuColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
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
