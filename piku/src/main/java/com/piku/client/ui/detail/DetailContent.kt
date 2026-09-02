package com.piku.client.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.common.LinkSegment
import com.piku.client.common.LinkText
import com.piku.client.domain.model.WorkDetail
import com.piku.client.ui.common.ExpandableIconAction
import com.piku.client.ui.common.rememberAnimatedImage
import com.piku.client.ui.theme.AccentSolid
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.PikuColors
import kotlin.math.roundToInt

/** 描述折叠阈值：全文行数超过该值才折叠为 3 行，避免展开只多出一两行的尴尬 */
internal const val DESCRIPTION_COLLAPSE_THRESHOLD = 4

/** 页面内容区左右各自的 padding（与底部操作栏的 20dp 对齐），图区据此推算可用宽度 */
private const val CONTENT_PADDING_DP = 20
/** 图区默认高度：图片尺寸还没量出来时的占位 */
private const val IMAGE_HEIGHT_DEFAULT_DP = 320
/** 图区高度下限：超宽横图不至于被压成一条 */
private const val IMAGE_HEIGHT_MIN_DP = 180
/** 图区高度上限：超长竖图不至于顶满整屏 */
private const val IMAGE_HEIGHT_MAX_DP = 520
/** 无图空状态高度：只有一行提示，不必占满 360dp */
private const val EMPTY_HEIGHT_DP = 160
/** 密码解锁框高度：标签 + 输入框 + 按钮 + 错误提示 */
private const val PASSWORD_HEIGHT_DP = 220
/** 小说预览高度 */
private const val NOVEL_PREVIEW_HEIGHT_DP = 320
/** 小说预览最多展示的行数，其余走全屏阅读器 */
private const val NOVEL_PREVIEW_MAX_LINES = 12
/** 首次进入时，图片翻译按钮自动展开文字的停留时间：比普通点击反馈久，留出看清的余裕 */
private const val IMAGE_HINT_EXPAND_MILLIS = 5_000L

private val POIPIKU_WORK_REGEX = Regex("""https?://poipiku\.com/(\d+)/(\d+)\.html""")

@Composable
internal fun DetailContent(
    detail: WorkDetail,
    dark: Boolean,
    /** 由页面持有的滚动状态：顶栏据此决定标题是否淡入，避免两处各建一份 */
    scrollState: ScrollState = rememberScrollState(),
    onImageClick: (Int) -> Unit,
    onImageLongPress: (Int) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onPasswordSubmit: () -> Unit,
    passwordLoading: Boolean,
    onTagClick: (String) -> Unit,
    onRelatedWorkClick: (Long, Long, String) -> Unit,
    onAuthorClick: () -> Unit,
    customTags: Set<String>,
    onToggleCustomTag: (String) -> Unit,
    onOpenNovelReader: () -> Unit,
    hasImageModel: Boolean = false,
    imageTranslated: Boolean = false,
    imageTranslatingPage: Int? = null,
    translatedImages: Map<Int, android.graphics.Bitmap> = emptyMap(),
    onImageTranslateClick: ((Int) -> Unit)? = null,
    onPageChanged: ((Int) -> Unit)? = null,
    translationAvailable: Boolean = false,
    showTranslation: (TranslateField) -> Boolean = { false },
    onToggleField: (TranslateField) -> Unit = {},
    /** 首次进入时，图区的图片翻译按钮自动展开一次文字说明 */
    autoExpandImageHint: Boolean = false,
    /** 提示真的展开出来时回调，供外部消耗「已展示过」的一次性标记 */
    onImageHintShown: () -> Unit = {},
) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    val translated = detail.translated
    // 只有该字段真有译文时才显示 chip，避免出现点了没反应的按钮
    fun chipVisible(field: TranslateField): Boolean = translationAvailable && when (field) {
        TranslateField.TITLE -> !translated?.title.isNullOrBlank()
        TranslateField.DESCRIPTION -> !translated?.description.isNullOrBlank()
        TranslateField.AUTHOR_PROFILE -> !translated?.authorProfile.isNullOrBlank()
        TranslateField.TAGS -> !translated?.tags.isNullOrEmpty()
        TranslateField.NOVEL -> !translated?.novelText.isNullOrBlank()
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
    ) {
        AuthorRow(detail = detail, dark = dark, onAuthorClick = onAuthorClick)
        if (detail.authorProfile.isNotBlank()) {
            val profileTranslated = showTranslation(TranslateField.AUTHOR_PROFILE)
            val profileText = translated?.authorProfile
                ?.takeIf { profileTranslated && it.isNotBlank() }
                ?: detail.authorProfile
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    text = linkify(profileText, dark, onRelatedWorkClick),
                    color = PikuColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (chipVisible(TranslateField.AUTHOR_PROFILE)) {
                    TranslateChip(
                        showTranslation = profileTranslated,
                        dark = dark,
                        onClick = { onToggleField(TranslateField.AUTHOR_PROFILE) },
                        modifier = Modifier.padding(start = 6.dp, top = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ImagePager(
            detail = detail,
            dark = dark,
            onImageClick = onImageClick,
            onImageLongPress = onImageLongPress,
            onWorkClick = onRelatedWorkClick,
            password = password,
            onPasswordChange = onPasswordChange,
            onPasswordSubmit = onPasswordSubmit,
            passwordLoading = passwordLoading,
            onOpenNovelReader = onOpenNovelReader,
            hasImageModel = hasImageModel,
            imageTranslated = imageTranslated,
            imageTranslatingPage = imageTranslatingPage,
            translatedImages = translatedImages,
            onImageTranslateClick = onImageTranslateClick,
            onPageChanged = onPageChanged,
            autoExpandImageHint = autoExpandImageHint,
            onImageHintShown = onImageHintShown,
        )
        Spacer(Modifier.height(14.dp))
        if (detail.title.isNotBlank()) {
            val titleTranslated = showTranslation(TranslateField.TITLE)
            val titleText = translated?.title
                ?.takeIf { titleTranslated && it.isNotBlank() }
                ?: detail.title
            val titleSelection = remember { SelectionState() }
            Row(verticalAlignment = Alignment.Top) {
                SelectionContainer(
                    state = titleSelection,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(titleSelection) {
                            detectTapGestures(onTap = { titleSelection.clear() })
                        },
                ) {
                    Text(
                        text = linkify(titleText, dark, onRelatedWorkClick),
                        color = PikuColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (chipVisible(TranslateField.TITLE)) {
                    TranslateChip(
                        showTranslation = titleTranslated,
                        dark = dark,
                        onClick = { onToggleField(TranslateField.TITLE) },
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                    )
                }
            }
        }
        if (detail.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            val descriptionTranslated = showTranslation(TranslateField.DESCRIPTION)
            val descriptionText = translated?.description
                ?.takeIf { descriptionTranslated && it.isNotBlank() }
                ?: detail.description
            val descriptionSelection = remember { SelectionState() }
            val textMeasurer = rememberTextMeasurer()
            val linkifiedDescription = linkify(descriptionText, dark, onRelatedWorkClick)
            var containerWidthPx by remember { mutableIntStateOf(0) }
            val descriptionStyle = LocalTextStyle.current.copy(fontSize = 13.sp, lineHeight = 20.sp)
            val fullLineCount = if (containerWidthPx > 0) {
                remember(linkifiedDescription, containerWidthPx, dark) {
                    textMeasurer.measure(
                        text = linkifiedDescription,
                        style = descriptionStyle,
                        constraints = Constraints(maxWidth = containerWidthPx),
                        overflow = TextOverflow.Clip,
                    ).lineCount
                }
            } else {
                0
            }
            val collapsible = fullLineCount > DESCRIPTION_COLLAPSE_THRESHOLD
            SelectionContainer(
                state = descriptionSelection,
                modifier = Modifier
                    .animateContentSize()
                    .pointerInput(descriptionSelection) {
                        detectTapGestures(onTap = { descriptionSelection.clear() })
                    },
            ) {
                Text(
                    text = linkifiedDescription,
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = if (collapsible && !descriptionExpanded) 3 else Int.MAX_VALUE,
                    overflow = if (collapsible && !descriptionExpanded) {
                        TextOverflow.Ellipsis
                    } else {
                        TextOverflow.Clip
                    },
                    modifier = Modifier.onSizeChanged { containerWidthPx = it.width },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (collapsible) {
                    Text(
                        text = stringResource(
                            if (descriptionExpanded) R.string.detail_show_less else R.string.detail_show_more
                        ),
                        color = PikuColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 8.dp)
                            .clickable { descriptionExpanded = !descriptionExpanded },
                    )
                }
                if (chipVisible(TranslateField.DESCRIPTION)) {
                    if (collapsible) Spacer(Modifier.width(8.dp))
                    TranslateChip(
                        showTranslation = descriptionTranslated,
                        dark = dark,
                        onClick = { onToggleField(TranslateField.DESCRIPTION) },
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
            }
        }
        if (detail.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val tagsTranslated = showTranslation(TranslateField.TAGS)
            // 译文标签与原文标签一一对应；点击筛选始终用原文，否则搜不到结果
            val displayTags = translated?.tags
                ?.takeIf { tagsTranslated && it.size == detail.tags.size }
                ?: detail.tags
            TagFlow(
                tags = detail.tags,
                displayTags = displayTags,
                customTags = customTags,
                dark = dark,
                onTagClick = onTagClick,
                onToggleCustomTag = onToggleCustomTag,
                trailing = if (chipVisible(TranslateField.TAGS)) {
                    {
                        TranslateChip(
                            showTranslation = tagsTranslated,
                            dark = dark,
                            onClick = { onToggleField(TranslateField.TAGS) },
                        )
                    }
                } else {
                    null
                },
            )
        }
        if (detail.relatedWorks.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            RelatedWorksSection(
                works = detail.relatedWorks,
                dark = dark,
                onClick = onRelatedWorkClick,
            )
        }
    }
}

@Composable
private fun AuthorRow(detail: WorkDetail, dark: Boolean, onAuthorClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = detail.authorAvatarUrl,
            contentDescription = stringResource(R.string.detail_author_home),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PikuColors.surface)
                .clickable(onClick = onAuthorClick),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = detail.authorName,
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAuthorClick)
                .padding(vertical = 4.dp),
        )
        if (detail.categoryName.isNotBlank()) {
            Text(
                text = detail.categoryName,
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(PikuColors.surfaceSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePager(
    detail: WorkDetail,
    dark: Boolean,
    onImageClick: (Int) -> Unit,
    onImageLongPress: (Int) -> Unit,
    onWorkClick: (Long, Long, String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onPasswordSubmit: () -> Unit,
    passwordLoading: Boolean,
    onOpenNovelReader: () -> Unit,
    hasImageModel: Boolean = false,
    imageTranslated: Boolean = false,
    imageTranslatingPage: Int? = null,
    translatedImages: Map<Int, android.graphics.Bitmap> = emptyMap(),
    onImageTranslateClick: ((Int) -> Unit)? = null,
    onPageChanged: ((Int) -> Unit)? = null,
    /** 首次进入时，图片翻译按钮自动展开一次文字说明 */
    autoExpandImageHint: Boolean = false,
    /** 提示真的展开出来时回调，供外部消耗「已展示过」的一次性标记 */
    onImageHintShown: () -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { detail.imageUrls.size })
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged?.invoke(pagerState.currentPage)
    }

    // 图区高度跟随真实宽高比：竖图不再被压成窄带，横图也不再上下留大片空白。
    // 量过的页码缓存下来，翻回看过的图能立刻恢复高度，不会先跳回默认值再跳回来。
    val aspectCache = remember { mutableStateMapOf<Int, Float>() }
    val availableWidthDp = LocalConfiguration.current.screenWidthDp - CONTENT_PADDING_DP * 2
    val heightForAspect: (Float) -> Int = { aspect ->
        (availableWidthDp / aspect).roundToInt().coerceIn(IMAGE_HEIGHT_MIN_DP, IMAGE_HEIGHT_MAX_DP)
    }
    // 外层高度取「已测量过的图里最高的那张」，而不是当前页的高度：
    // 翻页时外层纹丝不动，下面的标题/描述/标签就不会跟着上下跳。
    // 代价是尺寸小的图上下会留出背景色的空白带——用这点留白换下方内容稳定。
    // HorizontalPager 会预加载相邻页，所以下一张的高度通常在翻页前就已经算进来了。
    val imageHeightDp = aspectCache.values.maxOfOrNull { heightForAspect(it) }
        ?: IMAGE_HEIGHT_DEFAULT_DP
    // 无图时按内容给合适高度：一行提示不需要 320dp，密码框和小说预览才需要空间
    val boxHeightDp = when {
        detail.imageUrls.isNotEmpty() -> imageHeightDp
        detail.adultLocked -> EMPTY_HEIGHT_DP
        detail.novelText.isNotBlank() -> NOVEL_PREVIEW_HEIGHT_DP
        detail.passwordProtected -> PASSWORD_HEIGHT_DP
        else -> EMPTY_HEIGHT_DP
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .height(boxHeightDp.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PikuColors.surfaceSoft),
    ) {
        if (detail.imageUrls.isEmpty()) {
            when {
                detail.adultLocked -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.detail_adult_locked),
                            color = PikuColors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                detail.novelText.isNotBlank() -> {
                    NovelPreview(detail = detail, dark = dark, onWorkClick = onWorkClick)
                    NovelReaderEntryButton(
                        dark = dark,
                        onOpen = onOpenNovelReader,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
                detail.passwordProtected -> {
                    PasswordBox(
                        dark = dark,
                        password = password,
                        onPasswordChange = onPasswordChange,
                        onPasswordSubmit = onPasswordSubmit,
                        loading = passwordLoading,
                        error = detail.passwordError,
                        blocked = detail.unlockBlocked,
                        blockedMessage = detail.unlockBlockedMessage,
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.detail_no_image),
                            color = PikuColors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            // pager 铺满整个图区：留白带也能横向滑动切图，不只是图片本身那一条
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val translatedBitmap = if (imageTranslated) translatedImages[page] else null
                if (translatedBitmap != null) {
                    Image(
                        bitmap = translatedBitmap.asImageBitmap(),
                        contentDescription = detail.title,
                        colorFilter = PikuColors.tameWhiteFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = { onImageClick(page) },
                                onLongClick = { onImageLongPress(page) },
                            ),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    AsyncImage(
                        model = rememberAnimatedImage(detail.imageUrls[page]),
                        contentDescription = detail.title,
                        colorFilter = PikuColors.tameWhiteFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = { onImageClick(page) },
                                onLongClick = { onImageLongPress(page) },
                            ),
                        contentScale = ContentScale.Fit,
                        onSuccess = { state ->
                            // 取 painter 的固有尺寸换算宽高比，不依赖 result 的具体图片类型
                            val size = state.painter.intrinsicSize
                            if (size.width > 0f && size.height > 0f) {
                                aspectCache[page] = size.width / size.height
                            }
                        },
                    )
                }
            }
            // 角标与按钮一律锚在卡片四角，不跟着图片尺寸走：
            // 图区高度刚固定下来，浮层再随图片高度浮动就等于白固定了。
            if (detail.imageUrls.size > 1) {
                Text(
                    text = stringResource(
                        R.string.detail_image_index,
                        pagerState.currentPage + 1,
                        detail.imageUrls.size,
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
            if (hasImageModel && onImageTranslateClick != null) {
                val isCurrentPageTranslating = imageTranslatingPage == pagerState.currentPage
                ExpandableIconAction(
                    label = stringResource(R.string.detail_image_translate),
                    dark = dark,
                    onClick = { onImageTranslateClick(pagerState.currentPage) },
                    enabled = !isCurrentPageTranslating,
                    autoExpand = autoExpandImageHint,
                    onAutoExpandShown = onImageHintShown,
                    expandDurationMillis = IMAGE_HINT_EXPAND_MILLIS,
                    containerColor = when {
                        isCurrentPageTranslating -> Color(0x44000000)
                        imageTranslated -> Color(0x442196F3)
                        dark -> Color(0x66000000)
                        else -> Color(0x66FFFFFF)
                    },
                    // 展开时把底色压到近实色：文字是压在图片上的，半透明底会读不清
                    expandedContainerColor = if (dark) Color(0xCC000000) else Color(0xCCFFFFFF),
                    height = 28.dp,
                    horizontalPadding = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    icon = {
                        if (isCurrentPageTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = stringResource(R.string.detail_image_translate),
                                tint = if (imageTranslated) Color(0xFF4FC3F7)
                                else PikuColors.textPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * 小说正文预览：只展示开头若干行，底部渐隐提示还有更多。
 * 刻意不做内层滚动——外层 DetailContent 已是垂直滚动容器，嵌套同向滚动会让滑动手势归属随机。
 * 完整阅读统一走右上角的全屏阅读器。
 */
@Composable
private fun NovelPreview(detail: WorkDetail, dark: Boolean, onWorkClick: (Long, Long, String) -> Unit) {
    val backdrop = PikuColors.surfaceSoft
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = linkify(detail.novelText, dark, onWorkClick),
            color = PikuColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 22.sp,
            maxLines = NOVEL_PREVIEW_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(32.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, backdrop)),
                ),
        )
    }
}

/** 全屏阅读入口按钮：纯文字作品的图区右上角 */
@Composable
private fun NovelReaderEntryButton(
    dark: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) Color(0xCC141312) else Color(0xCCFFFFFF))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Article,
            contentDescription = null,
            tint = PikuColors.textPrimary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.detail_novel_fullscreen),
            color = PikuColors.textPrimary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PasswordBox(
    dark: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    onPasswordSubmit: () -> Unit,
    loading: Boolean,
    error: Boolean,
    blocked: Boolean = false,
    blockedMessage: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_password_label),
            color = PikuColors.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { if (it.length <= 16) onPasswordChange(it) },
            placeholder = {
                Text(
                    text = stringResource(R.string.detail_password_hint),
                    fontSize = 13.sp,
                )
            },
            singleLine = true,
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PikuColors.surfaceMuted,
                unfocusedContainerColor = PikuColors.surfaceMuted,
                focusedBorderColor = PikuColors.border,
                unfocusedBorderColor = PikuColors.border,
                cursorColor = PikuColors.controlAccent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onPasswordSubmit,
            enabled = password.isNotBlank() && !loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (dark) LoginTextPrimaryDark else AccentSolid,
                contentColor = if (dark) LoginBackgroundDark else Color.White,
            ),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.detail_password_submit),
                    fontSize = 13.sp,
                )
            }
        }
        if (error) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.detail_password_error),
                color = PikuColors.error,
                fontSize = 12.sp,
            )
        }
        if (blocked) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = blockedMessage.ifBlank { stringResource(R.string.detail_unlock_blocked) },
                color = PikuColors.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun linkify(
    raw: String,
    dark: Boolean,
    onWorkClick: (Long, Long, String) -> Unit,
): AnnotatedString {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)
    val currentOnWorkClick by rememberUpdatedState(onWorkClick)
    val controlAccent = PikuColors.controlAccent
    return remember(raw, dark) {
        val style = SpanStyle(
            color = controlAccent,
            textDecoration = TextDecoration.Underline,
        )
        buildAnnotatedString {
            for (segment in LinkText.parse(raw)) {
                when (segment) {
                    is LinkSegment.Plain -> append(segment.text)
                    is LinkSegment.Link -> {
                        val workMatch = POIPIKU_WORK_REGEX.matchEntire(segment.url)
                        if (workMatch != null) {
                            val authorId = workMatch.groupValues[1].toLongOrNull()
                            val workId = workMatch.groupValues[2].toLongOrNull()
                            withLink(
                                LinkAnnotation.Clickable(tag = segment.url) {
                                    if (authorId != null && workId != null) {
                                        // 文本链接无缩略图信息
                                        currentOnWorkClick(authorId, workId, "")
                                    }
                                },
                            ) {
                                pushStyle(style)
                                append(segment.text)
                                pop()
                            }
                        } else {
                            withLink(
                                LinkAnnotation.Clickable(tag = segment.url) {
                                    currentContext.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(segment.url)),
                                    )
                                },
                            ) {
                                pushStyle(style)
                                append(segment.text)
                                pop()
                            }
                        }
                    }
                }
            }
        }
    }
}
