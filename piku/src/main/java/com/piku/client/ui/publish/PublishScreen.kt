package com.piku.client.ui.publish

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.CATEGORY_GROUPS
import com.piku.client.domain.model.NsfwLevel
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.ShowVisibility
import com.piku.client.domain.model.UploadKind
import androidx.compose.foundation.gestures.detectTapGestures
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.PikuBottomSheet
import com.piku.client.ui.common.PikuSheetHandle
import com.piku.client.ui.common.PikuSheetTitle
import com.piku.client.ui.common.SkeletonBlock
import com.piku.client.ui.common.rememberSkeletonPulse
import com.piku.client.ui.theme.ErrorRedDark
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.GlassCardBgLight
import com.piku.client.ui.theme.GlassCardBorderDark
import com.piku.client.ui.theme.GlassCardBorderLight
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight
import com.piku.client.ui.theme.PrivateAmberDark
import com.piku.client.ui.theme.PrivateAmberLight
import com.piku.client.ui.theme.ViewerBackgroundDark
import com.piku.client.ui.theme.themedSwitchColors
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** 分类卡上的"常用"快捷集（其余走"全部分类"） */
private val CURATED_CATEGORY_CDS = listOf(4, 6, 15, 9)

/** accent 按钮上的文字色：暗色主题 accent 是浅色，需深色文字 */
@Composable
internal fun onAccent(): Color = if (LocalDarkTheme.current) LoginBackgroundDark else Color.White

@Composable
fun PublishScreen(
    onBack: () -> Unit,
    onPublished: (Long) -> Unit,
    initialDraftId: Long? = null,
    /** >0 = 编辑该已发布作品（编辑模式：无草稿箱、无选图、无类型切换、提交走 Update 端点） */
    editWorkId: Long = -1L,
) {
    val viewModel: PublishViewModel = hiltViewModel()
    val draftBoxViewModel: DraftBoxViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()
    val boxDrafts by draftBoxViewModel.drafts.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val isEditMode = editWorkId > 0
    val snackbar = remember { SnackbarHostState() }
    val boxScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showCategorySheet by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showImageActions by remember { mutableIntStateOf(-1) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showNovelComposer by remember { mutableStateOf(false) }
    var showDraftBox by remember { mutableStateOf(false) }
    var pendingTag by remember { mutableStateOf("") }

    /** 把标签框里还没提交的内容落盘（标签本身不含空白、逗号、顿号）：失焦、回车、发布、退出前都会调用，避免静默丢失 */
    fun flushPendingTag() {
        val pending = pendingTag.replace(Regex("[\\s,，、]"), "").trim()
        if (pending.isNotEmpty()) viewModel.appendTag(pending)
        pendingTag = ""
        viewModel.onTagInputChange("")
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    LaunchedEffect(Unit) {
        viewModel.published.collect { workId -> onPublished(workId) }
    }
    // 进入会话：编辑已发布作品 > 编辑本地草稿 > 新创作；只执行一次
    LaunchedEffect(editWorkId, initialDraftId) {
        if (editWorkId > 0) viewModel.openEditSession(editWorkId)
        else viewModel.openSession(initialDraftId)
    }
    // 编辑页加载失败：提示已发 Snackbar，直接回列表页
    LaunchedEffect(state.editFailed) {
        if (state.editFailed) onBack()
    }
    // 每次打开草稿箱刷新独立列表
    LaunchedEffect(showDraftBox) {
        if (showDraftBox) draftBoxViewModel.refresh()
    }
    LaunchedEffect(state.noticeRes) {
        val res = state.noticeRes ?: return@LaunchedEffect
        viewModel.consumeNotice()
        snackbar.showSnackbar(context.getString(res))
    }

    // 拦截返回键：busy 时空处理（消费事件，不让 HomeScreen 的 onDismissRequest 关掉浮层）；
    // 非 busy 时有改动弹确认，无改动直接退
    BackHandler(enabled = true) {
        if (state.isBusy) return@BackHandler
        if (state.dirty && state.hasContent) {
            showExitDialog = true
        } else {
            flushPendingTag()
            viewModel.saveAndExit(onBack)
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
        Column(Modifier.fillMaxSize()) {
            PublishTopBar(
                title = if (isEditMode) stringResource(R.string.publish_edit_title)
                else stringResource(R.string.publish_title),
                onBack = {
                    if (state.isBusy || state.editLoading) return@PublishTopBar
                    if (state.dirty && state.hasContent) {
                        showExitDialog = true
                    } else {
                        flushPendingTag()
                        if (isEditMode) onBack() else viewModel.saveAndExit(onBack)
                    }
                },
                draftCount = boxDrafts.size,
                // 编辑模式没有草稿语义：草稿箱入口隐藏
                onOpenDrafts = if (isEditMode) null else {
                    {
                        flushPendingTag()
                        showDraftBox = true
                    }
                },
                canPublish = !state.isBusy && !state.editLoading,
                publishLabel = if (isEditMode) stringResource(R.string.publish_save)
                else stringResource(R.string.publish_publish),
                onPublish = {
                    flushPendingTag()
                    viewModel.publish()
                },
                dark = dark,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(top = 4.dp, bottom = 24.dp)
                    .imePadding(),
            ) {
                if (state.editLoading) {
                    // 编辑页加载骨架：与编辑模式版式同构，替代原先的单点 LoaderDots
                    PublishEditSkeleton(dark = dark)
                } else {
                    if (!isEditMode) {
                        KindSegmented(kind = state.kind, enabled = !state.isBusy, onSelect = viewModel::setKind)
                        Spacer(Modifier.height(14.dp))
                    }
                    if (state.kind == UploadKind.ILLUST) {
                        if (isEditMode) {
                            // 编辑模式第一版不改图片：服务端保持原有图片
                            EditImageReadonlyNote(dark = dark)
                        } else {
                            ImageSection(
                                images = state.images,
                                totalBytes = state.totalBytes,
                                onPick = {
                                    pickImages.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                onTileClick = { showImageActions = it },
                                onRemove = viewModel::removeImage,
                                dark = dark,
                            )
                        }
                    } else {
                        NovelSection(
                            title = state.title,
                            body = state.body,
                            vertical = state.novelDirection == 1,
                            onTitle = viewModel::setTitle,
                            onDirection = viewModel::setNovelDirection,
                            onOpenComposer = { showNovelComposer = true },
                            dark = dark,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    CategorySection(
                        categoryCd = state.categoryCd,
                        onSelect = viewModel::setCategory,
                        onMore = { showCategorySheet = true },
                        dark = dark,
                    )
                    Spacer(Modifier.height(12.dp))
                    TagsSection(
                        tags = state.tagsText,
                        pendingTag = pendingTag,
                        onPendingTagChange = { pendingTag = it },
                        myTags = viewModel.myTags.collectAsStateWithLifecycle().value,
                        tagSuggestions = viewModel.tagSuggestions.collectAsStateWithLifecycle().value,
                        onTagInputChange = viewModel::onTagInputChange,
                        onAppend = viewModel::appendTag,
                        onRemove = viewModel::removeTagWord,
                        onFlushPending = { flushPendingTag() },
                        dark = dark,
                    )
                    Spacer(Modifier.height(12.dp))
                    DescriptionSection(
                        text = state.description,
                        onChange = viewModel::setDescription,
                        dark = dark,
                    )
                    Spacer(Modifier.height(12.dp))
                    OptionsSummaryCard(
                        state = state,
                        onPublic = viewModel::setPublish,
                        onNsfw = viewModel::setNsfw,
                        onClickMore = { showOptionsSheet = true },
                        dark = dark,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )

        if (state.phase != PublishPhase.Idle) {
            PublishOverlay(
                phase = state.phase,
                onRetry = viewModel::retryPublish,
                onAbort = viewModel::abortPublish,
                dark = dark,
            )
        }

        if (showNovelComposer) {
            NovelComposer(
                body = state.body,
                onChange = viewModel::setBody,
                onClose = { showNovelComposer = false },
                dark = dark,
            )
        }
    }

    if (showDraftBox) {
        Dialog(
            onDismissRequest = { showDraftBox = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
        ) {
            DraftBoxScreen(
                sessionId = sessionId,
                onBack = { showDraftBox = false },
                onDraftSelected = { id ->
                    showDraftBox = false
                    // 独立会话切换：当前已自动保存，直接载入目标，不再弹窗问覆盖
                    viewModel.switchTo(id) { boxScope.launch { draftBoxViewModel.refresh() } }
                },
                onDraftDeleted = { id ->
                    // 先终止会话（取消防抖、清 session id），再删行+删目录，杜绝复活竞态
                    if (id == sessionId) viewModel.resetState()
                    draftBoxViewModel.delete(id)
                },
                onNewDraft = {
                    showDraftBox = false
                    viewModel.startNew { boxScope.launch { draftBoxViewModel.refresh() } }
                },
            )
        }
    }
    if (showCategorySheet) {
        CategorySheet(
            selected = state.categoryCd,
            onSelect = {
                viewModel.setCategory(it)
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false },
            dark = dark,
        )
    }
    if (showOptionsSheet) {
        PublishOptionsSheet(
            state = state,
            onPublic = viewModel::setPublish,
            onNsfw = viewModel::setNsfw,
            onVisibility = viewModel::setVisibility,
            onPasswordEnabled = viewModel::setPasswordEnabled,
            onPassword = viewModel::setPassword,
            onRecent = viewModel::setShowRecent,
            onFirstOnly = viewModel::setShowFirstOnly,
            onDismiss = { showOptionsSheet = false },
            dark = dark,
        )
    }
    if (showImageActions >= 0) {
        ImageActionsSheet(
            index = showImageActions,
            count = state.images.size,
            onMove = { delta ->
                viewModel.moveImage(showImageActions, delta)
                showImageActions = -1
            },
            onRemove = {
                viewModel.removeImage(showImageActions)
                showImageActions = -1
            },
            onDismiss = { showImageActions = -1 },
            dark = dark,
        )
    }
    if (showExitDialog) {
        if (isEditMode) {
            // 编辑模式：没有草稿语义，只有"放弃修改 / 继续编辑"
            EditExitDialog(
                onDiscard = {
                    showExitDialog = false
                    onBack()
                },
                onStay = { showExitDialog = false },
            )
        } else {
            ExitDraftDialog(
                onSave = {
                    showExitDialog = false
                    flushPendingTag()
                    viewModel.saveAndExit(onBack)
                },
                onDiscard = {
                    showExitDialog = false
                    viewModel.discardAndExit(onBack)
                },
                onStay = { showExitDialog = false },
            )
        }
    }
}

// ---------- 通用卡 / 字段 ----------

@Composable
private fun SectionCard(
    dark: Boolean,
    clickable: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = if (dark) GlassCardBgDark else GlassCardBgLight
    val border = if (dark) GlassCardBorderDark else GlassCardBorderLight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(24.dp))
            .then(if (clickable != null) Modifier.clickable(onClick = clickable) else Modifier)
            .padding(16.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionHeader(
    label: String,
    required: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (required) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = "*",
                color = PikuColors.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
private fun HeaderMeta(text: String) {
    Text(text = text, color = PikuColors.textFaint, fontSize = 11.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val accent = PikuColors.accent
    val fieldBg = PikuColors.textSecondary.copy(alpha = 0.08f)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        visualTransformation = visualTransformation,
        placeholder = placeholder?.let { {
            Text(it, fontSize = 13.sp, color = PikuColors.textFaint)
        } },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent.copy(alpha = 0.6f),
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = fieldBg,
            unfocusedContainerColor = fieldBg,
            cursorColor = accent,
            focusedLabelColor = accent,
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.sp,
            color = PikuColors.textPrimary,
        ),
        modifier = modifier,
    )
}

private fun Modifier.dashedBorder(color: Color, corner: Dp): Modifier =
    this.drawBehind {
        drawRoundRect(
            color = color,
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f), 0f),
            ),
            cornerRadius = CornerRadius(corner.toPx()),
        )
    }

// ---------- 顶栏 ----------

@Composable
private fun PublishTopBar(
    title: String,
    onBack: () -> Unit,
    draftCount: Int,
    /** null = 隐藏草稿箱入口（编辑模式） */
    onOpenDrafts: (() -> Unit)?,
    canPublish: Boolean,
    publishLabel: String,
    onPublish: () -> Unit,
    dark: Boolean,
) {
    val accent = PikuColors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与详情页 / 收藏页头部一致：半透明玻璃底 + 0.5dp 底分隔线
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
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PikuBackButton(
            onClick = onBack,
            dark = dark,
            contentDescription = stringResource(R.string.back),
        )
        Text(
            text = title,
            color = PikuColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp),
        )
        // 草稿箱（编辑模式不渲染）
        if (onOpenDrafts != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenDrafts)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.menu_draft_box),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
                if (draftCount > 0) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .widthIn(min = 16.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (draftCount > 99) "99+" else "$draftCount",
                            color = onAccent(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            // 9sp 小字自带 font padding 会视觉偏上：去掉内边距并收紧行高来居中
                            style = androidx.compose.ui.text.TextStyle(
                                lineHeight = 10.sp,
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false,
                                ),
                            ),
                        )
                    }
                }
            }
            }
        }
        Spacer(Modifier.width(8.dp))
        // 发布按钮（无背景）
        Text(
            text = publishLabel,
            color = if (canPublish) accent else accent.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(enabled = canPublish, onClick = onPublish)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

// ---------- 类型分段 ----------

@Composable
private fun KindSegmented(
    kind: UploadKind,
    enabled: Boolean,
    onSelect: (UploadKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(PikuColors.textSecondary.copy(alpha = 0.10f))
            .padding(4.dp),
    ) {
        KindSegmentItem(
            text = stringResource(R.string.publish_kind_image),
            selected = kind == UploadKind.ILLUST,
            enabled = enabled,
            onClick = { onSelect(UploadKind.ILLUST) },
            modifier = Modifier.weight(1f),
        )
        KindSegmentItem(
            text = stringResource(R.string.publish_kind_novel),
            selected = kind == UploadKind.NOVEL,
            enabled = enabled,
            onClick = { onSelect(UploadKind.NOVEL) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KindSegmentItem(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) PikuColors.accent else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) onAccent() else PikuColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ---------- 图片编辑器 ----------

@Composable
private fun ImageSection(
    images: List<String>,
    totalBytes: Long,
    onPick: () -> Unit,
    onTileClick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    dark: Boolean,
) {
    SectionCard(dark = dark) {
        SectionHeader(
            label = stringResource(R.string.publish_kind_image),
            trailing = {
                if (images.isNotEmpty()) {
                    val mb = totalBytes / 1024f / 1024f
                    HeaderMeta(
                        text = if (mb >= 1f) {
                            stringResource(
                                R.string.publish_images_count_size,
                                images.size,
                                String.format(Locale.getDefault(), "%.1f", mb),
                            )
                        } else {
                            stringResource(R.string.publish_images_count, images.size)
                        },
                    )
                }
            },
        )
        Spacer(Modifier.height(14.dp))
        if (images.isEmpty()) {
            EmptyImageArea(onPick = onPick)
        } else {
            (0..images.size).toList().chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { index ->
                        if (index < images.size) {
                            ImageTile(
                                path = images[index],
                                order = index + 1,
                                onClick = { onTileClick(index) },
                                onRemove = { onRemove(index) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            AddImageTile(onClick = onPick, modifier = Modifier.weight(1f))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EmptyImageArea(onPick: () -> Unit) {
    val accent = PikuColors.accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.05f))
            .dashedBorder(accent.copy(alpha = 0.45f), 18.dp)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.publish_add_images_hint),
                color = accent.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun AddImageTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = PikuColors.accent
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.05f))
            .dashedBorder(accent.copy(alpha = 0.45f), 16.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.publish_add_images),
                color = accent.copy(alpha = 0.85f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ImageTile(
    path: String,
    order: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = if (order == 1) {
                    stringResource(R.string.publish_image_cover_badge)
                } else {
                    "$order"
                },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // 触摸区放到 32dp（视觉仍 22dp）：原来 20dp 又小又贴边，删除很容易误触
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(1.dp)
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.publish_image_action_remove),
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

// ---------- 小说编辑器 ----------

@Composable
private fun NovelSection(
    title: String,
    body: String,
    vertical: Boolean,
    onTitle: (String) -> Unit,
    onDirection: (Boolean) -> Unit,
    onOpenComposer: () -> Unit,
    dark: Boolean,
) {
    SectionCard(dark = dark) {
        SectionHeader(
            label = stringResource(R.string.publish_kind_novel),
            trailing = { DirectionSegment(vertical = vertical, onDirection = onDirection) },
        )
        Spacer(Modifier.height(12.dp))
        PublishField(
            value = title,
            onChange = onTitle,
            placeholder = stringResource(R.string.publish_novel_title_hint),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        BodySummaryCard(body = body, onClick = onOpenComposer)
    }
}

@Composable
private fun DirectionSegment(vertical: Boolean, onDirection: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PikuColors.textSecondary.copy(alpha = 0.10f))
            .padding(2.dp),
    ) {
        DirectionSegmentItem(
            text = stringResource(R.string.publish_novel_horizontal),
            selected = !vertical,
            onClick = { onDirection(false) },
        )
        DirectionSegmentItem(
            text = stringResource(R.string.publish_novel_vertical),
            selected = vertical,
            onClick = { onDirection(true) },
        )
    }
}

@Composable
private fun DirectionSegmentItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = PikuColors.accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (selected) accent else PikuColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 正文摘要卡：点它进全屏编辑器 */
@Composable
private fun BodySummaryCard(body: String, onClick: () -> Unit) {
    val accent = PikuColors.accent
    val faint = PikuColors.textFaint
    val paragraphs = body.trim().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PikuColors.textSecondary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (body.isBlank()) {
            Text(
                text = stringResource(R.string.publish_novel_body_hint),
                color = faint,
                fontSize = 13.sp,
            )
        } else {
            Text(
                text = body,
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 23.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = faint.copy(alpha = 0.2f), thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.publish_novel_chars, body.length, paragraphs.size),
                color = faint,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.publish_novel_edit_full),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ---------- 全屏小说编辑器 ----------

/** 中文小说常用标点符号 */
private val NOVEL_PUNCTUATIONS = listOf(
    "。", "，", "！", "？", "、",
    "「", "」", "『", "』",
    "——", "……", "…",
    "（", "）", "【", "】",
    "：", "；",
)

// 阅读器配色（与 FullNovelViewer 共享）
private val NovelReaderBgLight = Color(0xFFF3EEDA)
private val NovelReaderTextLight = Color(0xFF2E2A23)
private val NovelReaderBgDark = ViewerBackgroundDark
private val NovelReaderTextDark = Color(0xFFD6D0C4)
private val NovelReaderControlBgLight = Color(0xFFFAF5EC)
private val NovelReaderControlBgDark = Color(0xCC141312)
private val NovelReaderProgressAccentLight = Color(0xFFB08A52)
private val NovelReaderProgressTrackLight = Color(0xFFE6DFD2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelComposer(
    body: String,
    onChange: (String) -> Unit,
    onClose: () -> Unit,
    dark: Boolean,
) {
    var preview by remember { mutableStateOf(false) }
    var editorDark by remember { mutableStateOf(dark) }
    val accent = PikuColors.accent

    // 使用 TextFieldValue 追踪光标位置
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = body,
                selection = TextRange(body.length),
            ),
        )
    }
    // 同步外部 body 变化
    LaunchedEffect(body) {
        if (textFieldValue.text != body) {
            textFieldValue = textFieldValue.copy(text = body)
        }
    }

    val currentText = textFieldValue.text
    val paragraphs = remember(currentText) { currentText.trim().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() } }
    val chars = currentText.length

    val previewScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 在光标位置插入文字
    fun insertAtCursor(text: String) {
        val pos = textFieldValue.selection.start
        val newText = currentText.substring(0, pos) + text + currentText.substring(pos)
        val newPos = pos + text.length
        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newPos),
        )
        onChange(newText)
    }

    // 阅读器配色
    val bg = if (editorDark) NovelReaderBgDark else NovelReaderBgLight
    val fg = if (editorDark) NovelReaderTextDark else NovelReaderTextLight
    val controlBg = if (editorDark) NovelReaderControlBgDark else NovelReaderControlBgLight
    val progressAccent = if (editorDark) accent else NovelReaderProgressAccentLight
    val progressTrack = if (editorDark) fg.copy(alpha = 0.25f) else NovelReaderProgressTrackLight

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(controlBg)
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PikuBackButton(
                    onClick = onClose,
                    dark = editorDark,
                    contentDescription = stringResource(R.string.back),
                    tint = fg,
                )
                Text(
                    text = stringResource(R.string.publish_novel_edit_full),
                    color = fg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp),
                )
                Text(
                    text = stringResource(R.string.publish_novel_chars, chars, paragraphs.size),
                    color = fg.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                IconButton(onClick = { editorDark = !editorDark }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (editorDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 预览模式进度条
            if (preview) {
                val max = previewScrollState.maxValue
                if (max > 0) {
                    val progress = (previewScrollState.value.toFloat() / max).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .pointerInput(max) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    scope.launch { previewScrollState.scrollTo((down.position.x / size.width * max).toInt().coerceIn(0, max)) }
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull()
                                        if (change == null || !change.pressed) break
                                        if (change.position != change.previousPosition) {
                                            scope.launch { previewScrollState.scrollTo((change.position.x / size.width * max).toInt().coerceIn(0, max)) }
                                            change.consume()
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(progressTrack))
                        Box(Modifier.fillMaxWidth(progress).height(2.dp).clip(RoundedCornerShape(1.dp)).background(progressAccent))
                    }
                }
            }

            // 编辑/预览区域
            if (!preview) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onChange(newValue.text)
                    },
                    placeholder = {
                        Text(stringResource(R.string.publish_novel_body_hint), fontSize = 14.sp, color = fg.copy(alpha = 0.4f))
                    },
                    shape = RoundedCornerShape(0.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = accent,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 27.sp,
                        color = fg,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(previewScrollState)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    if (currentText.isBlank()) {
                        Text(
                            text = stringResource(R.string.publish_novel_body_hint),
                            color = fg.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                        )
                    } else {
                        paragraphs.forEachIndexed { i, para ->
                            Text(
                                text = para,
                                color = fg,
                                fontSize = 16.sp,
                                lineHeight = 27.sp,
                            )
                            if (i != paragraphs.lastIndex) Spacer(Modifier.height(14.dp))
                        }
                    }
                }
            }

            // 底部工具栏（标点 + 预览）
            if (!preview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(controlBg)
                        .navigationBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 标点符号（横向滚动）
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NOVEL_PUNCTUATIONS.forEach { punct ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { insertAtCursor(punct) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = punct, color = fg, fontSize = 15.sp)
                            }
                        }
                    }
                    // 预览按钮
                    Text(
                        text = stringResource(R.string.publish_novel_preview),
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { preview = true }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            } else {
                // 预览模式底部
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(controlBg)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.publish_novel_edit_exit),
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { preview = false }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------- 分类（常用 + 更多）----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(
    categoryCd: Int?,
    onSelect: (Int) -> Unit,
    onMore: () -> Unit,
    dark: Boolean,
) {
    val accent = PikuColors.accent
    val selectedName = categoryCd
        ?.let { PoipikuCategory.fromCd(it)?.let { c -> stringResource(c.nameRes) } }
    SectionCard(dark = dark) {
        SectionHeader(
            label = stringResource(R.string.publish_category),
            required = true,
            trailing = {
                if (selectedName != null) {
                    Text(text = selectedName, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.publish_category_quick),
            color = PikuColors.textFaint,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val chips = buildSet {
                addAll(CURATED_CATEGORY_CDS)
                categoryCd?.let { if (it !in CURATED_CATEGORY_CDS) add(it) }
            }
            chips.forEach { cd ->
                val name = PoipikuCategory.fromCd(cd)?.let { stringResource(it.nameRes) }
                    ?: return@forEach
                SelectableChip(
                    text = name,
                    selected = categoryCd == cd,
                    onClick = { onSelect(cd) },
                )
            }
            MoreChip(
                text = stringResource(R.string.publish_category_more),
                onClick = onMore,
            )
        }
    }
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) PikuColors.accent else PikuColors.textSecondary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (selected) onAccent() else PikuColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MoreChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(BorderStroke(1.dp, PikuColors.border), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(text = text, color = PikuColors.textSecondary, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    selected: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark, scrollable = true) {
        PikuSheetHandle()
        PikuSheetTitle(text = stringResource(R.string.publish_category_full_title))
        Spacer(Modifier.height(12.dp))
        CATEGORY_GROUPS.forEach { group ->
            Text(
                text = stringResource(group.titleRes),
                color = PikuColors.textFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.categories.forEach { category ->
                    val accent = PikuColors.accent
                    val chosen = selected == category.cd
                    val shape = RoundedCornerShape(20.dp)
                    Row(
                        modifier = Modifier
                            .clip(shape)
                            .background(
                                if (chosen) PikuColors.textPrimary
                                else if (dark) GlassCardBgDark else Color(0xFF000000).copy(alpha = 0.04f),
                            )
                            .border(
                                BorderStroke(0.5.dp, PikuColors.border),
                                shape,
                            )
                            .clickable { onSelect(category.cd) }
                            .padding(
                                start = 14.dp,
                                end = if (chosen) 9.dp else 14.dp,
                                top = 10.dp,
                                bottom = 10.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(category.nameRes),
                            color = if (chosen) {
                                if (dark) LoginBackgroundDark else Color.White
                            } else {
                                if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
                            },
                            fontSize = 13.sp,
                        )
                        if (chosen) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (dark) LoginBackgroundDark else Color.White,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------- 标签 ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: String,
    pendingTag: String,
    onPendingTagChange: (String) -> Unit,
    myTags: List<String>,
    tagSuggestions: List<String>,
    onTagInputChange: (String) -> Unit,
    onAppend: (String) -> Unit,
    onRemove: (String) -> Unit,
    onFlushPending: () -> Unit,
    dark: Boolean,
) {
    val list = remember(tags) { tags.split(Regex("\\s+")).filter { it.isNotBlank() } }
    val accent = PikuColors.accent
    val faint = PikuColors.textFaint

    SectionCard(dark = dark) {
        SectionHeader(
            label = stringResource(R.string.publish_tags),
            trailing = {
                HeaderMeta(
                    text = stringResource(R.string.publish_tags_count, list.size, tags.length),
                )
            },
        )
        Spacer(Modifier.height(12.dp))

        // 已选标签 chips
        if (list.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 输入中的文本规范化后与某个已选标签相同时，高亮该 chip 提示「已经加过了」
                val pendingNormalized = pendingTag.replace(Regex("[\\s,，、]"), "")
                list.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .then(
                                if (tag == pendingNormalized) {
                                    Modifier.border(1.dp, accent, RoundedCornerShape(999.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .padding(start = 11.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tag, color = accent, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { onRemove(tag) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.publish_tag_remove),
                                tint = accent.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 标签输入框（Tag Chips 模式）：不自动分隔，由用户回车或失焦时整条提交
        OutlinedTextField(
            value = pendingTag,
            onValueChange = { newValue ->
                onPendingTagChange(newValue)
                onTagInputChange(newValue)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onFlushPending() }),
            placeholder = {
                Text(
                    text = if (list.isEmpty()) {
                        stringResource(R.string.publish_tags_input)
                    } else {
                        stringResource(R.string.publish_tags_add_more)
                    },
                    fontSize = 13.sp,
                    color = faint,
                )
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = PikuColors.textPrimary,
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = PikuColors.textSecondary.copy(alpha = 0.08f),
                unfocusedContainerColor = PikuColors.textSecondary.copy(alpha = 0.08f),
                cursorColor = accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) onFlushPending()
                },
        )

        // 标签自动补全建议
        if (tagSuggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tagSuggestions.take(6).forEach { suggestion ->
                    val isAlreadyAdded = suggestion in list
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isAlreadyAdded) accent.copy(alpha = 0.08f)
                                else PikuColors.textSecondary.copy(alpha = 0.06f),
                            )
                            .border(
                                BorderStroke(
                                    0.5.dp,
                                    if (isAlreadyAdded) accent.copy(alpha = 0.3f)
                                    else PikuColors.border,
                                ),
                                RoundedCornerShape(999.dp),
                            )
                            .clickable {
                                if (!isAlreadyAdded) {
                                    onAppend(suggestion)
                                    onPendingTagChange("")
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = suggestion,
                            color = if (isAlreadyAdded) accent else PikuColors.textSecondary,
                            fontSize = 11.sp,
                        )
                        if (isAlreadyAdded) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
            }
        }

        // 我的标签
        if (myTags.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.publish_tags_mine),
                    color = PikuColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.publish_tags_tap_hint),
                    color = faint,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                myTags.forEach { tag ->
                    val isSelected = tag in list
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSelected) accent.copy(alpha = 0.08f)
                                else PikuColors.textSecondary.copy(alpha = 0.08f),
                            )
                            .border(
                                BorderStroke(
                                    0.5.dp,
                                    if (isSelected) accent.copy(alpha = 0.3f)
                                    else PikuColors.border,
                                ),
                                RoundedCornerShape(999.dp),
                            )
                            .clickable {
                                if (isSelected) onRemove(tag) else {
                                    onAppend(tag)
                                    onPendingTagChange("")
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tag,
                            color = if (isSelected) accent else PikuColors.textSecondary,
                            fontSize = 12.sp,
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- 说明 ----------

@Composable
private fun DescriptionSection(text: String, onChange: (String) -> Unit, dark: Boolean) {
    SectionCard(dark = dark) {
        SectionHeader(
            label = stringResource(R.string.publish_description),
            trailing = { HeaderMeta(text = "${text.length}/200") },
        )
        Spacer(Modifier.height(12.dp))
        PublishField(
            value = text,
            onChange = onChange,
            placeholder = stringResource(R.string.publish_description_hint),
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp),
        )
    }
}

// ---------- 发布选项摘要 ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsSummaryCard(
    state: PublishUiState,
    onPublic: (Boolean) -> Unit,
    onNsfw: (NsfwLevel) -> Unit,
    onClickMore: () -> Unit,
    dark: Boolean,
) {
    val faint = PikuColors.textFaint

    SectionCard(dark = dark) {
        SectionHeader(
            label = stringResource(R.string.publish_options),
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = faint,
                    modifier = Modifier.size(18.dp),
                )
            },
            modifier = Modifier.clickable(onClick = onClickMore),
        )
        Spacer(Modifier.height(12.dp))

        // 公开/私密开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.publish_options_public_row),
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.publish,
                onCheckedChange = onPublic,
                colors = themedSwitchColors(dark),
            )
        }
        // 开关状态实时说明：公开时提示可用的细化选项，私密时说明后果
        Text(
            text = stringResource(
                if (state.publish) R.string.publish_options_public_on_hint
                else R.string.publish_options_public_off_hint,
            ),
            color = PikuColors.textFaint,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(8.dp))

        // 年龄分级
        Text(
            text = stringResource(R.string.publish_options_rating),
            color = PikuColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        RatingSegmented(
            nsfw = state.nsfw,
            red = ErrorRedDark,
            onPick = onNsfw,
        )
    }
}

// ---------- 图片操作弹层 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageActionsSheet(
    index: Int,
    count: Int,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    val accent = PikuColors.accent
    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark) {
        PikuSheetHandle()
        PikuSheetTitle(text = stringResource(R.string.publish_image_actions))
        Spacer(Modifier.height(6.dp))
        if (index > 0) {
            SheetTextAction(text = stringResource(R.string.publish_image_action_set_cover), color = accent) {
                onMove(-index)
            }
        }
        if (index > 0) {
            SheetTextAction(text = stringResource(R.string.publish_image_action_prev), color = accent) {
                onMove(-1)
            }
        }
        if (index < count - 1) {
            SheetTextAction(text = stringResource(R.string.publish_image_action_next), color = accent) {
                onMove(1)
            }
        }
        HorizontalDivider(color = PikuColors.border, modifier = Modifier.padding(vertical = 6.dp))
        SheetTextAction(text = stringResource(R.string.publish_image_action_remove), color = ErrorRedDark) {
            onRemove()
        }
    }
}

@Composable
private fun SheetTextAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 8.dp),
    )
}

// ---------- 发布选项弹层 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishOptionsSheet(
    state: PublishUiState,
    onPublic: (Boolean) -> Unit,
    onNsfw: (NsfwLevel) -> Unit,
    onVisibility: (ShowVisibility) -> Unit,
    onPasswordEnabled: (Boolean) -> Unit,
    onPassword: (String) -> Unit,
    onRecent: (Boolean) -> Unit,
    onFirstOnly: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    val faint = PikuColors.textFaint
    val accent = PikuColors.accent

    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark, scrollable = true) {
        PikuSheetHandle()
        PikuSheetTitle(text = stringResource(R.string.publish_options))
        Spacer(Modifier.height(12.dp))

        // 可见范围
        Text(
            text = stringResource(R.string.publish_options_visibility_title),
            color = PikuColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        VisibilityOption(
            label = stringResource(R.string.publish_options_visibility_anyone),
            selected = state.visibility == ShowVisibility.ANYONE,
            accent = accent,
            enabled = state.publish,
            onClick = { onVisibility(ShowVisibility.ANYONE) },
        )
        VisibilityOption(
            label = stringResource(R.string.publish_options_visibility_login),
            selected = state.visibility == ShowVisibility.POIPIKU_LOGIN,
            accent = accent,
            enabled = state.publish,
            onClick = { onVisibility(ShowVisibility.POIPIKU_LOGIN) },
        )
        VisibilityOption(
            label = stringResource(R.string.publish_options_visibility_follower),
            selected = state.visibility == ShowVisibility.FOLLOWER,
            accent = accent,
            enabled = state.publish,
            onClick = { onVisibility(ShowVisibility.FOLLOWER) },
        )
        if (!state.publish) {
            // 私密时可见范围不适用：置灰保留原选择，重新公开后恢复
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.publish_options_private_scope_hint),
                color = if (dark) PrivateAmberDark else PrivateAmberLight,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 密码（公开时显示）
        if (state.publish) {
            Text(
                text = stringResource(R.string.publish_options_password),
                color = PikuColors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                label = if (state.passwordEnabled) {
                    stringResource(R.string.publish_options_password_enabled_hint)
                } else {
                    stringResource(R.string.publish_options_password_off_hint)
                },
                checked = state.passwordEnabled,
                onChange = onPasswordEnabled,
                dark = dark,
            )
            if (state.passwordEnabled) {
                Spacer(Modifier.height(8.dp))
                PublishField(
                    value = state.password,
                    onChange = onPassword,
                    placeholder = stringResource(R.string.publish_options_password_hint),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // 其他选项
        SwitchRow(
            label = stringResource(R.string.publish_options_recent),
            checked = state.showRecent,
            onChange = onRecent,
            // 私密作品不会出现在任何动向，禁用但保留状态
            enabled = state.publish,
            dark = dark,
        )
        if (state.publish && state.images.size >= 2 && state.visibility != ShowVisibility.ANYONE) {
            SwitchRow(
                label = stringResource(R.string.publish_options_first_only),
                checked = state.showFirstOnly,
                onChange = onFirstOnly,
                dark = dark,
            )
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(accent)
                .clickable(onClick = onDismiss)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.publish_ok),
                color = onAccent(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    hint: String = "",
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (enabled) PikuColors.textPrimary else PikuColors.textFaint,
                fontSize = 14.sp,
            )
            if (hint.isNotBlank()) {
                Text(hint, color = PikuColors.textFaint, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            colors = themedSwitchColors(dark),
        )
    }
}

@Composable
private fun RatingSegmented(nsfw: NsfwLevel, red: Color, onPick: (NsfwLevel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RatingChip(
            text = stringResource(R.string.publish_options_rating_all),
            selected = nsfw == NsfwLevel.ALL,
            tint = PikuColors.accent,
            onClick = { onPick(NsfwLevel.ALL) },
            modifier = Modifier.weight(1f),
        )
        RatingChip(
            text = stringResource(R.string.publish_options_rating_cushion),
            selected = nsfw == NsfwLevel.CUSHION,
            tint = PikuColors.accent,
            onClick = { onPick(NsfwLevel.CUSHION) },
            modifier = Modifier.weight(1f),
        )
        RatingChip(
            text = "R18",
            selected = nsfw == NsfwLevel.R18,
            tint = red,
            onClick = { onPick(NsfwLevel.R18) },
            modifier = Modifier.weight(1f),
        )
        RatingChip(
            text = "R18+",
            selected = nsfw == NsfwLevel.R18PLUS,
            tint = red,
            onClick = { onPick(NsfwLevel.R18PLUS) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RatingChip(
    text: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) tint.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (selected) tint.copy(alpha = 0.7f) else PikuColors.border),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) tint else PikuColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun VisibilityOption(
    label: String,
    selected: Boolean,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // 禁用（私密）时整体置灰，选中态仅保留淡色标记供"重新公开后恢复"参考
    val ringColor = when {
        !enabled -> PikuColors.border
        selected -> accent
        else -> PikuColors.textFaint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(1.5.dp, ringColor),
                    CircleShape,
                )
                .padding(3.dp),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (enabled) accent else PikuColors.border),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = when {
                !enabled -> PikuColors.textFaint
                selected -> accent
                else -> PikuColors.textPrimary
            },
            fontSize = 14.sp,
            fontWeight = if (selected && enabled) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

// ---------- 离开确认 ----------

@Composable
private fun ExitDraftDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onStay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.publish_exit_title)) },
        text = { Text(stringResource(R.string.publish_exit_body)) },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.publish_exit_save), color = PikuColors.accent)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onStay) {
                    Text(stringResource(R.string.publish_exit_stay), color = PikuColors.textSecondary)
                }
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.publish_exit_discard), color = PikuColors.error)
                }
            }
        },
    )
}

/** 编辑模式的退出确认：没有草稿语义，只有"放弃修改 / 继续编辑" */
@Composable
private fun EditExitDialog(
    onDiscard: () -> Unit,
    onStay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.publish_edit_exit_title)) },
        confirmButton = {
            TextButton(onClick = onStay) {
                Text(stringResource(R.string.publish_edit_exit_stay), color = PikuColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.publish_edit_exit_discard), color = PikuColors.error)
            }
        },
    )
}

/** 编辑模式图片区占位 */
@Composable
private fun EditImageReadonlyNote(dark: Boolean) {
    SectionCard(dark = dark) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.publish_kind_image),
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.publish_edit_image_note),
            color = PikuColors.textFaint,
            fontSize = 12.sp,
        )
    }
}

/** 编辑页加载骨架：与编辑模式版式同构（首卡 + 分类 + 标签 + 说明 + 选项摘要），呼吸脉冲 */
@Composable
private fun PublishEditSkeleton(dark: Boolean) {
    val pulse = rememberSkeletonPulse()
    Column {
        // 首卡：插画=图片只读说明，小说=标题/正文摘要；类型解析前两者版式相近，共用一个壳
        SectionCard(dark = dark) {
            SkeletonBlock(pulse.value, Modifier.fillMaxWidth().height(13.dp), shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.height(10.dp))
            SkeletonBlock(pulse.value, Modifier.fillMaxWidth(0.72f).height(10.dp), shape = RoundedCornerShape(5.dp))
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(pulse.value, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        // 分类
        SectionCard(dark = dark) {
            SkeletonBlock(pulse.value, Modifier.size(64.dp, 12.dp), shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(pulse.value, Modifier.size(72.dp, 30.dp), shape = RoundedCornerShape(15.dp))
                SkeletonBlock(pulse.value, Modifier.size(88.dp, 30.dp), shape = RoundedCornerShape(15.dp))
                SkeletonBlock(pulse.value, Modifier.size(64.dp, 30.dp), shape = RoundedCornerShape(15.dp))
                SkeletonBlock(pulse.value, Modifier.size(80.dp, 30.dp), shape = RoundedCornerShape(15.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        // 标签
        SectionCard(dark = dark) {
            SkeletonBlock(pulse.value, Modifier.size(48.dp, 12.dp), shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(pulse.value, Modifier.fillMaxWidth().height(38.dp), shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(pulse.value, Modifier.size(68.dp, 28.dp), shape = RoundedCornerShape(14.dp))
                SkeletonBlock(pulse.value, Modifier.size(96.dp, 28.dp), shape = RoundedCornerShape(14.dp))
                SkeletonBlock(pulse.value, Modifier.size(56.dp, 28.dp), shape = RoundedCornerShape(14.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        // 说明
        SectionCard(dark = dark) {
            SkeletonBlock(pulse.value, Modifier.size(48.dp, 12.dp), shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(pulse.value, Modifier.fillMaxWidth().height(88.dp), shape = RoundedCornerShape(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        // 发布选项摘要：开关行 + 年龄分级
        SectionCard(dark = dark) {
            SkeletonBlock(pulse.value, Modifier.size(72.dp, 12.dp), shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(pulse.value, Modifier.weight(1f).height(12.dp), shape = RoundedCornerShape(6.dp))
                Spacer(Modifier.width(12.dp))
                SkeletonBlock(pulse.value, Modifier.size(40.dp, 22.dp), shape = RoundedCornerShape(11.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) {
                    SkeletonBlock(pulse.value, Modifier.weight(1f).height(30.dp), shape = RoundedCornerShape(15.dp))
                }
            }
        }
    }
}

// ---------- 发布中 / 失败 / 成功覆盖层 ----------

/** 发布全程共用一个弹窗：加载 → 成功/失败在同一张卡内 Crossfade 切换，视觉连续 */
@Composable
private fun PublishOverlay(
    phase: PublishPhase,
    onRetry: () -> Unit,
    onAbort: () -> Unit,
    dark: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { } }, // 吃掉点击，遮罩期间不与表单交互
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 56.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (dark) GlassCardBgDark else GlassCardBgLight)
                .padding(horizontal = 20.dp, vertical = 26.dp),
        ) {
            Crossfade(targetState = phase, label = "publishPhase") { p ->
                when (p) {
                    PublishPhase.Idle -> Unit
                    PublishPhase.Creating, is PublishPhase.Uploading -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LoaderDots(dark = dark)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = when (p) {
                                is PublishPhase.Uploading ->
                                    stringResource(R.string.publish_progress_page, p.index + 1, p.total)
                                else -> stringResource(R.string.publish_progress_creating)
                            },
                            color = PikuColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PublishPhase.Success -> SuccessContent()
                    is PublishPhase.PageFailed -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.publish_failed_page, p.index + 1),
                            color = PikuColors.error,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = p.fileName,
                            color = PikuColors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = onAbort) {
                                Text(stringResource(R.string.publish_failed_abort), color = PikuColors.textSecondary)
                            }
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.publish_failed_retry), color = PikuColors.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** ✓ 已发布：accent 圆标弹入 + 淡入 */
@Composable
private fun SuccessContent() {
    val scale = remember { Animatable(0.6f) }
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
        alphaAnim.animateTo(1f, tween(180))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = alphaAnim.value
                }
                .size(44.dp)
                .clip(CircleShape)
                .background(PikuColors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.publish_success),
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

