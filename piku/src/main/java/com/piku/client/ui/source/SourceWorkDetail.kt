package com.piku.client.ui.source

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.piku.client.R
import com.piku.client.data.local.ShareTargets
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.key
import com.piku.client.ui.detail.DETAIL_TOP_BAR_HEIGHT
import com.piku.client.ui.detail.DetailSkeleton
import com.piku.client.ui.detail.DetailTopBar
import com.piku.client.ui.common.FeedbackHost
import com.piku.client.ui.detail.FavoriteSheet
import com.piku.client.ui.detail.ViewerOverlay
import com.piku.client.ui.detail.FullNovelViewer
import com.piku.client.ui.detail.ImageActionSheet
import com.piku.client.ui.navigation.workSharedKey
import com.piku.client.ui.theme.BlobPinkDark
import com.piku.client.ui.theme.BlobPinkLight
import com.piku.client.ui.theme.BlobPurpleDark
import com.piku.client.ui.theme.BlobPurpleLight
import com.piku.client.ui.theme.BlobWarmDark
import com.piku.client.ui.theme.BlobWarmLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.launch

@Composable
internal fun SourceWorkDetailScreen(
    work: Work,
    onBack: () -> Unit,
    /** 顶栏「回首页」：弹回首页（跨过中间的收藏/历史等页面），与 poipiku 详情同语义 */
    onHomeClick: () -> Unit,
    onOpenAuthor: (Work) -> Unit,
    /** 点相关作品：压栈进新详情；栈深上限由导航层统一把关 */
    onRelatedClick: (Work) -> Unit,
    viewModel: SourceWorkDetailViewModel = hiltViewModel(key = "source-detail-${work.key}"),
) {
    val dark = LocalDarkTheme.current
    LaunchedEffect(work.id) { viewModel.load(work) }
    // VM 随返回栈条目驻留：退出时释放图片翻译位图等重体量状态（见 release 注释）
    DisposableEffect(work.id) {
        onDispose { viewModel.release() }
    }

    val state by viewModel.ui.collectAsState()
    var viewerPage by remember(work.id) { mutableStateOf(-1) }
    var imageActionPage by remember(work.id) { mutableStateOf(-1) }
    var favoriteSheetVisible by remember(work.id) { mutableStateOf(false) }
    // 图区已上屏的图：看图器拿它当零延迟垫底，不依赖任何缓存命中
    val shownPainters = remember(work.id) { mutableStateMapOf<Int, Painter>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savePermissionMessage = stringResource(R.string.detail_save_permission_denied)

    // 保存权限（Q 以下）：与 poipiku 详情同一套 launcher + 待办标记
    var pendingSavePage by remember { mutableStateOf(-1) }
    var pendingSaveAll by remember { mutableStateOf(false) }
    fun hasSavePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val page = pendingSavePage
        when {
            granted && pendingSaveAll -> viewModel.saveAllImages()
            granted && page >= 0 -> viewModel.saveImage(page)
            !granted && (page >= 0 || pendingSaveAll) ->
                scope.launch { snackbarHostState.showSnackbar(savePermissionMessage) }
        }
        pendingSavePage = -1
        pendingSaveAll = false
    }
    fun requestSaveImage(page: Int) {
        if (hasSavePermission()) {
            viewModel.saveImage(page)
        } else {
            pendingSavePage = page
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    fun requestSaveAllImages() {
        if (hasSavePermission()) {
            viewModel.saveAllImages()
        } else {
            pendingSaveAll = true
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(work.id) {
        viewModel.shareSheetDismiss.collect { imageActionPage = -1 }
    }

    // 分享准备完成 → 拉起分享（定向不可解析静静回落系统面板；只有确认未安装才提示）——与 poipiku 同逻辑
    val shareRequest by viewModel.shareRequest.collectAsState()
    LaunchedEffect(shareRequest) {
        val request = shareRequest ?: return@LaunchedEffect
        val target = request.targetPackage
        fun baseIntent(): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, request.uri)
            if (request.shareText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, request.shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newUri(context.contentResolver, "shared_image", request.uri)
        }
        fun openChooser(): Boolean = runCatching {
            context.startActivity(Intent.createChooser(baseIntent(), null))
            true
        }.getOrDefault(false)
        try {
            if (target != null) {
                val targeted = baseIntent().apply { setPackage(target) }
                val launched = if (ShareTargets.isResolvable(context, targeted)) {
                    runCatching {
                        context.startActivity(targeted)
                        true
                    }.getOrDefault(false)
                } else {
                    false
                }
                if (!launched) {
                    if (!ShareTargets.isInstalled(context, target)) {
                        val appName = when (target) {
                            ShareTargets.WECHAT -> context.getString(R.string.detail_share_wechat)
                            ShareTargets.QQ -> context.getString(R.string.detail_share_qq)
                            else -> target
                        }
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.detail_share_target_missing, appName),
                            )
                        }
                    }
                    if (!openChooser()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.detail_share_failed))
                        }
                    }
                }
            } else {
                if (!openChooser()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.detail_share_failed))
                    }
                }
            }
        } finally {
            imageActionPage = -1
            viewModel.clearShareRequest()
        }
    }

    // 与 poipiku 详情页同款背景：渐变 + 三团装饰光斑
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val blobPurple = if (dark) BlobPurpleDark else BlobPurpleLight
            val blobWarm = if (dark) BlobWarmDark else BlobWarmLight
            val blobPink = if (dark) BlobPinkDark else BlobPinkLight
            fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }
            blob(blobPurple, size.width + 10.dp.toPx(), 210.dp.toPx(), 130.dp.toPx())
            blob(blobWarm, 0f, 400.dp.toPx(), 100.dp.toPx())
            blob(blobPink, size.width, 620.dp.toPx(), 90.dp.toPx())
        }

        val detail = state.detail
        // 顶栏是浮层，所有状态下常驻（与 poipiku 详情同构）；内容顶部按顶栏高度让位
        val scrollState = rememberScrollState()
        val scrolled by remember { derivedStateOf { scrollState.value > 0 } }
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            DETAIL_TOP_BAR_HEIGHT + 12.dp

        when {
            state.loading && detail == null -> DetailSkeleton(topInset = topInset)
            state.failed || detail == null -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.home_error_network),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
                Text(
                    text = stringResource(R.string.common_retry),
                    color = PikuColors.accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.load(work, force = true) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            // 小说与插画是两套版式：小说没有图区，正文交给阅读器
            else -> if (work.kind == WorkKind.NOVEL) {
                NovelDetailContent(
                    work = work,
                    detail = detail,
                    stats = state.stats,
                    dark = dark,
                    language = state.language,
                    topInset = topInset,
                    sharedKey = workSharedKey(work.authorId, work.id),
                    customTags = state.customTags.toSet(),
                    novelBodyLoading = state.novelBodyLoading,
                    onReadClick = viewModel::openNovelReader,
                    onTagClick = { tag ->
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.pixiv.net/tags/${Uri.encode(tag)}"),
                                ),
                            )
                        }
                    },
                    onToggleCustomTag = viewModel::toggleCustomTag,
                    onAuthorClick = { onOpenAuthor(work) },
                    isFavorite = state.isFavorite,
                    onBookmarkToggle = { viewModel.toggleFavorite() },
                    onBookmarkLongPress = { favoriteSheetVisible = true },
                    followed = state.followed,
                    showFollow = state.loggedIn && work.source == WorkSource.PIXIV && work.authorId > 0,
                    followSending = state.followSending,
                    onFollowClick = viewModel::toggleFollow,
                    showTranslation = state.showTranslationAll,
                    translating = state.translating,
                    onToggleTranslation = viewModel::onTopBarTranslateClick,
                    onRetranslate = viewModel::onRetranslate,
                    showTranslatedTags = state.showTranslatedTags,
                    tagsTranslating = state.tagsTranslating,
                    onToggleTagsTranslation = viewModel::onToggleTagsTranslation,
                )
            } else {
                // pixiv 走自己的版面：图通栏置顶 + 概览卡（计数/元信息），与 poipiku 互不干涉
                PixivDetailContent(
                    detail = detail,
                    stats = state.stats,
                    dark = dark,
                    sharedKey = workSharedKey(work.authorId, work.id),
                    language = state.language,
                    scrollState = scrollState,
                    topInset = topInset,
                    sourceThumbnailUrl = work.thumbnailUrl,
                    onImageShown = { page, painter -> shownPainters[page] = painter },
                    // 点击随时放行：viewerImages 在页表没回来时用屏上打底图兜底，页表到了自动补全
                    onImageClick = { page ->
                        val images = viewModel.viewerImages
                        if (images.isNotEmpty()) {
                            val item = images.getOrNull(page)
                            Log.d(
                                "PikuDiag",
                                "viewer open page=$page pagesReady=${state.pages.isNotEmpty()} " +
                                    "underlay=${item?.thumbnailUrl} full=${item?.fullUrl}",
                            )
                            viewerPage = page
                        }
                    },
                    // 长按给保存/分享用，要等页表
                    onImageLongPress = { page -> if (state.pages.isNotEmpty()) imageActionPage = page },
                    onTagClick = { tag ->
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.pixiv.net/tags/${Uri.encode(tag)}"),
                                ),
                            )
                        }
                    },
                    customTags = state.customTags.toSet(),
                    onToggleCustomTag = viewModel::toggleCustomTag,
                    onAuthorClick = { onOpenAuthor(work) },
                    hasImageModel = state.hasImageModel,
                    imageTranslated = state.showTranslatedImage,
                    imageTranslatingPage = state.imageTranslatingPage,
                    translatedImages = state.translatedImages,
                    onImageTranslateClick = viewModel::onImageTranslateClick,
                    onPageChanged = viewModel::onImagePageChanged,
                    showTranslation = state.showTranslationAll,
                    translating = state.translating,
                    onToggleTranslation = viewModel::onTopBarTranslateClick,
                    onRetranslate = viewModel::onRetranslate,
                    showTranslatedTags = state.showTranslatedTags,
                    tagsTranslating = state.tagsTranslating,
                    onToggleTagsTranslation = viewModel::onToggleTagsTranslation,
                    related = state.related,
                    onRelatedClick = onRelatedClick,
                    // 收藏与关注收进概览卡本体，不用悬浮条——相关作品网格完整可见
                    isFavorite = state.isFavorite,
                    followed = state.followed,
                    showFollow = state.loggedIn && work.source == WorkSource.PIXIV && work.authorId > 0,
                    followSending = state.followSending,
                    onBookmarkToggle = viewModel::toggleFavorite,
                    onBookmarkLongPress = { favoriteSheetVisible = true },
                    onFollowClick = viewModel::toggleFollow,
                )
            }
        }

        if (imageActionPage >= 0) {
            val wechatInstalled = remember(context) { ShareTargets.isInstalled(context, ShareTargets.WECHAT) }
            val qqInstalled = remember(context) { ShareTargets.isInstalled(context, ShareTargets.QQ) }
            val wechatIcon = remember(context) { ShareTargets.appIcon(context, ShareTargets.WECHAT) }
            val qqIcon = remember(context) { ShareTargets.appIcon(context, ShareTargets.QQ) }
            ImageActionSheet(
                dark = dark,
                imageCount = state.pages.size,
                sharingImage = state.sharingImage,
                sharingTargetPackage = state.sharingTargetPackage,
                wechatInstalled = wechatInstalled,
                qqInstalled = qqInstalled,
                wechatIcon = wechatIcon,
                qqIcon = qqIcon,
                // 用户中途划掉面板 = 取消分享：中断下载，避免关了面板分享面板又弹出来
                onDismiss = {
                    if (state.sharingImage) viewModel.cancelShare()
                    imageActionPage = -1
                },
                onSave = {
                    val page = imageActionPage
                    imageActionPage = -1
                    requestSaveImage(page)
                },
                onShareToWechat = {
                    if (imageActionPage >= 0) viewModel.shareImage(imageActionPage, ShareTargets.WECHAT)
                },
                onShareToQQ = {
                    if (imageActionPage >= 0) viewModel.shareImage(imageActionPage, ShareTargets.QQ)
                },
                onShareMore = {
                    if (imageActionPage >= 0) viewModel.shareImage(imageActionPage, null)
                },
                onSaveAll = {
                    imageActionPage = -1
                    requestSaveAllImages()
                },
            )
        }

        // 相关作品点开 = 导航层压栈新的 SOURCE_DETAIL（每层独立 VM），本层不再自己叠浮层

        // 层级与 poipiku 详情一致：顶栏先渲染，看图器盖过它，Snackbar 盖过看图器
        DetailTopBar(
            onBack = onBack,
            onHomeClick = onHomeClick,
            dark = dark,
            modifier = Modifier.align(Alignment.TopCenter),
            scrolled = scrolled,
            title = detail?.translated?.title
                ?.takeIf { state.showTranslationAll }
                ?: detail?.title.orEmpty(),
            titleVisible = scrolled,
        )
        ViewerOverlay(
            page = viewerPage.takeIf { it >= 0 },
            images = viewModel.viewerImages,
            previews = shownPainters,
            dark = dark,
            onClose = { viewerPage = -1 },
            onLongPressImage = { page -> imageActionPage = page },
            hasImageModel = state.hasImageModel,
            imageTranslating = state.imageTranslatingPage != null,
            imageTranslated = state.showTranslatedImage,
            translatedImages = state.translatedImages,
            onImageTranslateClick = viewModel::onImageTranslateClick,
            onPageChanged = viewModel::onImagePageChanged,
            hdPages = state.hdPages,
            onHdToggle = viewModel::onHdToggle,
        )
        // 阅读器盖在顶栏与详情之上，与 poipiku 详情页同级
        if (state.novelReaderOpen && state.novelText.isNotBlank()) {
            FullNovelViewer(
                text = state.novelText,
                title = detail?.translated?.title
                    ?.takeIf { state.showTranslationAll && it.isNotBlank() }
                    ?: detail?.title.orEmpty(),
                fontSize = state.novelFontSize,
                light = state.novelReaderLight,
                initialPercent = state.novelProgressPercent,
                onProgressSave = viewModel::saveNovelProgress,
                onFontSizeChange = viewModel::setNovelFontSize,
                onLightChange = viewModel::setNovelReaderLight,
                onClose = viewModel::closeNovelReader,
                onWorkClick = { _, _, _ -> },
            )
        }
        FeedbackHost(channel = viewModel.feedback, snackbarHostState = snackbarHostState)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (favoriteSheetVisible && state.detail != null) {
            FavoriteSheet(
                folders = state.favoriteFolders,
                selectedFolderIds = state.workFavoriteFolderIds,
                dark = dark,
                onToggleFolder = viewModel::toggleFavoriteFolder,
                onCreateFolder = viewModel::createFavoriteFolder,
                onDismiss = { favoriteSheetVisible = false },
            )
        }
    }
}
