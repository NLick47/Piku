package com.piku.client.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import com.piku.client.ui.navigation.sharedWorkBounds
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.common.LinkSegment
import com.piku.client.common.LinkText
import com.piku.client.data.repository.ThumbnailResolver
import com.piku.client.domain.model.WorkDetail
import com.piku.client.domain.model.TranslatedFields
import com.piku.client.domain.model.RestrictionReason
import com.piku.client.ui.common.ExpandableIconAction
import com.piku.client.ui.common.isAnimatedImage
import com.piku.client.ui.common.localizedCategoryName
import com.piku.client.ui.common.rememberAnimatedImage
import com.piku.client.ui.theme.AccentSolid
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.OverlayBorder
import com.piku.client.ui.theme.OverlayScrim
import com.piku.client.ui.theme.OverlayScrimHeavy
import com.piku.client.ui.theme.OverlayScrimFaint
import com.piku.client.ui.theme.OverlayScrimLight
import com.piku.client.ui.theme.OverlayTipDark
import com.piku.client.ui.theme.OverlayTipLight
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.TranslateActiveBlue
import com.piku.client.ui.theme.TranslateActiveBlueTint
import com.piku.client.ui.theme.ViewerBackgroundDark
import kotlin.math.roundToInt

/** 描述折叠阈值：全文行数超过该值才折叠为 3 行，避免展开只多出一两行的尴尬 */
internal const val DESCRIPTION_COLLAPSE_THRESHOLD = 4

/** 页面内容区左右各自的 padding（与底部操作栏的 20dp 对齐），图区据此推算可用宽度 */
internal const val CONTENT_PADDING_DP = 20
/** 图区默认高度：图片尺寸还没量出来时的占位 */
internal const val IMAGE_HEIGHT_DEFAULT_DP = 320
/** 图区高度下限：超宽横图不至于被压成一条 */
internal const val IMAGE_HEIGHT_MIN_DP = 180
/** 图区高度上限：超长竖图不至于顶满整屏 */
internal const val IMAGE_HEIGHT_MAX_DP = 520
/** 通栏图区的高度上限：屏高的这个比例——p站竖图/多格漫画不该被 520dp 压成一条 */
private const val FULL_BLEED_MAX_HEIGHT_FRACTION = 0.75f
/** 通栏图区：图左右到边、顶到状态栏底下，信息块整体后移 */
internal enum class DetailLayout { AuthorFirst, ImageFirst }
/** 无图空状态高度：只有一行提示，不必占满 360dp */
private const val EMPTY_HEIGHT_DP = 160
/** 密码解锁框高度：标签 + 输入框 + 按钮 + 错误提示 */
private const val PASSWORD_HEIGHT_DP = 220
/** 受限门卡高度：图标 + 标题 + 副文案 + 主按钮 */
private const val GATE_HEIGHT_DP = 220
/** 小说预览高度：正文全文流入，限高卡片内可滚动阅读 */
private const val NOVEL_PREVIEW_HEIGHT_DP = 480
/** 首次进入时，图片翻译按钮自动展开文字的停留时间：比普通点击反馈久，留出看清的余裕 */
private const val IMAGE_HINT_EXPAND_MILLIS = 5_000L

private val POIPIKU_WORK_REGEX = Regex("""https?://poipiku\.com/(\d+)/(\d+)\.html""")

/**
 * 图区高度：按宽高比换算并钳在上下限内；骨架与内容共用同一把尺子，接手时高度不跳。
 * [availableWidthDp] 是图区实际可用的宽度（通栏时就是屏宽），[maxHeightDp] 由版面给。
 */
internal fun imageHeightForAspect(
    aspect: Float,
    availableWidthDp: Int,
    maxHeightDp: Int = IMAGE_HEIGHT_MAX_DP,
): Int {
    if (aspect <= 0f) return IMAGE_HEIGHT_DEFAULT_DP
    return (availableWidthDp / aspect).roundToInt().coerceIn(IMAGE_HEIGHT_MIN_DP, maxHeightDp)
}

/**
 * 通栏版面的图区高度上限：屏高的 3/4，给下面的信息块留出余量。
 * 矮屏上不低于记录卡式的上限——通栏图的余地只该更大，不该反而更小。
 */
internal fun fullBleedMaxHeightDp(screenHeightDp: Int): Int =
    maxOf((screenHeightDp * FULL_BLEED_MAX_HEIGHT_FRACTION).roundToInt(), IMAGE_HEIGHT_MAX_DP)

@Composable
internal fun DetailContent(
    detail: WorkDetail,
    dark: Boolean,
    /** 与列表卡片一致的共享元素 key；空串表示不参与过渡 */
    sharedKey: String = "",
    /** 由页面持有的滚动状态：顶栏据此决定标题是否淡入，避免两处各建一份 */
    scrollState: ScrollState = rememberScrollState(),
    /** 顶部让位给浮起来的顶栏；写在滚动容器内部，滚上去时会跟着内容一起被顶栏盖住 */
    topInset: Dp = 12.dp,
    /** 来源页缩略图（列表卡片的 _360）：首图到位前的低清打底，空串表示没有 */
    sourceThumbnailUrl: String = "",
    /** 内联图区画哪一档（长度与 [WorkDetail.imageUrls] 一致）；null = 用后者，OCR/分享也取后者 */
    displayImageUrls: List<String>? = null,
    /** 版面：[DetailLayout.AuthorFirst] 记录卡式（作者在图前），[DetailLayout.ImageFirst] p站版（图通栏置顶） */
    layout: DetailLayout = DetailLayout.AuthorFirst,
    /** append 还在路上（HTML 阶段的内容已画出）：图区先不显示页码角标 */
    loadingMore: Boolean = false,
    /** 首图渲染完成：据此开始解析原图 URL（见 DetailViewModel.ensureFullImages） */
    onFirstImageLoaded: () -> Unit = {},
    /** 上一次加载失败但屏上已有内容：图区角落给常驻重试入口 */
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
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
    showTranslation: Boolean = false,
    translating: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    /** 长按 chip：换模型重翻 */
    onRetranslate: () -> Unit = {},
    /** 标签独立态：默认显示/懒翻译都由它驱动，与正文统一切换隔离 */
    showTranslatedTags: Boolean = false,
    tagsTranslating: Boolean = false,
    onToggleTagsTranslation: () -> Unit = {},
    /** 首次进入时，图区的图片翻译按钮自动展开一次文字说明 */
    autoExpandImageHint: Boolean = false,
    /** 提示真的展开出来时回调，供外部消耗「已展示过」的一次性标记 */
    onImageHintShown: () -> Unit = {},
    /** 该页有图成功上屏：把 painter 交出去，看图器拿它当零延迟垫底 */
    onImageShown: ((Int, Painter) -> Unit)? = null,
    /** 受限门卡主按钮：LOGIN→去登录，ADULT→一键开启 R-18 显示，FOLLOW→浏览器打开 */
    onGateAction: () -> Unit = {},
    /** 门卡主按钮的动作进行中（R-18 开启 / 关注作者等），按钮转圈防连点 */
    gateLoading: Boolean = false,
    /**
     * 受限门卡类型（ViewModel 结合登录态统一推导，与数据层判定同序）。
     * UI 只认这个字段渲染门卡，不再各自判 detail 上的门属性——
     * 避免"登录用户被展示登录门卡"这类判定错位。
     */
    restrictionReason: RestrictionReason? = null,
) {
    val imageFirst = layout == DetailLayout.ImageFirst
    val translated = detail.translated
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        // 图区两种版面只有位置不同，参数一样：抽成 lambda，避免把整串参数抄两遍
        val imageSection: @Composable () -> Unit = {
            ImagePager(
                detail = detail,
                dark = dark,
                sharedKey = sharedKey,
                sourceThumbnailUrl = sourceThumbnailUrl,
                displayImageUrls = displayImageUrls,
                fullBleed = imageFirst,
                loadingMore = loadingMore,
                onFirstImageLoaded = onFirstImageLoaded,
                loadFailed = loadFailed,
                onRetry = onRetry,
                onImageClick = onImageClick,
                onImageLongPress = onImageLongPress,
                onWorkClick = onRelatedWorkClick,
                password = password,
                onPasswordChange = onPasswordChange,
                onPasswordSubmit = onPasswordSubmit,
                passwordLoading = passwordLoading,
                onGateAction = onGateAction,
                gateLoading = gateLoading,
                restrictionReason = restrictionReason,
                onOpenNovelReader = onOpenNovelReader,
                hasImageModel = hasImageModel,
                imageTranslated = imageTranslated,
                imageTranslatingPage = imageTranslatingPage,
                translatedImages = translatedImages,
                onImageTranslateClick = onImageTranslateClick,
                onPageChanged = onPageChanged,
                autoExpandImageHint = autoExpandImageHint,
                onImageHintShown = onImageHintShown,
                onImageShown = onImageShown,
            )
        }
        // p站版：图左右到边、排在首位，但不钻到顶栏底下——状态栏图标与挖孔摄像头都落在页面底色上
        if (imageFirst) {
            Column(Modifier.padding(top = topInset)) { imageSection() }
        }
        Column(
            Modifier.padding(
                start = CONTENT_PADDING_DP.dp,
                end = CONTENT_PADDING_DP.dp,
                top = if (imageFirst) 14.dp else topInset,
                bottom = 96.dp,
            ),
        ) {
            if (!imageFirst) {
                AuthorSection(
                    detail = detail,
                    dark = dark,
                    translated = translated,
                    showTranslation = showTranslation,
                    onAuthorClick = onAuthorClick,
                    onRelatedWorkClick = onRelatedWorkClick,
                )
                Spacer(Modifier.height(10.dp))
                imageSection()
                Spacer(Modifier.height(14.dp))
            }
            TitleSection(
                detail = detail,
                dark = dark,
                translated = translated,
                showTranslation = showTranslation,
                translating = translating,
                onToggleTranslation = onToggleTranslation,
                onRetranslate = onRetranslate,
                onRelatedWorkClick = onRelatedWorkClick,
            )
            if (imageFirst) {
                Spacer(Modifier.height(12.dp))
                AuthorSection(
                    detail = detail,
                    dark = dark,
                    translated = translated,
                    showTranslation = showTranslation,
                    onAuthorClick = onAuthorClick,
                    onRelatedWorkClick = onRelatedWorkClick,
                )
            }
            DescriptionSection(
                detail = detail,
                dark = dark,
                translated = translated,
                showTranslation = showTranslation,
                onRelatedWorkClick = onRelatedWorkClick,
            )
            TagsSection(
                detail = detail,
                dark = dark,
                translated = translated,
                customTags = customTags,
                showTranslation = showTranslatedTags,
                tagsTranslating = tagsTranslating,
                onTagClick = onTagClick,
                onToggleCustomTag = onToggleCustomTag,
                onToggleTranslation = onToggleTagsTranslation,
                // 通栏版面：chip 从标签流里摘出来右对齐独占一行，免得读成"一个没有 # 的标签"
                chipTrailing = imageFirst,
            )
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
}

/** 作者行 + 作者简介（原/译由标题行那颗 chip 统一切） */
@Composable
private fun AuthorSection(
    detail: WorkDetail,
    dark: Boolean,
    translated: TranslatedFields?,
    showTranslation: Boolean,
    onAuthorClick: () -> Unit,
    onRelatedWorkClick: (Long, Long, String) -> Unit,
) {
    AuthorRow(detail = detail, dark = dark, onAuthorClick = onAuthorClick)
    if (detail.authorProfile.isNotBlank()) {
        val profileText = translated?.authorProfile
            ?.takeIf { showTranslation && it.isNotBlank() }
            ?: detail.authorProfile
        Text(
            text = linkify(profileText, dark, onRelatedWorkClick),
            color = PikuColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 标题（详情页唯一的「原/译」挂在标题行右上角） */
@Composable
private fun TitleSection(
    detail: WorkDetail,
    dark: Boolean,
    translated: TranslatedFields?,
    showTranslation: Boolean,
    translating: Boolean,
    onToggleTranslation: () -> Unit,
    onRetranslate: () -> Unit,
    onRelatedWorkClick: (Long, Long, String) -> Unit,
) {
    if (detail.title.isBlank()) return
    val titleText = translated?.title
        ?.takeIf { showTranslation && it.isNotBlank() }
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
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
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

/** 简介：超过三行折叠，展开/收起独占一行；原/译由标题行那颗 chip 统一切 */
@Composable
private fun DescriptionSection(
    detail: WorkDetail,
    dark: Boolean,
    translated: TranslatedFields?,
    showTranslation: Boolean,
    onRelatedWorkClick: (Long, Long, String) -> Unit,
) {
    if (detail.description.isBlank()) return
    var descriptionExpanded by remember { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    val descriptionText = translated?.description
        ?.takeIf { showTranslation && it.isNotBlank() }
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
    if (!collapsible) return
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

/**
 * 标签流：「原/译」独立于正文统一切换——chip 常驻（有文本模型才出），默认态由「自动翻译标签」决定。
 * 译文标签与原文标签一一对应；点击筛选始终用原文，否则搜不到结果。
 */
@Composable
private fun TagsSection(
    detail: WorkDetail,
    dark: Boolean,
    translated: TranslatedFields?,
    customTags: Set<String>,
    showTranslation: Boolean,
    tagsTranslating: Boolean,
    onTagClick: (String) -> Unit,
    onToggleCustomTag: (String) -> Unit,
    onToggleTranslation: () -> Unit,
    chipTrailing: Boolean = false,
) {
    if (detail.tags.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    val shown = tagsTranslationShown(showTranslation, detail.tags, translated?.tags)
    val displayTags = translated?.tags?.takeIf { shown } ?: detail.tags
    val chip: @Composable () -> Unit = {
        TagsTranslateChip(shown = shown, translating = tagsTranslating, onClick = onToggleTranslation)
    }
    TagFlow(
        tags = detail.tags,
        displayTags = displayTags,
        customTags = customTags,
        dark = dark,
        onTagClick = onTagClick,
        onToggleCustomTag = onToggleCustomTag,
        trailing = if (!chipTrailing) chip else null,
    )
    if (chipTrailing) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            chip()
        }
    }
}

@Composable
private fun AuthorRow(detail: WorkDetail, dark: Boolean, onAuthorClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = detail.authorAvatarUrl,
            contentDescription = stringResource(R.string.detail_user_home),
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
                text = localizedCategoryName(detail.categoryCd, detail.categoryName),
                color = PikuColors.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

/** internal：pixiv 详情（ui.source）复用同一个图区，不另抄一份 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImagePager(
    detail: WorkDetail,
    dark: Boolean,
    /** 与列表卡片一致的共享元素 key；空串表示不参与过渡 */
    sharedKey: String = "",
    /** 来源页缩略图（列表卡片的 _360）：首图到位前的低清打底 */
    sourceThumbnailUrl: String = "",
    /** 内联画哪一档（长度与 detail.imageUrls 一致）；null = 用 detail.imageUrls */
    displayImageUrls: List<String>? = null,
    /** 通栏：图左右到边、高度上限按屏高放宽（p站版）；圆角也去掉 */
    fullBleed: Boolean = false,
    /** append 还在路上：页码角标先不显示（此刻的页数只有主图这一张） */
    loadingMore: Boolean = false,
    /** 首图渲染完成回调（只报第一页） */
    onFirstImageLoaded: () -> Unit = {},
    /** 上一次加载失败但屏上已有内容：图区角落给常驻重试入口 */
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onImageClick: (Int) -> Unit,
    onImageLongPress: (Int) -> Unit,
    onWorkClick: (Long, Long, String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onPasswordSubmit: () -> Unit,
    passwordLoading: Boolean,
    /** 受限门卡主按钮：LOGIN→去登录，ADULT→一键开启 R-18 显示，FOLLOW→浏览器打开 */
    onGateAction: () -> Unit = {},
    /** 门卡主按钮的动作进行中（R-18 开启 / 关注作者等）：按钮转圈防连点 */
    gateLoading: Boolean = false,
    /** 受限门卡类型（ViewModel 统一推导）；null = 无比受限门卡 */
    restrictionReason: RestrictionReason? = null,
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
    /** 该页有图成功上屏：把 painter 交出去（pixiv 详情拿它给看图器当零延迟垫底） */
    onImageShown: ((Int, Painter) -> Unit)? = null,
) {
    val urls = displayImageUrls ?: detail.imageUrls
    val pagerState = rememberPagerState(pageCount = { urls.size })
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged?.invoke(pagerState.currentPage)
    }
    // 首图的低清打底：来源缩略图与首图是同一张图时才垫（判定见
    // ThumbnailResolver.detailUnderlayUrl——占位图、首图另有其图都不垫）。
    // 垫的是卡片刚渲染过的那张，缓存必中：共享元素过渡落地时图区就是有图的，
    // 不会先空一块或只剩底色，清晰档到位后盖上去。
    val underlayUrl = remember(sourceThumbnailUrl, urls) {
        ThumbnailResolver.detailUnderlayUrl(sourceThumbnailUrl, urls.firstOrNull())
    }
    // 每页自己记住已上屏的档位：同页换档时旧图垫在下面直到新档就绪，
    // 图区不露底。首页优先用来源缩略图，其余页自持
    val shownUrls = remember { mutableStateMapOf<Int, String>() }

    // 图区高度跟随真实宽高比：竖图不再被压成窄带，横图也不再上下留大片空白。
    // 量过的页码缓存下来，翻回看过的图能立刻恢复高度，不会先跳回默认值再跳回来。
    val aspectCache = remember { mutableStateMapOf<Int, Float>() }
    val configuration = LocalConfiguration.current
    val availableWidthDp = if (fullBleed) {
        configuration.screenWidthDp
    } else {
        configuration.screenWidthDp - CONTENT_PADDING_DP * 2
    }
    val maxHeightDp = if (fullBleed) fullBleedMaxHeightDp(configuration.screenHeightDp) else IMAGE_HEIGHT_MAX_DP
    val heightForAspect: (Float) -> Int = { aspect -> imageHeightForAspect(aspect, availableWidthDp, maxHeightDp) }
    // 外层高度取「已测量过的图里最高的那张」，而不是当前页的高度：
    // 翻页时外层纹丝不动，下面的标题/描述/标签就不会跟着上下跳。
    // 代价是尺寸小的图上下会留出背景色的空白带——用这点留白换下方内容稳定。
    // HorizontalPager 会预加载相邻页，所以下一张的高度通常在翻页前就已经算进来了。
    val imageHeightDp = aspectCache.values.maxOfOrNull { heightForAspect(it) }
        ?: IMAGE_HEIGHT_DEFAULT_DP
    // 无图时按内容给合适高度：一行提示不需要 320dp，密码框和小说预览才需要空间
    val boxHeightDp = when {
        detail.imageUrls.isNotEmpty() -> imageHeightDp
        restrictionReason != null -> GATE_HEIGHT_DP
        detail.novelText.isNotBlank() -> NOVEL_PREVIEW_HEIGHT_DP
        detail.passwordProtected -> PASSWORD_HEIGHT_DP
        else -> EMPTY_HEIGHT_DP
    }

    // 密码框的高度随错误/封禁提示自适应：写死高度时，提示文字超出部分会被整体裁掉
    // 有正文时走下面的固定高度分支：heightIn 没有上限，小说预览的 verticalScroll 会拿到无限高度直接崩
    val passwordOnly = detail.imageUrls.isEmpty() && detail.passwordProtected && detail.novelText.isBlank()
    Box(
        modifier = Modifier
            .sharedWorkBounds(sharedKey)
            .fillMaxWidth()
            .animateContentSize()
            .then(
                if (passwordOnly) {
                    Modifier.heightIn(min = PASSWORD_HEIGHT_DP.dp)
                } else {
                    Modifier.height(boxHeightDp.dp)
                },
            )
            // 通栏不加圆角：图左右已经顶到边，圆角会把两侧切出背景色缺口
            .then(if (fullBleed) Modifier else Modifier.clip(RoundedCornerShape(12.dp)))
            .background(PikuColors.surfaceSoft),
    ) {
        if (detail.imageUrls.isEmpty()) {
            when {
                // 受限门卡（登录/关注/R-18）：类型由 ViewModel 结合登录态统一推导，
                // 与数据层判定同序。UI 不再自行判 detail 上的门属性——否则登录用户
                // 会因为作品属性里的 "Login" 标记被错展示成"需要登录"
                restrictionReason != null -> {
                    LockGateCard(
                        reason = restrictionReason,
                        loading = gateLoading,
                        onPrimaryAction = onGateAction,
                    )
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
                    Box(Modifier.fillMaxSize()) {
                        // 垫底：首页吃来源缩略图，其余页吃本页已上屏的档位
                        // 换档期间旧图一直在，新档就绪后盖住它，图区全程不露底
                        val underlay = (if (page == 0) underlayUrl else null) ?: shownUrls[page]
                        if (underlay != null && !isAnimatedImage(underlay)) {
                            AsyncImage(
                                model = underlay,
                                contentDescription = null,
                                colorFilter = PikuColors.tameWhiteFilter,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                onSuccess = { state ->
                                    // 打底图与首图是同一张（见 detailUnderlayUrl），宽高比先量出来，
                                    // 图区高度一次到位，下面的内容不会等首图到了再往下跳
                                    val size = state.painter.intrinsicSize
                                    if (size.width > 0f && size.height > 0f) {
                                        aspectCache[page] = size.width / size.height
                                    }
                                    onImageShown?.invoke(page, state.painter)
                                },
                            )
                        }
                        AsyncImage(
                            model = rememberAnimatedImage(urls[page]),
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
                                shownUrls[page] = urls[page]
                                // 首图已经在屏上了：此刻再解析原图 URL，不和它抢带宽
                                if (page == 0) onFirstImageLoaded()
                                onImageShown?.invoke(page, state.painter)
                            },
                        )
                    }
                }
            }
            // 角标与按钮一律锚在卡片四角，不跟着图片尺寸走：
            // 图区高度刚固定下来，浮层再随图片高度浮动就等于白固定了。
            // append 未到时不显示：此刻页数只有主图这一张，会从 1/1 跳到 1/12；
            // 失败时也让位给重试入口——两者锚在同一个角，叠在一起会互相压住
            if (urls.size > 1 && !loadingMore && !loadFailed) {
                Text(
                    text = stringResource(
                        R.string.detail_image_index,
                        pagerState.currentPage + 1,
                        urls.size,
                    ),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(OverlayScrim)
                        .border(BorderStroke(0.5.dp, OverlayBorder), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            // 追加图/正文拉取失败：给常驻入口，snackbar 消失后也能重来
            if (loadFailed) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(OverlayScrim)
                        .border(BorderStroke(0.5.dp, OverlayBorder), RoundedCornerShape(999.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.home_retry),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
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
                        isCurrentPageTranslating -> OverlayScrimHeavy
                        imageTranslated -> TranslateActiveBlueTint
                        dark -> OverlayScrim
                        else -> OverlayScrimLight
                    },
                    // 展开时把底色压到近实色：文字是压在图片上的，半透明底会读不清
                    expandedContainerColor = if (dark) OverlayTipDark else OverlayTipLight,
                    height = 28.dp,
                    horizontalPadding = 6.dp,
                    loading = isCurrentPageTranslating,
                    loadingLabel = stringResource(R.string.detail_translating),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    icon = {
                        if (isCurrentPageTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = if (dark) Color.White else TranslateActiveBlue,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = stringResource(R.string.detail_image_translate),
                                tint = if (imageTranslated) TranslateActiveBlue
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
 * 小说正文预览：正文全文流入限高卡片，卡片内可垂直滚动（内层滚动），
 * 底部渐隐提示「还有更多」，滚到底后渐隐消失。
 * 内层滚到底后剩余手势交给外层页面继续滚动（Compose 嵌套滚动默认接力）。
 * 完整阅读仍走右上角的全屏阅读器。
 */
@Composable
private fun NovelPreview(detail: WorkDetail, dark: Boolean, onWorkClick: (Long, Long, String) -> Unit) {
    val backdrop = PikuColors.surfaceSoft
    val previewScrollState = rememberScrollState()
    val atBottom by remember {
        derivedStateOf { previewScrollState.value >= previewScrollState.maxValue }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = linkify(detail.novelText, dark, onWorkClick),
            color = PikuColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(previewScrollState)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        )
        if (!atBottom) {
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
            .background(if (dark) ViewerBackgroundDark.copy(alpha = 0.8f) else OverlayTipLight)
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
            .fillMaxWidth()
            .defaultMinSize(minHeight = PASSWORD_HEIGHT_DP.dp)
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

/** internal：pixiv 详情复用同一套链接识别（非 poipiku 作品链接一律交给浏览器） */
@Composable
internal fun linkify(
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
