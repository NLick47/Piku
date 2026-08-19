package com.piku.client.ui.detail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.piku.client.ui.theme.LocalDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.compose.material3.LocalTextStyle
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.common.LinkSegment
import com.piku.client.common.LinkText
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.AccentSolid
import com.piku.client.ui.theme.BadgeBgDark
import com.piku.client.ui.theme.BadgeBgLight
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.ControlAccentLight
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
import com.piku.client.ui.theme.FollowTintDark
import com.piku.client.ui.theme.FollowTintLight
import com.piku.client.ui.theme.HomeFrameIcon
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginBackgroundLight
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginCardLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight
import com.piku.client.ui.theme.SoftBorderDark
import com.piku.client.ui.theme.SoftBorderLight
import com.piku.client.ui.theme.StarDark
import com.piku.client.ui.theme.StarLight
import com.piku.client.ui.theme.StarTintDark
import com.piku.client.ui.theme.StarTintLight
import com.piku.client.ui.theme.TameWhiteColorFilter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val dark = LocalDarkTheme.current
    var viewerPage by rememberSaveable { mutableIntStateOf(-1) }
    var reactionSheetVisible by rememberSaveable { mutableStateOf(false) }
    var favoriteSheetVisible by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val linkCopiedMessage = stringResource(R.string.detail_link_copied)
    val descriptionCopiedMessage = stringResource(R.string.detail_description_copied)
    val feedbackMessage = state.reactionFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            snackbarHostState.showSnackbar(feedbackMessage)
            viewModel.clearReactionFeedback()
        }
    }
    val followFeedbackMessage = state.followFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(followFeedbackMessage) {
        if (followFeedbackMessage != null) {
            snackbarHostState.showSnackbar(followFeedbackMessage)
            viewModel.clearFollowFeedback()
        }
    }
    val favoriteFeedbackMessage = state.favoriteFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(favoriteFeedbackMessage) {
        if (favoriteFeedbackMessage != null) {
            snackbarHostState.showSnackbar(favoriteFeedbackMessage)
            viewModel.clearFavoriteFeedback()
        }
    }
    val saveFeedbackMessage = state.saveFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(saveFeedbackMessage) {
        if (saveFeedbackMessage != null) {
            snackbarHostState.showSnackbar(saveFeedbackMessage)
            viewModel.clearSaveFeedback()
        }
    }
    val tagFeedbackMessage = state.tagFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(tagFeedbackMessage) {
        if (tagFeedbackMessage != null) {
            snackbarHostState.showSnackbar(tagFeedbackMessage)
            viewModel.clearTagFeedback()
        }
    }

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
            )
            when {
                state.loading && state.detail == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
                state.errorRes != null && state.detail == null -> {
                    DetailError(errorRes = state.errorRes!!, onRetry = viewModel::retry, dark = dark)
                }
                state.detail != null -> {
                    DetailContent(
                        detail = state.detail!!,
                        dark = dark,
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
            onShareLink = {
                val title = state.detail?.title.orEmpty()
                val text = if (title.isBlank()) state.shareUrl else "$title\n${state.shareUrl}"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            onOpenBrowser = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.shareUrl)))
            },
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
                    )
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

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 方案 B：去掉实时模糊，用接近不透明的纯色模拟玻璃质感
            .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
            .drawBehind {
                drawLine(
                    color = if (dark) PillBorderDark.copy(alpha = 0.6f) else PillBorderLight,
                    start = Offset(0f, size.height - 0.5.dp.toPx()),
                    end = Offset(size.width, size.height - 0.5.dp.toPx()),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.detail_back),
                tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onHomeClick) {
            Icon(
                imageVector = HomeFrameIcon,
                contentDescription = stringResource(R.string.detail_home),
                tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DetailBottomBar(
    isFavorite: Boolean,
    reactionCount: Int,
    followed: Boolean,
    onFavoriteClick: () -> Unit,
    onFavoriteLongPress: () -> Unit,
    onReactionClick: () -> Unit,
    onFollowClick: () -> Unit,
    dark: Boolean,
    onCopyLink: () -> Unit,
    onCopyDescription: () -> Unit,
    onShareLink: () -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pill = RoundedCornerShape(24.dp)
    // 收藏时的星标弹跳
    val favoriteScale = remember { Animatable(1f) }
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            favoriteScale.snapTo(1.35f)
            favoriteScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(10.dp, pill, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
                .clip(pill)
                .background(if (dark) Color(0xE63A3834) else Color(0xE6FFFFFF))
                .border(
                    BorderStroke(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight),
                    pill,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBarAction(
                onClick = onFavoriteClick,
                onLongPress = onFavoriteLongPress,
                dark = dark,
                active = isFavorite,
                activeTint = if (dark) StarTintDark else StarTintLight,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.detail_favorite),
                    tint = if (isFavorite) {
                        if (dark) StarDark else StarLight
                    } else {
                        if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight
                    },
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = favoriteScale.value
                            scaleY = favoriteScale.value
                        },
                )
            }
            DetailBarAction(
                onClick = onFollowClick,
                dark = dark,
                active = followed,
                activeTint = if (dark) FollowTintDark else FollowTintLight,
            ) {
                Icon(
                    imageVector = if (followed) Icons.Filled.Person else Icons.Outlined.PersonAdd,
                    contentDescription = stringResource(R.string.detail_follow),
                    tint = if (followed) {
                        if (dark) FollowDark else FollowLight
                    } else {
                        if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            DetailBarAction(
                onClick = onReactionClick,
                dark = dark,
                badge = formatReactionCount(reactionCount),
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.detail_reaction_title),
                    tint = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box {
                DetailBarAction(
                    onClick = { menuExpanded = true },
                    dark = dark,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.detail_more),
                        tint = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (menuExpanded) {
                    MoreMenuPopup(
                        dark = dark,
                        onDismiss = { menuExpanded = false },
                        onCopyLink = {
                            menuExpanded = false
                            onCopyLink()
                        },
                        onCopyDescription = {
                            menuExpanded = false
                            onCopyDescription()
                        },
                        onShareLink = {
                            menuExpanded = false
                            onShareLink()
                        },
                        onOpenBrowser = {
                            menuExpanded = false
                            onOpenBrowser()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBarAction(
    onClick: () -> Unit,
    dark: Boolean,
    active: Boolean = false,
    activeTint: Color = Color.Transparent,
    badge: String? = null,
    onLongPress: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        label = "barActionPress",
    )
    val interaction = if (onLongPress != null) {
        // 长按手势：单击在抬手时立即触发（无延迟），按住超过阈值触发长按并吞掉单击
        Modifier.tapOrLongPress(
            onTap = onClick,
            onLongPress = onLongPress,
            onPressChanged = { pressed = it },
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = Modifier.size(44.dp),
    ) {
        Box(
            modifier = interaction
                .matchParentSize()
                .clip(CircleShape)
                .background(if (active) activeTint else Color.Transparent),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        ) {
            icon()
        }
        if (badge != null) {
            Text(
                text = badge,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (dark) BadgeBgDark else BadgeBgLight,
                    )
                    .border(
                        BorderStroke(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight),
                        RoundedCornerShape(9.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
            )
        }
    }
}

/** 反应数紧凑展示：≥1000 缩写为 k 形式，去掉多余的 .0 */
private fun formatReactionCount(count: Int): String {
    if (count < 1000) return count.toString()
    val base = count / 1000f
    val num = if (base >= 100f) {
        base.toInt().toString()
    } else {
        "%.1f".format(Locale.US, base).trimEnd('0').trimEnd('.')
    }
    return "${num}k"
}

private const val GUIDE_HINT_MILLIS = 6_000L

/** 收到反应面板最多展示的表情种类数，防止表情过多撑满面板 */
private const val MAX_VISIBLE_REACTION_TYPES = 8

/** 底部菜单一次性新手引导：首次进入详情页时在菜单上方短暂展示按钮含义 */
@Composable
private fun BottomBarGuideHint(
    dark: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "guideHintAlpha",
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(GUIDE_HINT_MILLIS)
        visible = false
        delay(220)
        onDismiss()
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 72.dp)
            .graphicsLayer { this.alpha = alpha }
            .shadow(6.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
            .clip(shape)
            .background(if (dark) Color(0xE6242321) else Color(0xF2FFFFFF))
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x59FFFFFF) else Color(0x59C8C2B8)),
                shape,
            )
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GuideHintItem(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (dark) StarDark else StarLight,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = stringResource(R.string.detail_favorite),
                dark = dark,
            )
            GuideHintItem(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = stringResource(R.string.detail_follow),
                dark = dark,
            )
            GuideHintItem(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = stringResource(R.string.detail_reaction),
                dark = dark,
            )
            GuideHintItem(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = null,
                        tint = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = stringResource(R.string.detail_more),
                dark = dark,
            )
        }
    }
}

@Composable
private fun GuideHintItem(
    icon: @Composable () -> Unit,
    label: String,
    dark: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

/**
 * 单击 + 长按统一手势：
 * - 单击：抬手时立即触发（不像 combinedClickable 那样等长按超时）
 * - 长按：按住超过系统阈值触发，同时吞掉后续抬手事件，避免再触发单击
 */
private fun Modifier.tapOrLongPress(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPressChanged: (Boolean) -> Unit,
): Modifier = composed {
    val viewConfiguration = LocalViewConfiguration.current
    val gestureScope = rememberCoroutineScope()
    pointerInput(onTap, onLongPress) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPressChanged(true)
            var longPressFired = false
            val job = gestureScope.launch {
                delay(viewConfiguration.longPressTimeoutMillis)
                longPressFired = true
                down.consume()
                onLongPress()
            }
            val up = waitForUpOrCancellation()
            job.cancel()
            when {
                longPressFired -> if (up != null) up.consume()
                up != null -> {
                    up.consume()
                    onTap()
                }
            }
            onPressChanged(false)
        }
    }
}

@Composable
private fun DetailContent(
    detail: WorkDetail,
    dark: Boolean,
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
) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
    ) {
        AuthorRow(detail = detail, dark = dark, onAuthorClick = onAuthorClick)
        if (detail.authorProfile.isNotBlank()) {
            Text(
                text = linkify(detail.authorProfile, dark, onRelatedWorkClick),
                color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
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
        )
        Spacer(Modifier.height(14.dp))
        if (detail.title.isNotBlank()) {
            val titleSelection = remember { SelectionState() }
            SelectionContainer(
                state = titleSelection,
                modifier = Modifier.pointerInput(titleSelection) {
                    detectTapGestures(onTap = { titleSelection.clear() })
                },
            ) {
                Text(
                    text = linkify(detail.title, dark, onRelatedWorkClick),
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (detail.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            val descriptionSelection = remember { SelectionState() }
            val textMeasurer = rememberTextMeasurer()
            val linkifiedDescription = linkify(detail.description, dark, onRelatedWorkClick)
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
                    color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
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
            if (collapsible) {
                Text(
                    text = stringResource(
                        if (descriptionExpanded) R.string.detail_show_less else R.string.detail_show_more
                    ),
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 8.dp)
                        .clickable { descriptionExpanded = !descriptionExpanded },
                )
            }
        }
        if (detail.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TagFlow(
                tags = detail.tags,
                customTags = customTags,
                dark = dark,
                onTagClick = onTagClick,
                onToggleCustomTag = onToggleCustomTag,
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
                .background(if (dark) LoginCardDark else LoginCardLight)
                .clickable(onClick = onAuthorClick),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = detail.authorName,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
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
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (dark) LoginCardDark else Color(0xFFF1EFEA))
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
) {
    val pagerState = rememberPagerState(pageCount = { detail.imageUrls.size })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (dark) LoginCardDark else Color(0xFFF1EFEA)),
    ) {
        if (detail.imageUrls.isEmpty()) {
            when {
                detail.adultLocked -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.detail_adult_locked),
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 12.sp,
                        )
                    }
                }
                detail.novelText.isNotBlank() -> {
                    NovelView(detail = detail, dark = dark, onWorkClick = onWorkClick)
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
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                AsyncImage(
                    model = detail.imageUrls[page],
                    contentDescription = detail.title,
                    colorFilter = if (dark) TameWhiteColorFilter else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = { onImageClick(page) },
                            onLongClick = { onImageLongPress(page) },
                        ),
                    contentScale = ContentScale.Fit,
                )
            }
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
        }
    }
}

@Composable
private fun NovelView(detail: WorkDetail, dark: Boolean, onWorkClick: (Long, Long, String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = linkify(detail.novelText, dark, onWorkClick),
            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
            fontSize = 13.sp,
            lineHeight = 22.sp,
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
            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
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
                focusedContainerColor = if (dark) LoginCardDark else Color(0xFFEAE8E3),
                unfocusedContainerColor = if (dark) LoginCardDark else Color(0xFFEAE8E3),
                focusedBorderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                unfocusedBorderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                cursorColor = AccentDark,
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
                color = Color(0xFFD64545),
                fontSize = 12.sp,
            )
        }
        if (blocked) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = blockedMessage.ifBlank { stringResource(R.string.detail_unlock_blocked) },
                color = Color(0xFFD64545),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val COLS = 2
private const val ROWS = 2
private const val CARD_IMAGE_HEIGHT = 220
private const val CARD_TEXT_HEIGHT = 60
private const val CARD_HEIGHT = CARD_IMAGE_HEIGHT + CARD_TEXT_HEIGHT

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlow(
    tags: List<String>,
    customTags: Set<String>,
    dark: Boolean,
    onTagClick: (String) -> Unit,
    onToggleCustomTag: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            val added = tag in customTags
            val shape = RoundedCornerShape(12.dp)
            // 加入/移除时的图标弹跳
            val tagIconScale = remember { Animatable(1f) }
            LaunchedEffect(added) {
                tagIconScale.snapTo(0.5f)
                tagIconScale.animateTo(1.25f, tween(140, easing = LinearOutSlowInEasing))
                tagIconScale.animateTo(1f, tween(100))
            }
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(if (dark) LoginCardDark else Color(0xFFF1EFEA))
                    .border(
                        BorderStroke(
                            0.5.dp,
                            if (added) {
                                if (dark) LoginTextPrimaryDark else AccentDark
                            } else {
                                if (dark) LoginCardBorderDark else LoginCardBorderLight
                            },
                        ),
                        shape,
                    )
                    .clickable { onTagClick(tag) }
                    .padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#$tag",
                    color = if (dark) LoginTextSecondaryDark else AccentDark,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(2.dp))
                // 加入/移出个人标签：已添加显示 ✓，未添加显示 +
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (added) {
                                if (dark) LoginTextPrimaryDark.copy(alpha = 0.16f) else AccentDark.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onToggleCustomTag(tag) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (added) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = stringResource(
                            if (added) R.string.detail_tag_remove_from_my_tags
                            else R.string.detail_tag_add_to_my_tags,
                        ),
                        tint = if (added) {
                            if (dark) LoginTextPrimaryDark else AccentDark
                        } else {
                            if (dark) LoginTextFaintDark else LoginTextFaintLight
                        },
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer {
                                scaleX = tagIconScale.value
                                scaleY = tagIconScale.value
                            },
                    )
                }
            }
        }
    }
}

private val SEND_EMOJIS = listOf("💖", "❤", "👍", "👏", "💯", "🥰", "😍", "💞", "🫶", "🎉")

/** 描述折叠阈值：全文行数超过该值才折叠为 3 行，避免展开只多出一两行的尴尬 */
private const val DESCRIPTION_COLLAPSE_THRESHOLD = 4

private val POIPIKU_WORK_REGEX = Regex("""https?://poipiku\.com/(\d+)/(\d+)\.html""")

@Composable
private fun linkify(
    raw: String,
    dark: Boolean,
    onWorkClick: (Long, Long, String) -> Unit,
): AnnotatedString {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)
    val currentOnWorkClick by rememberUpdatedState(onWorkClick)
    return remember(raw, dark) {
        val style = SpanStyle(
            color = if (dark) ControlAccentDark else ControlAccentLight,
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

/** 更多操作：锚定在按钮上方的玻璃风格小弹窗，比系统菜单精致、比整页弹层省空间 */
@Composable
private fun MoreMenuPopup(
    dark: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyDescription: () -> Unit,
    onShareLink: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    val density = LocalDensity.current
    var offsetY by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(18.dp)
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .onSizeChanged { size ->
                    offsetY = -size.height - with(density) { 8.dp.roundToPx() }
                }
                .shadow(14.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x55000000))
                .clip(shape)
                .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
                .border(
                    BorderStroke(0.5.dp, if (dark) Color(0x59FFFFFF) else Color(0x59C8C2B8)),
                    shape,
                )
                .padding(vertical = 6.dp),
        ) {
            MoreMenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = if (dark) LoginTextPrimaryDark else AccentDark,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = stringResource(R.string.detail_copy_link),
                dark = dark,
                onClick = onCopyLink,
            )
            MoreMenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = if (dark) LoginTextPrimaryDark else AccentDark,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = stringResource(R.string.detail_copy_description),
                dark = dark,
                onClick = onCopyDescription,
            )
            MoreMenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = if (dark) LoginTextPrimaryDark else AccentDark,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = stringResource(R.string.detail_share_link),
                dark = dark,
                onClick = onShareLink,
            )
            MoreMenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.OpenInBrowser,
                        contentDescription = null,
                        tint = if (dark) LoginTextPrimaryDark else AccentDark,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = stringResource(R.string.detail_open_browser),
                dark = dark,
                onClick = onOpenBrowser,
            )
        }
    }
}

@Composable
private fun MoreMenuRow(
    icon: @Composable () -> Unit,
    label: String,
    dark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(
            text = label,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 13.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteSheet(
    folders: List<FavoriteFolder>,
    selectedFolderIds: Set<Long>,
    dark: Boolean,
    onToggleFolder: (Long) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var creatingNew by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else LoginBackgroundLight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.detail_favorite_sheet_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.detail_favorite_sheet_hint),
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            folders.forEach { folder ->
                FavoriteFolderRow(
                    folder = folder,
                    selected = folder.id in selectedFolderIds,
                    onClick = { onToggleFolder(folder.id) },
                    dark = dark,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (creatingNew) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.detail_favorite_new_hint),
                            fontSize = 13.sp,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (dark) LoginCardDark else Color(0xFFEAE8E3),
                        unfocusedContainerColor = if (dark) LoginCardDark else Color(0xFFEAE8E3),
                        focusedBorderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                        unfocusedBorderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                        cursorColor = AccentDark,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                onCreateFolder(newFolderName.trim())
                                newFolderName = ""
                                creatingNew = false
                            }
                        },
                        enabled = newFolderName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (dark) LoginTextPrimaryDark else AccentSolid,
                            contentColor = if (dark) LoginBackgroundDark else Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.detail_favorite_create), fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = {
                            newFolderName = ""
                            creatingNew = false
                        },
                    ) {
                        Text(stringResource(R.string.detail_favorite_cancel), fontSize = 13.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { creatingNew = true }) {
                    Text(
                        text = stringResource(R.string.detail_favorite_new_folder),
                        color = if (dark) LoginTextSecondaryDark else LoginTextPrimaryLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteFolderRow(
    folder: FavoriteFolder,
    selected: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (selected && dark) 4.dp else 0.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
            .clip(shape)
            .background(
                when {
                    selected && dark -> Color(0x26E0E0E0)
                    selected -> AccentDark.copy(alpha = 0.12f)
                    else -> Color.Transparent
                },
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) {
                        if (dark) ControlAccentDark else ControlAccentLight
                    } else {
                        Color.Transparent
                    },
                ),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = if (selected) {
                if (dark) StarDark else StarLight
            } else {
                if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = folder.name,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (folder.isDefault) {
            Spacer(Modifier.width(6.dp))
            DefaultFolderBadge(dark = dark)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = folder.workCount.toString(),
            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
            fontSize = 12.sp,
        )
    }
}

/** 「默认」小徽标：标识快速收藏的落点收藏夹。 */
@Composable
private fun DefaultFolderBadge(dark: Boolean) {
    Text(
        text = stringResource(R.string.collection_default_badge),
        color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (dark) Color(0x22FFFFFF) else Color(0x142C2C2C))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReactionSheet(
    detail: WorkDetail,
    dark: Boolean,
    loggedIn: Boolean,
    sending: Boolean,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else LoginBackgroundLight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_reaction_title),
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (detail.reactionCount > 0) {
                    Text(
                        text = stringResource(R.string.detail_reaction_total, detail.reactionCount),
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        fontSize = 12.sp,
                    )
                }
            }
            if (detail.reactionCount > 0) {
                Spacer(Modifier.height(16.dp))
                val entries = detail.reactionCounts.entries.sortedByDescending { it.value }
                val visible = entries.take(MAX_VISIBLE_REACTION_TYPES)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visible.forEach { (emoji, count) ->
                        ReceivedReactionPill(emoji = emoji, count = count, dark = dark)
                    }
                }
                if (entries.size > visible.size) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.detail_reaction_more, entries.size - visible.size),
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.detail_reaction_empty),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 13.sp,
                )
            }
            if (loggedIn) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.detail_reaction_send_title),
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                ReactionSendRow(sending = sending, received = detail.reactionCounts, onSend = onSend)
            } else {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.detail_reaction_login_hint),
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivedReactionPill(emoji: String, count: Int?, dark: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (dark) LoginCardDark else Color(0xFFF1EFEA))
            .border(
                BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                shape,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = emoji, fontSize = 14.sp)
        if (count != null) {
            Text(
                text = count.toString(),
                color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionSendRow(
    sending: Boolean,
    received: Map<String, Int>,
    onSend: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.graphicsLayer { alpha = if (sending) 0.45f else 1f },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SEND_EMOJIS.forEach { emoji ->
            ReactionSendButton(
                emoji = emoji,
                enabled = !sending,
                highlighted = received.containsKey(emoji),
                onClick = { onSend(emoji) },
            )
        }
    }
}

@Composable
private fun ReactionSendButton(
    emoji: String,
    enabled: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var popped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else if (popped) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "reactionSendScale",
    )
    LaunchedEffect(popped) {
        if (popped) {
            delay(400)
            popped = false
        }
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(AccentDark.copy(alpha = 0.12f))
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (highlighted) AccentDark.copy(alpha = 0.55f) else Color.Transparent,
                ),
                CircleShape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
            ) {
                popped = true
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 17.sp)
    }
}

@Composable
private fun RelatedWorksSection(
    works: List<Work>,
    dark: Boolean,
    onClick: (Long, Long, String) -> Unit,
) {
    val pages = (works.size + COLS * ROWS - 1) / (COLS * ROWS)
    val pagerState = rememberPagerState(pageCount = { pages })
    Column {
        Text(
            text = stringResource(R.string.detail_related_works_count, works.size),
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Box {
            HorizontalPager(
                state = pagerState,
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RelatedWorkRow(works, page * 4, dark, onClick)
                    RelatedWorkRow(works, page * 4 + 2, dark, onClick)
                }
            }
            if (pages > 1) {
                Text(
                    text = stringResource(
                        R.string.detail_image_index,
                        pagerState.currentPage + 1,
                        pages,
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
        }
        if (pages > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages) { page ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    page == pagerState.currentPage && dark -> ControlAccentDark
                                    page == pagerState.currentPage -> AccentDark
                                    dark -> LoginTextFaintDark
                                    else -> LoginTextFaintLight
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedWorkRow(
    works: List<Work>,
    startIndex: Int,
    dark: Boolean,
    onClick: (Long, Long, String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (startIndex until startIndex + COLS).forEach { index ->
            if (index < works.size) {
                RelatedWorkCard(
                    work = works[index],
                    dark = dark,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(
                    Modifier
                        .weight(1f)
                        .height(CARD_HEIGHT.dp),
                )
            }
        }
    }
}

@Composable
private fun RelatedWorkCard(
    work: Work,
    dark: Boolean,
    onClick: (Long, Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .shadow(elevation = 3.dp, shape = shape)
            .background(if (dark) LoginCardDark else LoginCardLight)
            .border(
                BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                shape,
            )
            .clickable { onClick(work.authorId, work.id, work.thumbnailUrl) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_IMAGE_HEIGHT.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(if (dark) LoginCardDark else Color(0xFFF1EFEA)),
        ) {
            // 底层：同图铺满格子 + 放大 + 模糊 + 压暗，把固定尺寸的留白变成毛玻璃衬底
            AsyncImage(
                model = work.thumbnailUrl,
                contentDescription = null,
                colorFilter = if (dark) TameWhiteColorFilter else null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.15f)
                    .blur(24.dp),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (dark) Color(0x59000000) else Color(0x40000000)),
            )
            // 顶层：原图完整呈现，不裁剪
            AsyncImage(
                model = work.thumbnailUrl,
                contentDescription = work.title,
                colorFilter = if (dark) TameWhiteColorFilter else null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
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
                        .background(Color(0x99000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(
            Modifier
                .padding(horizontal = 8.dp, vertical = 7.dp)
                .height(CARD_TEXT_HEIGHT.dp),
        ) {
            if (work.title.isNotBlank()) {
                Text(
                    text = work.title,
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                text = work.authorName,
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailError(errorRes: Int, onRetry: () -> Unit, dark: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(errorRes),
            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
            fontSize = 13.sp,
        )
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
