package com.piku.client.ui.source

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.piku.client.ui.detail.ImagePager
import com.piku.client.ui.detail.TranslateChip
import com.piku.client.ui.detail.TranslateField
import com.piku.client.ui.detail.linkify
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.OverlayScrimHeavy
import com.piku.client.ui.theme.PikuColors
import java.util.Locale

/** 信息区左右留白（与 poipiku 详情同一把尺子） */
private val PIXIV_CONTENT_PADDING = 20.dp

/** 相关作品网格：一格的目标宽度，可用宽度除以它就是列数（411dp 屏 → 3 列） */
private val RELATED_COLUMN_WIDTH = 112.dp
private val RELATED_GAP = 10.dp

@Composable
internal fun PixivDetailContent(
    detail: WorkDetail,
    stats: WorkStats?,
    dark: Boolean,
    language: AppLanguage,
    /** 由页面持有的滚动状态：顶栏据此决定标题是否淡入 */
    scrollState: ScrollState = rememberScrollState(),
    /** 顶部让位给浮起来的顶栏 */
    topInset: Dp = 12.dp,
    /** 来源页缩略图：首图到位前的低清打底 */
    sourceThumbnailUrl: String = "",
    onImageClick: (Int) -> Unit,
    onImageLongPress: (Int) -> Unit,
    onAuthorClick: () -> Unit,
    onTagClick: (String) -> Unit,
    hasImageModel: Boolean = false,
    imageTranslated: Boolean = false,
    imageTranslatingPage: Int? = null,
    translatedImages: Map<Int, Bitmap> = emptyMap(),
    onImageTranslateClick: ((Int) -> Unit)? = null,
    onPageChanged: ((Int) -> Unit)? = null,
    translationAvailable: Boolean = false,
    showTranslation: (TranslateField) -> Boolean = { false },
    onToggleField: (TranslateField) -> Unit = {},
    /** 底部相关作品；空列表时不渲染这一块 */
    related: List<Work> = emptyList(),
    onRelatedClick: (Work) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        // 图通栏：左右到边、顶到顶栏下面，不钻到状态栏底下
        Column(Modifier.padding(top = topInset)) {
            ImagePager(
                detail = detail,
                dark = dark,
                sourceThumbnailUrl = sourceThumbnailUrl,
                fullBleed = true,
                onImageClick = onImageClick,
                onImageLongPress = onImageLongPress,
                // pixiv 没有密码、小说与访问门，这几路回调留空
                onWorkClick = { _, _, _ -> },
                password = "",
                onPasswordChange = {},
                onPasswordSubmit = {},
                passwordLoading = false,
                onOpenNovelReader = {},
                hasImageModel = hasImageModel,
                imageTranslated = imageTranslated,
                imageTranslatingPage = imageTranslatingPage,
                translatedImages = translatedImages,
                onImageTranslateClick = onImageTranslateClick,
                onPageChanged = onPageChanged,
            )
        }
        Column(
            Modifier.padding(
                start = PIXIV_CONTENT_PADDING,
                end = PIXIV_CONTENT_PADDING,
                top = 14.dp,
                bottom = 96.dp,
            ),
        ) {
            OverviewCard(
                detail = detail,
                stats = stats,
                dark = dark,
                language = language,
                onAuthorClick = onAuthorClick,
                onTagClick = onTagClick,
                translationAvailable = translationAvailable,
                showTranslation = showTranslation,
                onToggleField = onToggleField,
            )
            RelatedRow(works = related, onClick = onRelatedClick)
        }
    }
}

/** 概览卡：标题、作者、简介、四格数据、元信息、标签 */
@Composable
private fun OverviewCard(
    detail: WorkDetail,
    stats: WorkStats?,
    dark: Boolean,
    language: AppLanguage,
    onAuthorClick: () -> Unit,
    onTagClick: (String) -> Unit,
    translationAvailable: Boolean,
    showTranslation: (TranslateField) -> Boolean,
    onToggleField: (TranslateField) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PikuColors.surface)
            .border(BorderStroke(0.5.dp, PikuColors.border), shape)
            .padding(14.dp),
    ) {
        TitleLine(
            detail = detail,
            translationAvailable = translationAvailable,
            showTranslation = showTranslation,
            onToggleField = onToggleField,
        )
        Spacer(Modifier.height(10.dp))
        AuthorLine(detail = detail, stats = stats, onAuthorClick = onAuthorClick)
        DescriptionBlock(
            detail = detail,
            dark = dark,
            translationAvailable = translationAvailable,
            showTranslation = showTranslation,
            onToggleField = onToggleField,
        )
        if (stats?.hasCounts == true) {
            Spacer(Modifier.height(12.dp))
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PikuColors.border),
            )
            Spacer(Modifier.height(10.dp))
            StatsRow(stats = stats, language = language)
        }
        MetaLine(stats = stats)
        TagsBlock(
            detail = detail,
            dark = dark,
            showTranslation = showTranslation,
            onTagClick = onTagClick,
        )
    }
}

@Composable
private fun TitleLine(
    detail: WorkDetail,
    translationAvailable: Boolean,
    showTranslation: (TranslateField) -> Boolean,
    onToggleField: (TranslateField) -> Unit,
) {
    if (detail.title.isBlank()) return
    val titleTranslated = showTranslation(TranslateField.TITLE)
    val titleText = detail.translated?.title
        ?.takeIf { titleTranslated && it.isNotBlank() }
        ?: detail.title
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = titleText,
            color = PikuColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
            modifier = Modifier.weight(1f),
        )
        if (translationAvailable && !detail.translated?.title.isNullOrBlank()) {
            TranslateChip(
                showTranslation = titleTranslated,
                onClick = { onToggleField(TranslateField.TITLE) },
                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun AuthorLine(detail: WorkDetail, stats: WorkStats?, onAuthorClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onAuthorClick)
            .padding(vertical = 4.dp),
    ) {
        AsyncImage(
            model = detail.authorAvatarUrl,
            contentDescription = detail.authorName,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PikuColors.surfaceMuted),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = detail.authorName,
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val account = stats?.authorAccount.orEmpty()
            if (account.isNotBlank()) {
                Text(
                    text = "@$account",
                    color = PikuColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 四格数据：浏览 / 点赞 / 收藏 / 评论 */
@Composable
private fun StatsRow(stats: WorkStats, language: AppLanguage) {
    Row(Modifier.fillMaxWidth()) {
        StatCell(
            icon = Icons.Outlined.Visibility,
            value = compactCount(stats.views, language),
            label = stringResource(R.string.pixiv_stat_views),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            icon = Icons.Outlined.FavoriteBorder,
            value = compactCount(stats.likes, language),
            label = stringResource(R.string.pixiv_stat_likes),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            icon = Icons.Outlined.BookmarkBorder,
            value = compactCount(stats.bookmarks, language),
            label = stringResource(R.string.pixiv_stat_bookmarks),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            icon = Icons.Outlined.ChatBubbleOutline,
            value = compactCount(stats.comments, language),
            label = stringResource(R.string.pixiv_stat_comments),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 一格计数：图标 + 数字，不写「浏览/点赞」这类小字——眼睛、心、书签、气泡已经自解释，
 * 写出来只是把一行撑成两行。[label] 只留给无障碍朗读。
 */
@Composable
private fun StatCell(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = PikuColors.textSecondary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = value,
            color = PikuColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 投稿时间 · 尺寸 · 张数；三项都没有时整行不出现 */
@Composable
private fun MetaLine(stats: WorkStats?) {
    val source = stats ?: return
    val posted = source.postedAt.takeIf { it.length >= 10 }?.substring(0, 10).orEmpty()
    val size = if (source.width > 0 && source.height > 0) {
        "${source.width}×${source.height}"
    } else {
        ""
    }
    val pages = if (source.pageCount > 1) stringResource(R.string.pixiv_meta_pages, source.pageCount) else ""
    val postedLabel = if (posted.isNotBlank()) stringResource(R.string.pixiv_meta_posted, posted) else ""
    val parts = listOf(postedLabel, size, pages).filter { it.isNotBlank() }
    if (parts.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text(
        text = parts.joinToString(" · "),
        color = PikuColors.textSecondary,
        fontSize = 11.sp,
    )
}

/**
 * 简介：超过三行折叠，展开/收起与译文 chip 同一行。
 * 标签的「原/译」也挂在这一行——标签收进卡片后，再单独占一行会和标签区割开。
 * 没有简介但标签有译文时这一行照样要出，所以简介 blank 不能整块 return。
 */
@Composable
private fun DescriptionBlock(
    detail: WorkDetail,
    dark: Boolean,
    translationAvailable: Boolean,
    showTranslation: (TranslateField) -> Boolean,
    onToggleField: (TranslateField) -> Unit,
) {
    val hasDescription = detail.description.isNotBlank()
    val tagsChipVisible = translationAvailable && !detail.translated?.tags.isNullOrEmpty()
    if (!hasDescription && !tagsChipVisible) return
    val descTranslated = showTranslation(TranslateField.DESCRIPTION)
    val text = detail.translated?.description
        ?.takeIf { descTranslated && it.isNotBlank() }
        ?: detail.description
    var expanded by remember { mutableStateOf(false) }
    // 折叠态才量溢出：展开后 hasVisualOverflow 恒为 false，会误判成"没有更多"
    var overflowing by remember { mutableStateOf(false) }
    val showMore = hasDescription && (overflowing || expanded)
    val chipVisible = translationAvailable && !detail.translated?.description.isNullOrBlank()
    if (hasDescription) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = linkify(text, dark) { _, _, _ -> },
            color = PikuColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) overflowing = result.hasVisualOverflow },
        )
    }
    if (!chipVisible && !tagsChipVisible && !showMore) return
    if (!hasDescription) Spacer(Modifier.height(10.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showMore) Arrangement.SpaceBetween else Arrangement.End,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (showMore) {
            Text(
                text = stringResource(
                    if (expanded) R.string.detail_show_less else R.string.detail_show_more,
                ),
                color = PikuColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
            )
        }
        if (chipVisible) {
            TranslateChip(
                showTranslation = descTranslated,
                onClick = { onToggleField(TranslateField.DESCRIPTION) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // 标签的「原/译」也挂在这一行：标签收进卡片后，再单独占一行会和标签区割开。
        // 两颗 chip 长得一样，前面加「标签」二字才分得清谁切谁。
        if (tagsChipVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.pixiv_tags_label),
                    color = PikuColors.textFaint,
                    fontSize = 10.sp,
                )
                TranslateChip(
                    showTranslation = showTranslation(TranslateField.TAGS),
                    onClick = { onToggleField(TranslateField.TAGS) },
                )
            }
        }
    }
}

/**
 * 标签：只读 chip，点一个去搜这个标签。
 * 不用 poipiku 那套带「+」的 TagFlow——pixiv 作品加不进个人标签，挂个点了没反应的按钮是噪音。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsBlock(
    detail: WorkDetail,
    dark: Boolean,
    showTranslation: (TranslateField) -> Boolean,
    onTagClick: (String) -> Unit,
) {
    if (detail.tags.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    val tagsTranslated = showTranslation(TranslateField.TAGS)
    // 译文标签与原文一一对应；点击始终用原文，否则搜不到
    val displayTags = detail.translated?.tags
        ?.takeIf { tagsTranslated && it.size == detail.tags.size }
        ?: detail.tags
    val shape = RoundedCornerShape(12.dp)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        detail.tags.forEachIndexed { index, tag ->
            Text(
                text = "#${displayTags.getOrElse(index) { tag }}",
                color = if (dark) LoginTextSecondaryDark else AccentDark,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(shape)
                    .background(PikuColors.surfaceSoft)
                    .border(BorderStroke(0.5.dp, PikuColors.border), shape)
                    .clickable { onTagClick(tag) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 底部相关作品：列数按可用宽度自适应的网格（手机 3 列、平板更宽就更多列），
 * 行数不设上限，跟着页面一起竖向滚——不套自己的滚动容器，也就没有嵌套滚动的
 * 高度冲突。条目数由接口一次给全（18 条），不分页，不会无限堆积。
 */
@Composable
private fun RelatedRow(works: List<Work>, onClick: (Work) -> Unit) {
    if (works.isEmpty()) return
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.pixiv_related_works),
        color = PikuColors.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(10.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / RELATED_COLUMN_WIDTH).toInt().coerceIn(2, 4)
        val rows = works.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(RELATED_GAP)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(RELATED_GAP)) {
                    row.forEach { work ->
                        RelatedCard(work = work, onClick = onClick, modifier = Modifier.weight(1f))
                    }
                    // 末行不满时补空位，卡片才不会被拉宽
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RelatedCard(work: Work, onClick: (Work) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(PikuColors.surface)
            .border(BorderStroke(0.5.dp, PikuColors.border), shape)
            .clickable { onClick(work) },
    ) {
        Box(
            Modifier
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
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                text = work.title,
                color = PikuColors.textPrimary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = work.authorName,
                color = PikuColors.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 计数缩写：中日按万/亿，英文按 K/M。
 * 固定用 US locale 格式化小数，避免系统语言把「21.8万」写成「21,8万」。
 */
internal fun compactCount(value: Int, language: AppLanguage): String {
    if (value < 10_000) return value.toString()
    return if (language == AppLanguage.EN) {
        when {
            value >= 1_000_000 -> "%.1fM".format(Locale.US, value / 1_000_000f)
            else -> "%.1fK".format(Locale.US, value / 1_000f)
        }
    } else {
        when {
            value >= 100_000_000 -> "%.1f亿".format(Locale.US, value / 100_000_000f)
            else -> "%.1f万".format(Locale.US, value / 10_000f)
        }
    }
}
