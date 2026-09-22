package com.piku.client.ui.detail

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.piku.client.ui.common.FeedbackHost
import com.piku.client.ui.common.PikuBottomSheet
import com.piku.client.ui.common.PikuSheetTitle
import com.piku.client.ui.navigation.workSharedKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.data.local.ShareTargets
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

/** 顶栏标题淡入所需的滚动距离：大致等于滚过图区上沿 */
private const val TITLE_REVEAL_SCROLL_DP = 180

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onRelatedWorkClick: (Long, Long, String) -> Unit,
    onAuthorClick: (Long, String) -> Unit,
) {
    val viewModel: DetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val catalogModels by viewModel.catalogModels.collectAsStateWithLifecycle()
    val shareRequest by viewModel.shareRequest.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // 下滑越过图区上沿后，顶栏淡入作品标题，避免长内容页面滚着滚着失去上下文
    val titleVisible by remember(density) {
        derivedStateOf { scrollState.value > with(density) { TITLE_REVEAL_SCROLL_DP.dp.toPx() } }
    }
    // 标题跟随原/译状态，与正文里的标题保持同一份文案
    val detailTitle = state.detail?.let { detail ->
        val showTranslated = state.showTranslation(TranslateField.TITLE)
        detail.translated?.title
            ?.takeIf { showTranslated && it.isNotBlank() }
            ?: detail.title
    }.orEmpty()
    var viewerPage by rememberSaveable { mutableIntStateOf(-1) }
    var reactionSheetVisible by rememberSaveable { mutableStateOf(false) }
    var favoriteSheetVisible by rememberSaveable { mutableStateOf(false) }
    // 屏蔽作者前的二次确认：屏蔽会连带解除关注，误触有代价，故比其他菜单项多一步
    var blockConfirmVisible by rememberSaveable { mutableStateOf(false) }
    // 长按命中的页码：分享期间面板保持打开（loading 转圈），成功/失败后才关闭；
    // 必须声明在分享 LaunchedEffect 之前，effect 内要把它置 -1 关面板
    var imageActionPage by rememberSaveable { mutableIntStateOf(-1) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val linkCopiedMessage = stringResource(R.string.detail_link_copied)
    val descriptionCopiedMessage = stringResource(R.string.detail_description_copied)

    // 分享图片：面板在下载期间保持打开（loading 转圈在被点的那一行），
    // 下载完成后才拉起分享；定向先查可解析（微信常不接通用 ACTION_SEND），
    // 不可解析时静静回落系统面板——只有确认未安装才提示，避免装了也误报。
    // 成功/失败（含 chooser 都打不开）一律 finally 关面板清请求，不卡死。
    LaunchedEffect(shareRequest) {
        val request = shareRequest ?: return@LaunchedEffect
        val target = request.targetPackage
        fun baseIntent(): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, request.uri)
            // 部分应用（比如微信）带图分享时会吞掉 EXTRA_TEXT，链接可能发不过去；
            // 先带上（能接的则接），不做强保证
            if (request.shareText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, request.shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newUri(context.contentResolver, "shared_image", request.uri)
        }
        // ClipData + FLAG 授权已足够定向目标读取，不再手动 grantUriPermission（免泄漏）
        fun openChooser(): Boolean = runCatching {
            context.startActivity(Intent.createChooser(baseIntent(), null))
            true
        }.getOrDefault(false)
        fun failedMessage(): String = context.getString(R.string.detail_share_failed)
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
                        scope.launch { snackbarHostState.showSnackbar(failedMessage()) }
                    }
                }
            } else {
                if (!openChooser()) {
                    scope.launch { snackbarHostState.showSnackbar(failedMessage()) }
                }
            }
        } finally {
            imageActionPage = -1
            viewModel.clearShareRequest()
        }
    }

    FeedbackHost(channel = viewModel.feedback, snackbarHostState = snackbarHostState)
    // 分享准备失败：面板仍开着（loading 刚结束），先收起面板，否则 snackbar 被 BottomSheet 盖住
    LaunchedEffect(Unit) {
        viewModel.shareSheetDismiss.collect { imageActionPage = -1 }
    }

    // 长按图片 → 弹出操作面板；面板中保存/分享时处理权限
    val savePermissionMessage = stringResource(R.string.detail_save_permission_denied)
    var pendingSavePage by rememberSaveable { mutableIntStateOf(-1) }
    var pendingSaveAll by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        when {
            granted && pendingSavePage >= 0 -> viewModel.saveImage(pendingSavePage)
            granted && pendingSaveAll -> viewModel.saveAllImages()
            !granted && (pendingSavePage >= 0 || pendingSaveAll) ->
                scope.launch { snackbarHostState.showSnackbar(savePermissionMessage) }
        }
        pendingSavePage = -1
        pendingSaveAll = false
    }
    fun hasSavePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
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
            blob(blobPurple, size.width - 40.dp.toPx(), 96.dp.toPx(), 120.dp.toPx())
            blob(blobWarm, 0f, 400.dp.toPx(), 100.dp.toPx())
            blob(blobPink, size.width, 620.dp.toPx(), 90.dp.toPx())
        }
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(
                onBack = onBack,
                onHomeClick = onHomeClick,
                dark = dark,
                title = detailTitle,
                titleVisible = titleVisible,
                translationAvailable = state.hasTranslation,
                showTranslation = state.showTranslationAll,
                translating = state.translating,
                canTranslate = state.canTranslate,
                onTranslateClick = viewModel::onTopBarTranslateClick,
                onOpenModelPicker = viewModel::openModelPicker,
            )
            when {
                state.loading && state.detail == null -> {
                    DetailSkeleton()
                }
                state.errorRes != null && state.detail == null -> {
                    DetailError(
                        errorRes = state.errorRes!!,
                        hintRes = state.errorHintRes,
                        retryable = state.errorRetryable,
                        onRetry = viewModel::retry,
                        dark = dark,
                    )
                }
                state.detail != null -> {
                    DetailContent(
                        detail = state.detail!!,
                        dark = dark,
                        sharedKey = workSharedKey(viewModel.authorId, viewModel.workId),
                        scrollState = scrollState,
                        onImageClick = { page -> viewerPage = page },
                        onImageLongPress = { page -> imageActionPage = page },
                        password = state.password,
                        onPasswordChange = viewModel::updatePassword,
                        onPasswordSubmit = viewModel::submitPassword,
                        passwordLoading = state.passwordLoading,
                        onTagClick = onTagClick,
                        onRelatedWorkClick = onRelatedWorkClick,
                        onAuthorClick = {
                            onAuthorClick(viewModel.authorId, state.detail!!.authorName)
                        },
                        customTags = state.customTags.toSet(),
                        onToggleCustomTag = viewModel::toggleCustomTag,
                        onOpenNovelReader = { viewModel.setNovelReaderOpen(true) },
                        hasImageModel = state.hasImageModel,
                        imageTranslated = state.showTranslatedImage,
                        imageTranslatingPage = state.imageTranslatingPage,
                        translatedImages = state.translatedImages,
                        onImageTranslateClick = { page -> viewModel.onImageTranslateClick(page) },
                        onPageChanged = { page -> viewModel.onImagePageChanged(page) },
                        translationAvailable = state.hasTranslation,
                        showTranslation = { field -> state.showTranslation(field) },
                        onToggleField = viewModel::toggleField,
                        autoExpandImageHint = state.imageHintVisible,
                        onImageHintShown = viewModel::consumeImageHint,
                    )
                }
            }
        }
        DetailBottomBar(
            isFavorite = state.isFavorite,
            reactionCount = state.detail?.reactionCount ?: 0,
            reacted = state.hasReacted,
            followed = state.detail?.followed == true,
            onFavoriteClick = { if (state.detail != null) viewModel.quickFavorite() },
            onFavoriteLongPress = { if (state.detail != null) favoriteSheetVisible = true },
            onReactionClick = { if (state.detail != null) reactionSheetVisible = true },
            onFollowClick = viewModel::toggleFollow,
            dark = dark,
            onCopyLink = {
                clipboard.setText(AnnotatedString(state.shareUrl))
                scope.launch {
                    snackbarHostState.showSnackbar(linkCopiedMessage)
                }
            },
            onCopyDescription = {
                state.detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    clipboard.setText(AnnotatedString(description))
                    scope.launch {
                        snackbarHostState.showSnackbar(descriptionCopiedMessage)
                    }
                }
            },
            onOpenBrowser = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.shareUrl)))
            },
            onOpenModelPicker = if (state.canTranslate) viewModel::openModelPicker else null,
            blocked = state.detail?.blocked == true,
            onToggleBlock = if (state.isSelf) {
                null
            } else {
                {
                    // 屏蔽有连带解除关注的副作用，先确认；解除屏蔽可直接执行
                    if (state.detail?.blocked == true) viewModel.toggleBlock() else blockConfirmVisible = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        // 屏蔽作者确认：屏蔽会连带解除关注，误触有代价；解除屏蔽可恢复，不弹确认
        if (blockConfirmVisible) {
            AlertDialog(
                onDismissRequest = { blockConfirmVisible = false },
                containerColor = PikuColors.surface,
                title = {
                    Text(
                        text = stringResource(R.string.detail_block_confirm_title),
                        color = PikuColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.detail_block_confirm_message),
                        color = PikuColors.textSecondary,
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        blockConfirmVisible = false
                        viewModel.toggleBlock()
                    }) {
                        Text(
                            text = stringResource(R.string.detail_block_confirm_ok),
                            color = PikuColors.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { blockConfirmVisible = false }) {
                        Text(
                            text = stringResource(R.string.detail_favorite_cancel),
                            color = PikuColors.textSecondary,
                        )
                    }
                },
            )
        }
        if (state.guideVisible && state.detail != null) {
            BottomBarGuideHint(
                dark = dark,
                onDismiss = viewModel::dismissGuide,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        // 全屏查看器放在 SnackbarHost 之前，保证保存结果的 snackbar 能盖在全屏黑底上
        state.detail?.let {
            if (viewerPage >= 0) {
                val images = state.viewerImages
                if (images.isNotEmpty()) {
                    FullScreenViewer(
                        images = images,
                        startPage = viewerPage.coerceAtMost(images.lastIndex),
                        onClose = { viewerPage = -1 },
                        onLongPressImage = { page -> imageActionPage = page },
                        dark = dark,
                        hasImageModel = state.hasImageModel,
                        imageTranslating = state.imageTranslatingPage != null,
                        imageTranslated = state.showTranslatedImage,
                        translatedImages = state.translatedImages,
                        onImageTranslateClick = { page -> viewModel.onImageTranslateClick(page) },
                        onPageChanged = { page -> viewModel.saveImageProgress(page) },
                    )

                }
            }
            if (state.novelReaderOpen && it.novelText.isNotBlank()) {
                val novelTranslated = state.showTranslation(TranslateField.NOVEL)
                val titleTranslated = state.showTranslation(TranslateField.TITLE)
                val translatedNovel = it.translated?.novelText
                // 边翻边读：流式进行中把未译剩余原文拼接在已译前缀之后；
                // 首块完成前（remainder 为空）显示纯原文，避免重复拼接
                val novelBody = if (
                    novelTranslated &&
                    state.novelStreamProgress != null &&
                    translatedNovel != null &&
                    !state.novelRemainder.isNullOrEmpty()
                ) {
                    "$translatedNovel\n\n${state.novelRemainder}"
                } else {
                    translatedNovel
                        ?.takeIf { text -> novelTranslated && text.isNotBlank() }
                        ?: it.novelText
                }
                val showingTranslation = novelTranslated && (
                    translatedNovel?.isNotBlank() == true || state.novelStreamProgress != null
                )
                FullNovelViewer(
                    text = novelBody,
                    title = it.translated?.title
                        ?.takeIf { t -> titleTranslated && t.isNotBlank() }
                        ?: it.title,
                    fontSize = state.novelFontSize,
                    light = state.novelReaderLight,
                    initialPercent = state.novelProgressPercent,
                    onProgressSave = viewModel::saveNovelProgress,
                    onFontSizeChange = viewModel::setNovelFontSize,
                    onLightChange = viewModel::setNovelReaderLight,
                    onClose = { viewModel.setNovelReaderOpen(false) },
                    onWorkClick = onRelatedWorkClick,
                    // 有原文正文就给原/译切换：没翻过时点击会在阅读器内触发拉取（长篇唯一入口）
                    translationAvailable = !it.novelText.isNullOrBlank(),
                    showTranslation = showingTranslation,
                    // 只有本轮真的在拉正文才显示加载态，元数据拉取不误标
                    translating = state.fetchingNovelText,
                    busy = state.translating,
                    novelStreamProgress = state.novelStreamProgress,
                    onToggleTranslation = viewModel::onReaderTranslateToggle,
                )
            }
        }
        if (state.showModelPicker) {
            val models = catalogModels.filter { it.available && !it.apiKey.isNullOrBlank() }
            PikuBottomSheet(
                onDismissRequest = viewModel::dismissModelPicker,
                dark = dark,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    PikuSheetTitle(text = stringResource(R.string.detail_retry_with_model_title))
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    ) {
                        items(models, key = { it.id }) { entry ->
                            ModelPickerRow(
                                entry = entry,
                                dark = dark,
                                onClick = { viewModel.reTranslateWith(entry) },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
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
        if (reactionSheetVisible && state.detail != null) {
            ReactionSheet(
                detail = state.detail!!,
                dark = dark,
                loggedIn = state.loggedIn,
                sending = state.reactionSending,
                onSend = viewModel::sendReaction,
                onDismiss = { reactionSheetVisible = false },
            )
        }
        if (imageActionPage >= 0) {
            val imageCount = state.detail?.imageUrls?.size ?: 0
            val wechatInstalled = remember(context) { ShareTargets.isInstalled(context, ShareTargets.WECHAT) }
            val qqInstalled = remember(context) { ShareTargets.isInstalled(context, ShareTargets.QQ) }
            val wechatIcon = remember(context) { ShareTargets.appIcon(context, ShareTargets.WECHAT) }
            val qqIcon = remember(context) { ShareTargets.appIcon(context, ShareTargets.QQ) }
            ImageActionSheet(
                dark = dark,
                imageCount = imageCount,
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
                // 分享行不预关面板：保持打开显示 loading，LaunchedEffect 拉起分享后才关
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
                showDecorationAction = state.decorationEnabled,
                onAddDecoration = {
                    val page = imageActionPage
                    imageActionPage = -1
                    viewModel.addDecoration(page)
                },
            )
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val pulse = rememberSkeletonPulse()
    val block = PikuColors.textFaint.copy(alpha = 0.22f + 0.34f * pulse.value)
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp),
    ) {
        // 作者行：头像 + 昵称 + 右侧分类位
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(block),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .width(132.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(block),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(52.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(block),
            )
        }
        Spacer(Modifier.height(14.dp))
        // 图区：与图片未量出时的默认占位高一致
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(block),
        )
        Spacer(Modifier.height(16.dp))
        // 标题 + 描述三行
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(block),
        )
        Spacer(Modifier.height(12.dp))
        SkeletonLine(block, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SkeletonLine(block, Modifier.fillMaxWidth(0.94f))
        Spacer(Modifier.height(6.dp))
        SkeletonLine(block, Modifier.fillMaxWidth(0.52f))
        Spacer(Modifier.height(16.dp))
        // 标签两行
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonTag(block, 68.dp)
            SkeletonTag(block, 96.dp)
            SkeletonTag(block, 72.dp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonTag(block, 56.dp)
            SkeletonTag(block, 84.dp)
        }
    }
}

@Composable
private fun SkeletonLine(block: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(block),
    )
}

@Composable
private fun SkeletonTag(block: Color, width: Dp) {
    Box(
        Modifier
            .width(width)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(block),
    )
}

/** 骨架占位的呼吸动画（与抽屉头部骨架同款时序） */
@Composable
private fun rememberSkeletonPulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "detailSkeleton")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "detailSkeletonPulse",
    )
}

/**
 * 详情页加载失败占位。
 *
 * 终态错误（作品被删除/不存在）连「重试」都不给——重试必然再次失败，只会让用户
 * 以为是自己网络不好。返回走顶栏，不再单独放按钮：
 * 曾放过「在浏览器打开」，但作品 404 时浏览器打开的还是同一个 404 页，纯属多余。
 */
@Composable
private fun DetailError(
    errorRes: Int,
    hintRes: Int?,
    retryable: Boolean,
    onRetry: () -> Unit,
    dark: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(errorRes),
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        if (hintRes != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(hintRes),
                color = PikuColors.textSecondary,
                fontSize = 12.sp,
            )
        }
        if (retryable) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        BorderStroke(0.5.dp, PikuColors.border),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_retry),
                    color = PikuColors.textPrimary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
