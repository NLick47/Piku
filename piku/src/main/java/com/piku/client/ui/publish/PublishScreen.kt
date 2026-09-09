package com.piku.client.ui.publish

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.NsfwLevel
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.PublishDraft
import com.piku.client.domain.model.ShowVisibility
import com.piku.client.domain.model.UploadKind
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.PikuBottomSheet
import com.piku.client.ui.common.PikuSheetHandle
import com.piku.client.ui.common.PikuSheetTitle
import com.piku.client.ui.theme.ErrorRedDark
import com.piku.client.ui.theme.ErrorRedLight
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.GlassCardBgLight
import com.piku.client.ui.theme.GlassCardBorderDark
import com.piku.client.ui.theme.GlassCardBorderLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.themedSwitchColors
import java.io.File

/** 分类卡上的"常用"快捷集（其余走"全部分类"） */
private val CURATED_CATEGORY_CDS = listOf(4, 15, 6, 9, 10, 14)

/** accent 按钮上的文字色：暗色主题 accent 是浅色，需深色文字 */
@Composable
private fun onAccent(): Color = if (LocalDarkTheme.current) LoginBackgroundDark else Color.White

@Composable
fun PublishScreen(
    onBack: () -> Unit,
    onPublished: (Long) -> Unit,
) {
    val viewModel: PublishViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showCategorySheet by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showImageActions by remember { mutableIntStateOf(-1) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showNovelComposer by remember { mutableStateOf(false) }
    var showDraftSheet by remember { mutableStateOf(false) }
    var deleteDraftTarget by remember { mutableStateOf<PublishDraft?>(null) }
    var overwriteDraftTarget by remember { mutableStateOf<PublishDraft?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    LaunchedEffect(Unit) {
        viewModel.published.collect { workId -> onPublished(workId) }
    }
    LaunchedEffect(state.noticeRes) {
        val res = state.noticeRes ?: return@LaunchedEffect
        viewModel.consumeNotice()
        snackbar.showSnackbar(context.getString(res))
    }

    // 发布中不可退出；有改动先问是否存草稿
    BackHandler(enabled = !state.isBusy) {
        if (state.dirty) showExitDialog = true else onBack()
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
                onBack = {
                    if (state.isBusy) return@PublishTopBar
                    if (state.dirty) showExitDialog = true else onBack()
                },
                draftCount = drafts.size,
                onOpenDrafts = { showDraftSheet = true },
                canPublish = !state.isBusy,
                onPublish = viewModel::publish,
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
                KindSegmented(kind = state.kind, enabled = !state.isBusy, onSelect = viewModel::setKind)
                Spacer(Modifier.height(14.dp))
                if (state.kind == UploadKind.ILLUST) {
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
                    myTags = viewModel.myTags.collectAsStateWithLifecycle().value,
                    onTagText = viewModel::setTags,
                    onAppend = viewModel::appendTag,
                    onRemove = viewModel::removeTagWord,
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
                    onClick = { showOptionsSheet = true },
                    dark = dark,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )

        when (val phase = state.phase) {
            PublishPhase.Idle -> Unit
            PublishPhase.Creating -> BusyOverlay(
                progressText = stringResource(R.string.publish_progress_creating),
                dark = dark,
            )
            is PublishPhase.Uploading -> BusyOverlay(
                progressText = stringResource(R.string.publish_progress_page, phase.index + 1, phase.total),
                fraction = if (phase.fileBytes > 0) {
                    phase.sentBytes.toFloat() / phase.fileBytes.toFloat()
                } else {
                    0f
                },
                detail = phase.fileName,
                dark = dark,
            )
            is PublishPhase.PageFailed -> PageFailedOverlay(
                title = stringResource(R.string.publish_failed_page, phase.index + 1),
                detail = phase.fileName,
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

    if (showDraftSheet) {
        DraftSheet(
            drafts = drafts,
            onResume = { draft ->
                showDraftSheet = false
                if (state.dirty || state.hasContent) {
                    overwriteDraftTarget = draft
                } else {
                    draft.draftId?.let(viewModel::loadDraft)
                }
            },
            onDelete = { deleteDraftTarget = it },
            onDismiss = { showDraftSheet = false },
            dark = dark,
        )
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
        ExitDraftDialog(
            onSave = {
                showExitDialog = false
                viewModel.saveDraftToBoxAndExit(onBack)
            },
            onDiscard = {
                showExitDialog = false
                viewModel.discardDraftAndExit(onBack)
            },
            onStay = { showExitDialog = false },
        )
    }
    overwriteDraftTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { overwriteDraftTarget = null },
            title = { Text(stringResource(R.string.publish_draft_overwrite_title)) },
            text = { Text(stringResource(R.string.publish_draft_overwrite_body)) },
            confirmButton = {
                TextButton(onClick = {
                    target.draftId?.let(viewModel::loadDraft)
                    overwriteDraftTarget = null
                }) {
                    Text(stringResource(R.string.publish_draft_restore), color = PikuColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { overwriteDraftTarget = null }) {
                    Text(stringResource(R.string.publish_cancel), color = PikuColors.textSecondary)
                }
            },
        )
    }
    deleteDraftTarget?.let { target ->
        val id = target.draftId ?: return@let
        AlertDialog(
            onDismissRequest = { deleteDraftTarget = null },
            title = { Text(stringResource(R.string.draft_box_delete_confirm_title)) },
            text = { Text(stringResource(R.string.draft_box_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDraft(id)
                    deleteDraftTarget = null
                }) {
                    Text(stringResource(R.string.draft_box_delete), color = PikuColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDraftTarget = null }) {
                    Text(stringResource(R.string.publish_cancel), color = PikuColors.textSecondary)
                }
            },
        )
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
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
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
) {
    val accent = PikuColors.accent
    val fieldBg = PikuColors.textSecondary.copy(alpha = 0.08f)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = minLines,
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
    onBack: () -> Unit,
    draftCount: Int,
    onOpenDrafts: () -> Unit,
    canPublish: Boolean,
    onPublish: () -> Unit,
    dark: Boolean,
) {
    val accent = PikuColors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            text = stringResource(R.string.publish_title),
            color = PikuColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp),
        )
        DraftEntryButton(count = draftCount, onClick = onOpenDrafts)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = if (canPublish) 0.16f else 0.08f))
                .clickable(enabled = canPublish, onClick = onPublish)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.publish_publish),
                color = accent.copy(alpha = if (canPublish) 1f else 0.4f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
private fun DraftEntryButton(count: Int, onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PikuColors.textSecondary.copy(alpha = 0.10f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = stringResource(R.string.menu_draft_box),
                tint = PikuColors.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(PikuColors.accent)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 99) "99+" else "$count",
                    color = onAccent(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
            icon = Icons.Outlined.Image,
            text = stringResource(R.string.publish_kind_image),
            selected = kind == UploadKind.ILLUST,
            enabled = enabled,
            onClick = { onSelect(UploadKind.ILLUST) },
            modifier = Modifier.weight(1f),
        )
        KindSegmentItem(
            icon = Icons.AutoMirrored.Outlined.Article,
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
    icon: ImageVector,
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) PikuColors.accent else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) onAccent() else PikuColors.textSecondary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
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
                        text = if (mb >= 1f) "${images.size} 张 · ${"%.1f".format(mb)}MB"
                        else "${images.size} 张",
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
            Text(text = "$order", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "×", color = Color.White, fontSize = 12.sp, lineHeight = 12.sp)
        }
    }
}

// ---------- 草稿箱弹层 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftSheet(
    drafts: List<PublishDraft>,
    onResume: (PublishDraft) -> Unit,
    onDelete: (PublishDraft) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark, scrollable = true) {
        PikuSheetHandle()
        PikuSheetTitle(text = stringResource(R.string.menu_draft_box))
        Spacer(Modifier.height(10.dp))
        if (drafts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = PikuColors.textFaint,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.draft_box_empty),
                    color = PikuColors.textFaint,
                    fontSize = 12.sp,
                )
            }
        } else {
            drafts.forEach { draft ->
                DraftRow(
                    draft = draft,
                    onResume = { onResume(draft) },
                    onDelete = { onDelete(draft) },
                )
            }
        }
    }
}

@Composable
private fun DraftRow(
    draft: PublishDraft,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = PikuColors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onResume)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            val cover = draft.imageFiles.firstOrNull()
            if (cover != null) {
                AsyncImage(
                    model = File(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = if (draft.kind == UploadKind.NOVEL) "文" else "图",
                    color = accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = draft.kindPreview(),
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            val timeTs = draft.savedAt ?: draft.draftId
            Text(
                text = (timeTs?.let { draftTime(it) } ?: "") + " · " +
                    if (draft.kind == UploadKind.NOVEL) "小说" else "${draft.imageFiles.size} 张",
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PikuColors.error.copy(alpha = 0.10f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.draft_box_delete),
                tint = PikuColors.error,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

private fun PublishDraft.kindPreview(): String = when {
    kind == UploadKind.NOVEL && title.isNotBlank() -> title
    tags.isNotBlank() -> tags
    else -> if (kind == UploadKind.NOVEL) "未命名小说" else "未命名图集"
}

private fun draftTime(ts: Long): String =
    DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelComposer(
    body: String,
    onChange: (String) -> Unit,
    onClose: () -> Unit,
    dark: Boolean,
) {
    var preview by remember { mutableStateOf(false) }
    val accent = PikuColors.accent
    val faint = PikuColors.textFaint
    val paragraphs = body.trim().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    val chars = body.length

    BackHandler(onBack = onClose)

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PikuBackButton(
                    onClick = onClose,
                    dark = dark,
                    contentDescription = stringResource(R.string.back),
                )
                Text(
                    text = stringResource(R.string.publish_novel_edit_full),
                    color = PikuColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp),
                )
                Text(
                    text = stringResource(R.string.publish_novel_chars, chars, paragraphs.size),
                    color = faint,
                    fontSize = 12.sp,
                )
            }

            if (!preview) {
                OutlinedTextField(
                    value = body,
                    onValueChange = onChange,
                    placeholder = {
                        Text(stringResource(R.string.publish_novel_body_hint), fontSize = 14.sp, color = faint)
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
                        color = PikuColors.textPrimary,
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
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    paragraphs.forEachIndexed { i, para ->
                        Text(
                            text = para,
                            color = PikuColors.textPrimary,
                            fontSize = 16.sp,
                            lineHeight = 27.sp,
                        )
                        if (i != paragraphs.lastIndex) Spacer(Modifier.height(14.dp))
                    }
                    if (paragraphs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.publish_novel_body_hint),
                            color = faint,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniChip(
                    text = stringResource(R.string.publish_novel_insert_break),
                    selected = false,
                    onClick = { onChange(appendParagraphBreak(body)) },
                )
                Spacer(Modifier.width(8.dp))
                MiniChip(
                    text = stringResource(
                        if (preview) R.string.publish_novel_edit_exit else R.string.publish_novel_preview,
                    ),
                    selected = preview,
                    onClick = { preview = !preview },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${body.count { it == '\n' } + 1} 行",
                    color = faint,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun appendParagraphBreak(body: String): String {
    if (body.isBlank()) return body
    val trimmed = body.trimEnd()
    return if (trimmed.endsWith("\n\n")) body else "$trimmed\n\n"
}

@Composable
private fun MiniChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = PikuColors.accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.7f) else PikuColors.border),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = if (selected) accent else PikuColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
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

@OptIn(ExperimentalMaterial3Api::class)
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
        Spacer(Modifier.height(8.dp))
        PoipikuCategory.entries.filter { it != PoipikuCategory.ALL }.forEach { category ->
            val accent = PikuColors.accent
            val chosen = selected == category.cd
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(category.cd) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(category.nameRes),
                    color = if (chosen) accent else PikuColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (chosen) Text("✓", color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- 标签 ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: String,
    myTags: List<String>,
    onTagText: (String) -> Unit,
    onAppend: (String) -> Unit,
    onRemove: (String) -> Unit,
    dark: Boolean,
) {
    val list = tags.split(Regex("\\s+")).filter { it.isNotBlank() }
    val accent = PikuColors.accent
    SectionCard(dark = dark) {
        SectionHeader(
            label = stringResource(R.string.publish_tags),
            trailing = { HeaderMeta(text = "${tags.length}/100") },
        )
        Spacer(Modifier.height(12.dp))
        if (list.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                list.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .clickable { onRemove(tag) }
                            .padding(start = 11.dp, end = 9.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tag, color = accent, fontSize = 12.sp)
                        Spacer(Modifier.width(5.dp))
                        Text("×", color = accent.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        PublishField(
            value = tags,
            onChange = onTagText,
            placeholder = stringResource(R.string.publish_tags_input),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (myTags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.publish_tags_mine),
                color = PikuColors.textFaint,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                myTags.take(12).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PikuColors.textSecondary.copy(alpha = 0.1f))
                            .clickable { onAppend(tag) }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        Text(tag, color = PikuColors.textSecondary, fontSize = 12.sp)
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
    onClick: () -> Unit,
    dark: Boolean,
) {
    val faint = PikuColors.textFaint
    SectionCard(dark = dark, clickable = onClick) {
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
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SummaryChip(
                text = stringResource(
                    if (state.publish) R.string.publish_options_summary_public
                    else R.string.publish_options_summary_private,
                ),
                bg = if (state.publish) FollowLight.copy(alpha = 0.3f) else faint.copy(alpha = 0.16f),
                fg = if (state.publish) FollowDark else faint,
            )
            SummaryChip(
                text = when (state.nsfw) {
                    NsfwLevel.ALL -> stringResource(R.string.publish_options_rating_all)
                    NsfwLevel.CUSHION -> stringResource(R.string.publish_options_rating_cushion)
                    NsfwLevel.R18 -> "R18"
                    NsfwLevel.R18PLUS -> "R18+"
                },
                bg = if (state.nsfw == NsfwLevel.ALL) faint.copy(alpha = 0.16f) else ErrorRedLight.copy(alpha = 0.3f),
                fg = if (state.nsfw == NsfwLevel.ALL) faint else ErrorRedDark,
            )
            if (state.visibility != ShowVisibility.ANYONE) {
                SummaryChip(
                    text = stringResource(
                        when (state.visibility) {
                            ShowVisibility.POIPIKU_LOGIN -> R.string.publish_options_visibility_login
                            ShowVisibility.FOLLOWER -> R.string.publish_options_visibility_follower
                            ShowVisibility.ANYONE -> R.string.publish_options_visibility_anyone
                        },
                    ),
                    bg = PikuColors.accent.copy(alpha = 0.14f),
                    fg = PikuColors.accent,
                )
            }
            if (state.publish && state.passwordEnabled) {
                SummaryChip(
                    text = stringResource(R.string.publish_options_password_on),
                    bg = PikuColors.accent.copy(alpha = 0.14f),
                    fg = PikuColors.accent,
                )
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
    var warnR18Plus by remember { mutableStateOf(false) }
    val faint = PikuColors.textFaint
    val accent = PikuColors.accent
    val red = ErrorRedDark

    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark, scrollable = true) {
        PikuSheetHandle()
        PikuSheetTitle(text = stringResource(R.string.publish_options))
        Spacer(Modifier.height(10.dp))

        SwitchRow(
            label = stringResource(R.string.publish_options_public_row),
            hint = stringResource(R.string.publish_options_public_off_hint),
            checked = state.publish,
            onChange = onPublic,
            dark = dark,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.publish_options_rating), color = faint, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        RatingSegmented(
            nsfw = state.nsfw,
            red = red,
            onPick = { level ->
                if (level == NsfwLevel.R18PLUS && state.nsfw != NsfwLevel.R18PLUS) {
                    warnR18Plus = true
                } else {
                    onNsfw(level)
                }
            },
        )

        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.publish_options_visibility_title), color = faint, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        VisibilityOption(
            label = stringResource(R.string.publish_options_visibility_anyone),
            selected = state.visibility == ShowVisibility.ANYONE,
            accent = accent,
            onClick = { onVisibility(ShowVisibility.ANYONE) },
        )
        VisibilityOption(
            label = stringResource(R.string.publish_options_visibility_login),
            selected = state.visibility == ShowVisibility.POIPIKU_LOGIN,
            accent = accent,
            onClick = { onVisibility(ShowVisibility.POIPIKU_LOGIN) },
        )
        VisibilityOption(
            label = stringResource(R.string.publish_options_visibility_follower),
            selected = state.visibility == ShowVisibility.FOLLOWER,
            accent = accent,
            onClick = { onVisibility(ShowVisibility.FOLLOWER) },
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = PikuColors.border)
        if (!state.publish) {
            Text(
                text = stringResource(R.string.publish_options_public_off_hint) + "，无需浏览密码",
                color = faint,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            SwitchRow(
                label = stringResource(R.string.publish_options_password),
                hint = if (state.passwordEnabled) {
                    stringResource(R.string.publish_options_password_enabled_hint)
                } else {
                    stringResource(R.string.publish_options_password_off_hint)
                },
                checked = state.passwordEnabled,
                onChange = onPasswordEnabled,
                dark = dark,
            )
            if (state.passwordEnabled) {
                Spacer(Modifier.height(6.dp))
                PublishField(
                    value = state.password,
                    onChange = onPassword,
                    placeholder = stringResource(R.string.publish_options_password_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = PikuColors.border)
        SwitchRow(
            label = stringResource(R.string.publish_options_recent),
            checked = state.showRecent,
            onChange = onRecent,
            dark = dark,
        )
        if (state.images.size >= 2 && state.visibility != ShowVisibility.ANYONE) {
            SwitchRow(
                label = stringResource(R.string.publish_options_first_only),
                checked = state.showFirstOnly,
                onChange = onFirstOnly,
                dark = dark,
            )
        } else {
            Text(
                text = stringResource(R.string.publish_options_first_only) + " · ≥2 图且限定可见",
                color = faint,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
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

    if (warnR18Plus) {
        AlertDialog(
            onDismissRequest = { warnR18Plus = false },
            title = { Text(stringResource(R.string.publish_options_r18plus_warn_title)) },
            text = { Text(stringResource(R.string.publish_options_r18plus_warn_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onNsfw(NsfwLevel.R18PLUS)
                    warnR18Plus = false
                }) { Text(stringResource(R.string.publish_ok), color = red) }
            },
            dismissButton = {
                TextButton(onClick = { warnR18Plus = false }) {
                    Text(stringResource(R.string.publish_cancel), color = PikuColors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    hint: String = "",
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = PikuColors.textPrimary, fontSize = 14.sp)
            if (hint.isNotBlank()) {
                Text(hint, color = PikuColors.textFaint, fontSize = 11.sp)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = themedSwitchColors(dark))
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
private fun VisibilityOption(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(1.5.dp, if (selected) accent else PikuColors.textFaint),
                    CircleShape,
                )
                .padding(3.dp),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected) accent else PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
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

// ---------- 发布中 / 失败覆盖层 ----------

@Composable
private fun BusyOverlay(
    progressText: String,
    fraction: Float? = null,
    detail: String? = null,
    dark: Boolean,
) {
    val accent = PikuColors.accent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 44.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (dark) GlassCardBgDark else GlassCardBgLight)
                .padding(22.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 3.dp,
                    color = accent,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = progressText,
                    color = PikuColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (detail != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        color = PikuColors.textFaint,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (fraction != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        color = accent,
                        trackColor = PikuColors.textFaint.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageFailedOverlay(
    title: String,
    detail: String,
    onRetry: () -> Unit,
    onAbort: () -> Unit,
    dark: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 44.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (dark) GlassCardBgDark else GlassCardBgLight)
                .padding(22.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = PikuColors.error,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    color = PikuColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
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
