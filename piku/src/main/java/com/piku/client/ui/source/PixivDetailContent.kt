package com.piku.client.ui.source

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
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
import com.piku.client.ui.detail.HeadlineTranslateChip
import com.piku.client.ui.detail.TagFlow
import com.piku.client.ui.detail.TagsTranslateChip
import com.piku.client.ui.detail.linkify
import com.piku.client.ui.detail.tagsTranslationShown
import com.piku.client.ui.navigation.sharedWorkBounds
import com.piku.client.ui.theme.GlassBarBgDark
import com.piku.client.ui.theme.GlassBarBgLight
import com.piku.client.ui.theme.OverlayScrimHeavy
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.ShadowAmbient
import com.piku.client.ui.theme.ShadowSpot
import com.piku.client.ui.theme.SoftBorderDark
import com.piku.client.ui.theme.SoftBorderLight
import com.piku.client.ui.theme.StarDark
import com.piku.client.ui.theme.StarLight
import com.piku.client.ui.theme.StarTintDark
import com.piku.client.ui.theme.StarTintLight
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
    /** 图区共享元素 key：与列表卡片配对做 hero 形变；空串 = 不参与过渡 */
    sharedKey: String = "",
    /** 来源页缩略图：首图到位前的低清打底 */
    sourceThumbnailUrl: String = "",
    onImageClick: (Int) -> Unit,
    onImageLongPress: (Int) -> Unit,
    /** 该页有图成功上屏：把 painter 交出去，看图器拿它当零延迟垫底 */
    onImageShown: ((Int, Painter) -> Unit)? = null,
    onAuthorClick: () -> Unit,
    onTagClick: (String) -> Unit,
    /** 已加入个人标签的标签名（PIXIV 那一份） */
    customTags: Set<String> = emptySet(),
    onToggleCustomTag: (String) -> Unit = {},
    hasImageModel: Boolean = false,
    imageTranslated: Boolean = false,
    imageTranslatingPage: Int? = null,
    translatedImages: Map<Int, Bitmap> = emptyMap(),
    onImageTranslateClick: ((Int) -> Unit)? = null,
    onPageChanged: ((Int) -> Unit)? = null,
    showTranslation: Boolean = false,
    translating: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    /** 长按 chip：换模型重翻 */
    onRetranslate: () -> Unit = {},
    /** 标签独立态：默认显示/懒翻译都由它驱动，与正文统一切换隔离 */
    showTranslatedTags: Boolean = false,
    tagsTranslating: Boolean = false,
    onToggleTagsTranslation: () -> Unit = {},
    /** 底部相关作品；空列表时不渲染这一块 */
    related: List<Work> = emptyList(),
    onRelatedClick: (Work) -> Unit = {},
    /** 收藏（本地）与关注：收纳进概览卡本体——关注在作者行，收藏在数据条的收藏格 */
    isFavorite: Boolean = false,
    /** 本地收藏已镜像到 pixiv 云端：星标角标 */
    cloudSynced: Boolean = false,
    followed: Boolean = false,
    showFollow: Boolean = false,
    followSending: Boolean = false,
    /** 当前关注是否为悄悄关注：胶囊文字旁带小锁 */
    followQuiet: Boolean = false,
    onBookmarkToggle: () -> Unit = {},
    /** 长按收藏格：打开收藏夹面板 */
    onBookmarkLongPress: () -> Unit = {},
    onFollowClick: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        // 图通栏：左右到边、顶到顶栏下面，不钻到状态栏底下。
        // 共享元素 Box 只包图区本体：topInset 留在外面，hero 形变落点不含状态栏空档
        Column(Modifier.padding(top = topInset)) {
            Box(
                Modifier
                    .sharedWorkBounds(sharedKey)
                    .fillMaxWidth()
                    .animateContentSize(),
            ) {
                ImagePager(
                    detail = detail,
                    dark = dark,
                    sourceThumbnailUrl = sourceThumbnailUrl,
                    fullBleed = true,
                    onImageClick = onImageClick,
                    onImageLongPress = onImageLongPress,
                    onImageShown = onImageShown,
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
                customTags = customTags,
                onToggleCustomTag = onToggleCustomTag,
                showTranslation = showTranslation,
                translating = translating,
                onToggleTranslation = onToggleTranslation,
                onRetranslate = onRetranslate,
                showTranslatedTags = showTranslatedTags,
                tagsTranslating = tagsTranslating,
                onToggleTagsTranslation = onToggleTagsTranslation,
                isFavorite = isFavorite,
                cloudSynced = cloudSynced,
                followed = followed,
                showFollow = showFollow,
                followSending = followSending,
                onBookmarkToggle = onBookmarkToggle,
                onBookmarkLongPress = onBookmarkLongPress,
                onFollowClick = onFollowClick,
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
    customTags: Set<String>,
    onToggleCustomTag: (String) -> Unit,
    showTranslation: Boolean,
    translating: Boolean,
    onToggleTranslation: () -> Unit,
    onRetranslate: () -> Unit,
    showTranslatedTags: Boolean,
    tagsTranslating: Boolean,
    onToggleTagsTranslation: () -> Unit,
    isFavorite: Boolean,
    cloudSynced: Boolean,
    followed: Boolean,
    showFollow: Boolean,
    followSending: Boolean,
    followQuiet: Boolean = false,
    onBookmarkToggle: () -> Unit,
    onBookmarkLongPress: () -> Unit,
    onFollowClick: () -> Unit,
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
            showTranslation = showTranslation,
            translating = translating,
            onToggleTranslation = onToggleTranslation,
            onRetranslate = onRetranslate,
        )
        Spacer(Modifier.height(10.dp))
        AuthorLine(
            detail = detail,
            stats = stats,
            onAuthorClick = onAuthorClick,
            isFavorite = isFavorite,
            cloudSynced = cloudSynced,
            dark = dark,
            followed = followed,
            showFollow = showFollow,
            followSending = followSending,
            followQuiet = followQuiet,
            onBookmarkToggle = onBookmarkToggle,
            onBookmarkLongPress = onBookmarkLongPress,
            onFollowClick = onFollowClick,
            // 收藏开关挪进统计行（☆ 计数格），作者行回归 pixiv 本家版式：头像/名字/关注
            showBookmark = false,
        )
        DescriptionBlock(
            detail = detail,
            dark = dark,
            showTranslation = showTranslation,
        )
        // 计数缺失（匿名限制、作品被限）时只藏两侧纯展示格，收藏开关恒在
        Spacer(Modifier.height(12.dp))
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PikuColors.border),
        )
        Spacer(Modifier.height(10.dp))
        StatsRow(
            stats = stats,
            language = language,
            dark = dark,
            isFavorite = isFavorite,
            cloudSynced = cloudSynced,
            onBookmarkToggle = onBookmarkToggle,
            onBookmarkLongPress = onBookmarkLongPress,
        )
        MetaLine(stats = stats)
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

@Composable
private fun TitleLine(
    detail: WorkDetail,
    showTranslation: Boolean,
    translating: Boolean,
    onToggleTranslation: () -> Unit,
    onRetranslate: () -> Unit,
) {
    if (detail.title.isBlank()) return
    val titleText = detail.translated?.title
        ?.takeIf { showTranslation && it.isNotBlank() }
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
        // 恒显：没有译文时它是「点了去翻」的入口，不能等译文出现才给
        HeadlineTranslateChip(
            shown = showTranslation,
            translating = translating,
            onClick = onToggleTranslation,
            onLongClick = onRetranslate,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
        )
    }
}

/** 收藏星标：点亮=金色实心，长按进收藏夹面板；已同步 pixiv 时带角标。放作者行右侧、不遮图（小说详情用；插画详情的开关是统计行里的 BookmarkStatButton） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteStarButton(
    favorited: Boolean,
    cloudSynced: Boolean,
    dark: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val tint = if (favorited) {
        if (dark) StarDark else StarLight
    } else {
        PikuColors.textSecondary
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .padding(8.dp),
    ) {
        Icon(
            imageVector = if (favorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(R.string.detail_favorite),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        // 同步角标：只表示「本 App 已把这条收藏送上 pixiv」，pixiv 本家藏的不标（取消规则与此对齐）
        if (favorited && cloudSynced) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(7.dp)
                    .background(PikuColors.controlAccent, CircleShape)
                    .border(1.dp, PikuColors.surface, CircleShape),
            )
        }
    }
}

@Composable
internal fun AuthorLine(
    detail: WorkDetail,
    stats: WorkStats?,
    onAuthorClick: () -> Unit,
    isFavorite: Boolean,
    cloudSynced: Boolean = false,
    dark: Boolean,
    followed: Boolean,
    showFollow: Boolean,
    followSending: Boolean,
    followQuiet: Boolean = false,
    onBookmarkToggle: () -> Unit,
    onBookmarkLongPress: () -> Unit,
    onFollowClick: () -> Unit,
    showBookmark: Boolean = true,
) {
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
        // 可进主页的提示：只有名字带箭头，右侧的收藏/关注仍是各自的按钮
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(16.dp),
        )
        // 收藏星标：对作品操作，点自己区域不触发整行跳作者页；pixiv 插画页改为统计行里的按钮
        if (showBookmark) {
            FavoriteStarButton(
                favorited = isFavorite,
                cloudSynced = cloudSynced,
                dark = dark,
                onToggle = onBookmarkToggle,
                onLongPress = onBookmarkLongPress,
            )
        }
        // 关注按钮：与 pixiv 本家同位（作者行右侧）；未登录时整颗不出现
        if (showFollow) {
            Spacer(Modifier.width(10.dp))
            FollowPill(
                followed = followed,
                enabled = !followSending,
                dark = dark,
                quiet = followQuiet,
                onClick = onFollowClick,
            )
        }
    }
}

@Composable
internal fun FollowPill(
    followed: Boolean,
    enabled: Boolean,
    dark: Boolean,
    quiet: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(
                if (!followed) {
                    Modifier.shadow(6.dp, shape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(
                if (followed) Color.Transparent else if (dark) GlassBarBgDark else GlassBarBgLight,
            )
            .border(
                BorderStroke(
                    0.5.dp,
                    if (followed) PikuColors.border else if (dark) SoftBorderDark else SoftBorderLight,
                ),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = if (quiet && followed) 9.dp else 12.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = stringResource(if (followed) R.string.detail_followed else R.string.detail_follow),
            color = if (followed) PikuColors.textSecondary else PikuColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (quiet && followed) {
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(R.string.detail_follow_quiet_state),
                tint = PikuColors.textSecondary,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun StatsRow(
    stats: WorkStats?,
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
            StatCell(
                icon = Icons.Outlined.FavoriteBorder,
                value = compactCount(stats.likes, language),
                label = stringResource(R.string.pixiv_stat_likes),
                modifier = Modifier.weight(1f),
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkStatButton(
    count: Int,
    language: AppLanguage,
    favorited: Boolean,
    cloudSynced: Boolean,
    dark: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    // 星标弹跳，与详情底栏的收藏星同款
    val starScale = remember { Animatable(1f) }
    LaunchedEffect(favorited) {
        if (favorited) {
            starScale.snapTo(1.35f)
            starScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .shadow(4.dp, shape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
            .clip(shape)
            // 玻璃底与关注胶囊同族：未收藏白霜玻璃，收藏后换淡金玻璃
            .background(
                if (favorited) if (dark) StarTintDark else StarTintLight
                else if (dark) GlassBarBgDark else GlassBarBgLight,
            )
            .border(
                BorderStroke(
                    0.5.dp,
                    if (favorited) Color.Transparent else if (dark) SoftBorderDark else SoftBorderLight,
                ),
                shape,
            )
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Box {
            Icon(
                imageVector = if (favorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(R.string.detail_favorite),
                tint = if (favorited) if (dark) StarDark else StarLight else PikuColors.textSecondary,
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer {
                        scaleX = starScale.value
                        scaleY = starScale.value
                    },
            )
            // 同步角标：只表示「本 App 已把这条收藏送上 pixiv」
            if (favorited && cloudSynced) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(6.dp)
                        .background(PikuColors.controlAccent, CircleShape)
                        .border(1.dp, PikuColors.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = compactCount(count, language),
            color = PikuColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 一格计数：图标 + 数字，不写「浏览/点赞/收藏」这类小字——眼睛、心、书签已经自解释，
 * 写出来只是把一行撑成两行。[label] 只留给无障碍朗读。
 */
@Composable
internal fun StatCell(icon: ImageVector, value: String, label: String, modifier: Modifier) {
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
internal fun MetaLine(stats: WorkStats?) {
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

/** 简介：超过三行折叠出「展开/收起」；原/译由标题行那颗 chip 统一切 */
@Composable
internal fun DescriptionBlock(
    detail: WorkDetail,
    dark: Boolean,
    showTranslation: Boolean,
) {
    if (detail.description.isBlank()) return
    val text = detail.translated?.description
        ?.takeIf { showTranslation && it.isNotBlank() }
        ?: detail.description
    var expanded by remember { mutableStateOf(false) }
    // 折叠态才量溢出：展开后 hasVisualOverflow 恒为 false，会误判成"没有更多"
    var overflowing by remember { mutableStateOf(false) }
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
    if (!overflowing && !expanded) return
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

/**
 * 标签：点一个去搜这个标签，右侧「+」加入个人标签（存进 PIXIV 那一份）。
 * 与 poipiku 详情页共用 [TagFlow]，差异只在归档到哪个源。
 * 「原/译」独立于正文统一切换：chip 常驻（有文本模型才出），默认态由「自动翻译标签」设置决定。
 */
@Composable
internal fun TagsBlock(
    detail: WorkDetail,
    customTags: Set<String>,
    dark: Boolean,
    showTranslation: Boolean,
    tagsTranslating: Boolean,
    onTagClick: (String) -> Unit,
    onToggleCustomTag: (String) -> Unit,
    onToggleTranslation: () -> Unit,
) {
    if (detail.tags.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    val shown = tagsTranslationShown(showTranslation, detail.tags, detail.translated?.tags)
    TagFlow(
        tags = detail.tags,
        customTags = customTags,
        dark = dark,
        onTagClick = onTagClick,
        onToggleCustomTag = onToggleCustomTag,
        displayTags = detail.translated?.tags?.takeIf { shown } ?: detail.tags,
        trailing = {
            TagsTranslateChip(
                shown = shown,
                translating = tagsTranslating,
                onClick = onToggleTranslation,
            )
        },
    )
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
