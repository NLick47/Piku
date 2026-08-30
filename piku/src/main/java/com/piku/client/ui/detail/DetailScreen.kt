package com.piku.client.ui.detail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
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
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val linkCopiedMessage = stringResource(R.string.detail_link_copied)
    val descriptionCopiedMessage = stringResource(R.string.detail_description_copied)
    FeedbackSnackbar(
        message = state.reactionFeedbackRes?.let { stringResource(it) },
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::clearReactionFeedback,
    )
    FeedbackSnackbar(
        message = state.followFeedbackRes?.let { stringResource(it) },
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::clearFollowFeedback,
    )
    FeedbackSnackbar(
        message = state.favoriteFeedbackRes?.let { stringResource(it) },
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::clearFavoriteFeedback,
    )
    FeedbackSnackbar(
        message = state.saveFeedbackRes?.let { stringResource(it) },
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::clearSaveFeedback,
    )
    FeedbackSnackbar(
        message = state.tagFeedbackRes?.let { stringResource(it) },
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::clearTagFeedback,
    )
    // 手动翻译失败：snackbar 轻提示 + 一键重试（自动路径不触发，保持静默）
    FeedbackSnackbar(
        message = state.translateFeedbackRes?.let { stringResource(it) },
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::clearTranslateFeedback,
        actionLabel = stringResource(R.string.detail_translate_retry),
        onAction = viewModel::retryLastTranslate,
    )

    // 长按图片 → 先确认再保存；确认后 API 29+ 免权限直接存，API 26-28 需申请 WRITE_EXTERNAL_STORAGE
    val savePermissionMessage = stringResource(R.string.detail_save_permission_denied)
    var pendingSavePage by rememberSaveable { mutableIntStateOf(-1) }
    var confirmSavePage by rememberSaveable { mutableIntStateOf(-1) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingSavePage >= 0) {
            viewModel.saveImage(pendingSavePage)
        } else if (!granted && pendingSavePage >= 0) {
            scope.launch { snackbarHostState.showSnackbar(savePermissionMessage) }
        }
        pendingSavePage = -1
    }
    fun requestSaveImage(page: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.saveImage(page)
        } else {
            pendingSavePage = page
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
            val blobPurple = if (dark) Color(0x409A7FC9) else Color(0x4D9A7FC9)
            val blobWarm = if (dark) Color(0x33C98A2D) else Color(0x4DC98A2D)
            val blobPink = if (dark) Color(0x33D8A8B8) else Color(0x4DD8A8B8)
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
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
                        scrollState = scrollState,
                        onImageClick = { page -> viewerPage = page },
                        onImageLongPress = { page -> confirmSavePage = page },
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
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
                        onSaveImage = { page -> confirmSavePage = page },
                        dark = dark,
                        hasImageModel = state.hasImageModel,
                        imageTranslating = state.imageTranslatingPage != null,
                        imageTranslated = state.showTranslatedImage,
                        translatedImages = state.translatedImages,
                        onImageTranslateClick = { page -> viewModel.onImageTranslateClick(page) },
                    )

                }
            }
            if (state.novelReaderOpen && it.novelText.isNotBlank()) {
                val novelTranslated = state.showTranslation(TranslateField.NOVEL)
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
                FullNovelViewer(
                    text = novelBody,
                    title = it.translated?.title
                        ?.takeIf { t -> novelTranslated && t.isNotBlank() }
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
                    showTranslation = novelTranslated,
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
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissModelPicker,
                containerColor = if (dark) HomeBgBottomDark else HomeBgBottomLight,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.detail_retry_with_model_title),
                        color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
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
        if (confirmSavePage >= 0) {
            AlertDialog(
                onDismissRequest = { confirmSavePage = -1 },
                containerColor = if (dark) LoginCardDark else Color.White,
                title = {
                    Text(
                        text = stringResource(R.string.detail_save_confirm_title),
                        color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.detail_save_confirm_message),
                        color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val page = confirmSavePage
                        confirmSavePage = -1
                        requestSaveImage(page)
                    }) {
                        Text(
                            text = stringResource(R.string.detail_save_confirm),
                            color = if (dark) LoginTextPrimaryDark else AccentDark,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSavePage = -1 }) {
                        Text(
                            text = stringResource(R.string.search_cancel),
                            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        )
                    }
                },
            )
        }
    }
}

/**
 * 统一的轻提示出口：把「弹 snackbar → 清掉 ViewModel 里的一次性状态」这段样板收拢到一处，
 * 六种反馈（反应/关注/收藏夹/保存/标签/翻译）共用同一套时序。
 * 传了 [actionLabel] 时（翻译失败重试）额外处理动作点击。
 */
@Composable
private fun FeedbackSnackbar(
    message: String?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val currentOnConsumed by rememberUpdatedState(onConsumed)
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
        )
        if (result == SnackbarResult.ActionPerformed) currentOnAction?.invoke()
        currentOnConsumed()
    }
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
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        if (hintRes != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(hintRes),
                color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                fontSize = 12.sp,
            )
        }
        if (retryable) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_retry),
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
