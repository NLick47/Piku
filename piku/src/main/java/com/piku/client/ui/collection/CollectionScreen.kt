package com.piku.client.ui.collection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.LocalDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.piku.client.ui.common.CardProgress
import com.piku.client.ui.common.FeedbackHost
import com.piku.client.ui.common.GlassCard
import com.piku.client.ui.common.PikuBottomSheet
import com.piku.client.ui.common.PikuSheetSubtitle
import com.piku.client.ui.common.PikuSheetTitle
import com.piku.client.ui.common.motionDuration
import com.piku.client.ui.common.rememberReducedMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.FolderSort
import com.piku.client.domain.model.ReadingProgress
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.common.resolve
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.PikuColors

/**
 * 玻璃卡片顶部的高光渐变，增强玻璃质感。
 */
private fun glassSheen(dark: Boolean): Brush =
    Brush.verticalGradient(
        0f to if (dark) Color(0x17FFFFFF) else Color(0x33FFFFFF),
        0.45f to Color.Transparent,
    )

/** 收藏归属的面板语义：添加到（保留原夹）与移动到（原夹不再保留）是两件事 */
private enum class FolderPickMode { ADD, MOVE }

@Composable
fun CollectionScreen(
    onWorkClick: (Work) -> Unit,
    onAuthorClick: (Work) -> Unit,
    onBack: () -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: CollectionViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    FeedbackHost(channel = viewModel.feedback, snackbarHostState = snackbarHostState)

    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<FavoriteFolder?>(null) }
    var pickMode by remember { mutableStateOf<FolderPickMode?>(null) }

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
        if (state.view is CollectionView.Folders) {
            FolderListContent(
                folders = state.folders,
                totalWorks = state.totalWorks,
                loaded = state.loaded,
                dark = dark,
                isTablet = isTablet,
                onBack = onBack,
                onNewFolder = { creatingFolder = true },
                onFolderClick = viewModel::selectFolder,
                onRenameFolder = { renamingFolder = it },
                onDeleteFolder = viewModel::requestDeleteFolder,
                onOpenAll = { viewModel.openAll() },
                onSearchAll = { viewModel.openAll(focusSearch = true) },
            )
        } else {
            FolderDetailContent(
                state = state,
                dark = dark,
                isTablet = isTablet,
                onBack = viewModel::backToFolders,
                onWorkClick = onWorkClick,
                onAuthorClick = onAuthorClick,
                onEnterSelection = viewModel::enterSelection,
                onExitSelection = viewModel::exitSelection,
                onToggleSelect = viewModel::toggleSelection,
                onToggleSelectAll = viewModel::toggleSelectAll,
                onQueryChange = viewModel::setQuery,
                onClearQuery = viewModel::clearQuery,
                onSortChange = viewModel::setSort,
                onSearchFocusConsumed = viewModel::consumeSearchFocus,
            )
        }

        // 多选工具条与撤销条互斥：批量操作结束会退出多选，撤销条随即接管底部
        SelectionActionBar(
            visible = state.selectionMode,
            selectedCount = state.selectedIds.size,
            removeLabel = stringResource(
                if (state.allScope) R.string.collection_remove_unfavorite
                else R.string.collection_remove_short,
            ),
            dark = dark,
            onAdd = {
                // 只选了一件时先查它的已有归属，面板才能标出「已添加」的收藏夹
                viewModel.loadSelectedFolderIds()
                pickMode = FolderPickMode.ADD
            },
            onMove = { pickMode = FolderPickMode.MOVE },
            onRemove = viewModel::removeSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
        CollectionUndoBar(
            // 延迟入场：等多选工具条先退场（180ms）再淡入，交接瞬间两者叠在同一位置会闪
            visible = !state.selectionMode && state.undoLabel != null,
            label = state.undoLabel?.resolve(context).orEmpty(),
            pendingCount = state.undoCount,
            dark = dark,
            onUndo = viewModel::undo,
            onDismiss = viewModel::dismissUndo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp),
        )
    }

    pickMode?.let { mode ->
        FolderPickSheet(
            mode = mode,
            folders = state.folders,
            // 「全部收藏」没有"当前收藏夹"，传 -1 表示不排除任何收藏夹
            currentFolderId = state.currentFolderId ?: -1L,
            addedFolderIds = if (mode == FolderPickMode.ADD) state.actionWorkFolderIds else emptySet(),
            dark = dark,
            onPick = { folder ->
                pickMode = null
                when (mode) {
                    FolderPickMode.ADD -> viewModel.addSelectedTo(folder)
                    FolderPickMode.MOVE -> viewModel.moveSelectedTo(folder)
                }
            },
            onDismiss = { pickMode = null },
        )
    }

    if (creatingFolder) {
        FolderNameDialog(
            title = stringResource(R.string.collection_create),
            initialName = "",
            confirmLabel = stringResource(R.string.detail_favorite_create),
            takenNames = state.folders.mapTo(mutableSetOf()) { it.name },
            onConfirm = {
                viewModel.createFolder(it)
                creatingFolder = false
            },
            onDismiss = { creatingFolder = false },
        )
    }
    renamingFolder?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.collection_rename),
            initialName = folder.name,
            confirmLabel = stringResource(R.string.collection_rename_confirm),
            // 排除自己：把名字改回去（或只动空格）不算重名
            takenNames = state.folders
                .filterNot { it.id == folder.id }
                .mapTo(mutableSetOf()) { it.name },
            onConfirm = {
                viewModel.renameFolder(folder.id, it)
                renamingFolder = null
            },
            onDismiss = { renamingFolder = null },
        )
    }
    state.deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteFolder,
            containerColor = PikuColors.surface,
            title = {
                Text(
                    text = stringResource(R.string.collection_delete_confirm_title),
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            R.string.collection_delete_confirm_message,
                            request.folder.name,
                        ),
                        color = PikuColors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (request.exclusiveCount > 0) {
                            stringResource(
                                R.string.collection_delete_confirm_exclusive,
                                request.exclusiveCount,
                            )
                        } else {
                            stringResource(R.string.collection_delete_confirm_kept)
                        },
                        color = if (request.exclusiveCount > 0) {
                            PikuColors.error
                        } else {
                            PikuColors.textFaint
                        },
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteFolder) {
                    Text(
                        text = stringResource(R.string.collection_delete),
                        color = PikuColors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteFolder) {
                    Text(
                        text = stringResource(R.string.detail_favorite_cancel),
                        color = PikuColors.textSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    /** 已被其他收藏夹占用的名字：同步按名字认收藏夹，重名会在云端被并成一个，所以这里就拦住 */
    takenNames: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val taken = name.trim() in takenNames
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PikuColors.surface,
        title = {
            Text(
                text = title,
                color = PikuColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.detail_favorite_new_hint),
                            fontSize = 13.sp,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PikuColors.surfaceMuted,
                        unfocusedContainerColor = PikuColors.surfaceMuted,
                        focusedBorderColor = PikuColors.border,
                        unfocusedBorderColor = PikuColors.border,
                        cursorColor = AccentDark,
                    ),
                )
                // 重名在这里就报出来，不等到点确认再被数据层弹回
                if (taken) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.collection_folder_name_taken),
                        color = PikuColors.error,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && !taken,
            ) {
                Text(
                    text = confirmLabel,
                    color = PikuColors.textPrimary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.detail_favorite_cancel),
                    color = PikuColors.textSecondary,
                )
            }
        },
    )
}

@Composable
private fun FolderListContent(
    folders: List<FavoriteFolder>,
    totalWorks: Int,
    loaded: Boolean,
    dark: Boolean,
    isTablet: Boolean,
    onBack: () -> Unit,
    onNewFolder: () -> Unit,
    onFolderClick: (FavoriteFolder) -> Unit,
    onRenameFolder: (FavoriteFolder) -> Unit,
    onDeleteFolder: (FavoriteFolder) -> Unit,
    onOpenAll: () -> Unit,
    onSearchAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
                .statusBarsPadding()
                .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PikuBackButton(
                onClick = onBack,
                dark = dark,
                contentDescription = stringResource(R.string.back),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.collection_title),
                    color = PikuColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (loaded && folders.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.collection_summary,
                                folders.size,
                                totalWorks,
                            ),
                            color = PikuColors.textFaint,
                            fontSize = 11.sp,
                        )
                }
            }
            // 放大镜直接落到「全部收藏」并聚焦检索框：回答"我把这个存哪了"
            IconButton(onClick = onSearchAll) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.collection_search_all),
                    tint = PikuColors.textPrimary,
                )
            }
            IconButton(onClick = onNewFolder) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.collection_create),
                    tint = PikuColors.textPrimary,
                )
            }
        }
        when {
            !loaded -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoaderDots(dark = dark)
                }
            }
            folders.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = PikuColors.textFaint,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.collection_empty),
                            color = PikuColors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(18.dp))
                        TextButton(onClick = onNewFolder) {
                            Text(
                                text = stringResource(R.string.collection_create),
                                color = PikuColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = if (isTablet) GridCells.Adaptive(200.dp) else GridCells.Fixed(2),
                    state = rememberLazyGridState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 「全部收藏」不是收藏夹，用整行入口与收藏夹卡片区分开
                    if (totalWorks > 0) {
                        item(key = "all-favorites", span = { GridItemSpan(maxLineSpan) }) {
                            AllFavoritesEntry(
                                workCount = totalWorks,
                                dark = dark,
                                onClick = onOpenAll,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    items(folders, key = { it.id }) { folder ->
                        FolderCard(
                            folder = folder,
                            dark = dark,
                            onClick = { onFolderClick(folder) },
                            onRename = onRenameFolder,
                            onDelete = onDeleteFolder,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「全部收藏」入口：跨收藏夹的全部作品。
 *
 * 它不是收藏夹——没有主键、不能重命名或删除，所以刻意不做成收藏夹卡片的样式：
 * 整行入口 + 图标气泡，既和收藏夹网格分层，也不会让用户以为它能长按管理。
 */
@Composable
private fun AllFavoritesEntry(
    workCount: Int,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
            .clip(shape)
            .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
            .border(
                BorderStroke(1.dp, if (dark) Color(0x3DFFFFFF) else Color(0x80FFFFFF)),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (dark) Color(0x14FFFFFF) else Color(0x0D2C2C2C)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = PikuColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.collection_all_title),
            color = PikuColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.collection_work_count, workCount),
            color = PikuColors.textFaint,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: FavoriteFolder,
    dark: Boolean,
    onClick: () -> Unit,
    onRename: (FavoriteFolder) -> Unit,
    onDelete: (FavoriteFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val placeholderColor = if (dark) Color(0x12FFFFFF) else Color(0x1F2C2C2C)
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
                .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
                .clip(shape)
                .border(
                    BorderStroke(1.dp, if (dark) Color(0x3DFFFFFF) else Color(0x80FFFFFF)),
                    shape,
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                ),
        ) {
            // 顶部高光，让玻璃更通透
            Box(
                Modifier
                    .matchParentSize()
                    .background(glassSheen(dark)),
            )
            Column(Modifier.padding(12.dp)) {
                if (folder.previewUrls.isEmpty()) {
                    // 空收藏夹：占位图
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2.2f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(placeholderColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = PikuColors.textFaint,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                } else {
                    // 最近收藏的 3 张作品缩略图，不进入也能预览内容
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(3) { index ->
                            val url = folder.previewUrls.getOrNull(index)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(placeholderColor),
                            ) {
                                if (url != null) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        colorFilter = PikuColors.tameWhiteFilter,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = folder.name,
                        color = PikuColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (folder.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        DefaultFolderBadge(dark = dark)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = PikuColors.textSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.collection_work_count, folder.workCount),
                        color = PikuColors.textFaint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        if (menuOpen) {
            FolderActionSheet(
                folder = folder,
                dark = dark,
                onRename = {
                    menuOpen = false
                    onRename(folder)
                },
                onDelete = {
                    menuOpen = false
                    onDelete(folder)
                },
                onDismiss = { menuOpen = false },
            )
        }
    }
}

/** 长按收藏夹卡片弹出的操作面板：重命名 / 删除（默认收藏夹不可删除） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderActionSheet(
    folder: FavoriteFolder,
    dark: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
    ) {
            PikuSheetTitle(text = folder.name)
            if (folder.isDefault) {
                Spacer(Modifier.height(4.dp))
                DefaultFolderBadge(dark = dark)
            }
            Spacer(Modifier.height(16.dp))
            SheetActionRow(
                icon = Icons.Outlined.Edit,
                label = stringResource(R.string.collection_rename),
                subtitle = stringResource(R.string.collection_rename_subtitle),
                dark = dark,
                onClick = onRename,
            )
            if (!folder.isDefault) {
                Spacer(Modifier.height(8.dp))
                SheetActionRow(
                    icon = Icons.Outlined.DeleteOutline,
                    label = stringResource(R.string.collection_delete),
                    subtitle = stringResource(R.string.collection_delete_subtitle),
                    dark = dark,
                    danger = true,
                    onClick = onDelete,
                )
            }
    }
}

/** 「默认」小徽标：标识快速收藏的落点收藏夹。 */
@Composable
private fun DefaultFolderBadge(dark: Boolean) {
    Text(
        text = stringResource(R.string.collection_default_badge),
        color = PikuColors.textSecondary,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (dark) Color(0x22FFFFFF) else Color(0x142C2C2C))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun FolderDetailContent(
    state: CollectionUiState,
    dark: Boolean,
    isTablet: Boolean,
    onBack: () -> Unit,
    onWorkClick: (Work) -> Unit,
    onAuthorClick: (Work) -> Unit,
    onEnterSelection: (Long?) -> Unit,
    onExitSelection: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onToggleSelectAll: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSortChange: (FolderSort) -> Unit,
    onSearchFocusConsumed: () -> Unit,
) {
    var sortSheetOpen by remember { mutableStateOf(false) }
    // 检索框默认只在「全部收藏」展开：跨夹检索才是它的主场。夹内收成一枚放大镜圆钮，
    // 不然每个收藏夹一进来就先顶着一整条输入框。换视图、换夹都重置，状态不跨视图带走
    var searchOpen by rememberSaveable(state.currentFolderId, state.allScope) {
        mutableStateOf(state.allScope)
    }
    // 点圆钮展开时要顺手拉起键盘，和顶栏放大镜进来是同一件事，共用一个 autoFocus 通道
    var focusOnExpand by remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()
    // 换收藏夹、换排序、改检索词都要回到顶部：否则列表一收缩，滚动位置会停在旧位置上
    LaunchedEffect(state.currentFolderId, state.allScope, state.sort, state.query) {
        gridState.scrollToItem(0)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.selectionMode) {
                IconButton(onClick = onExitSelection) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_cancel),
                        tint = PikuColors.textPrimary,
                    )
                }
                Text(
                    // 一件未选时给一句指令而不是沿用收藏夹名：否则多选态和普通态顶栏看起来一样
                    text = if (state.selectedIds.isEmpty()) {
                        stringResource(R.string.collection_select_hint)
                    } else {
                        stringResource(R.string.collection_selected_count, state.selectedIds.size)
                    },
                    color = PikuColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggleSelectAll) {
                    Text(
                        text = stringResource(
                            if (state.allSelected) {
                                R.string.collection_select_none
                            } else {
                                R.string.collection_select_all
                            },
                        ),
                        color = PikuColors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                PikuBackButton(
                    onClick = onBack,
                    dark = dark,
                    contentDescription = stringResource(R.string.detail_back),
                )
                if (searchOpen) {
                    // 检索胶囊顶掉标题占住头栏：内容区不再被一条工具条压着往下让位
                    CollectionSearchPill(
                        query = state.query,
                        hint = stringResource(
                            // 「全部收藏」的标题位让给了胶囊，靠提示语说明这框搜的是哪儿
                            if (state.allScope) {
                                R.string.collection_search_all
                            } else {
                                R.string.collection_search_hint
                            },
                        ),
                        // 匹配数收进胶囊尾部而不是另起一行：头栏高度不随检索变化，列表就不会跳
                        matchLabel = if (state.searching) {
                            stringResource(
                                R.string.collection_count_ratio,
                                state.works.size,
                                state.worksTotal,
                            )
                        } else {
                            null
                        },
                        autoFocus = state.focusSearch || focusOnExpand,
                        dark = dark,
                        onQueryChange = onQueryChange,
                        onClearQuery = onClearQuery,
                        onCollapse = { searchOpen = false },
                        onFocusConsumed = {
                            focusOnExpand = false
                            onSearchFocusConsumed()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                    )
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            // 「全部收藏」是跨收藏夹的视图，标题由视图决定而不是收藏夹名
                            text = when (val view = state.view) {
                                CollectionView.All -> stringResource(R.string.collection_all_title)
                                CollectionView.Folders -> stringResource(R.string.collection_title)
                                is CollectionView.InFolder -> view.name
                            },
                            color = PikuColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 读列表期间不报数：那会儿 worksTotal 还是 0，先亮一个"0 个投稿"是假信息
                        if (!state.listLoading) {
                            Text(
                                text = stringResource(R.string.collection_work_count, state.worksTotal),
                                color = PikuColors.textFaint,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                if (state.worksTotal > 0) {
                    // 夹内的检索入口：点开后胶囊接管标题位。「全部收藏」里胶囊常驻，不需要这枚钮
                    if (!searchOpen) {
                        IconButton(onClick = {
                            searchOpen = true
                            focusOnExpand = true
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search_action),
                                tint = PikuColors.textPrimary,
                            )
                        }
                    }
                    // 排序圆钮：非默认排序时染 accent，不用多叠小圆点也能一眼看出来
                    IconButton(onClick = { sortSheetOpen = true }) {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = sortLabel(state.sort),
                            tint = if (state.sort == FolderSort.ADDED) {
                                PikuColors.textPrimary
                            } else {
                                PikuColors.accent
                            },
                        )
                    }
                    // 多选入口：长按任意一张卡也能进，这枚钮留给不知道长按的人
                    IconButton(onClick = { onEnterSelection(null) }) {
                        Icon(
                            imageVector = Icons.Outlined.Checklist,
                            contentDescription = stringResource(R.string.collection_select_hint),
                            tint = PikuColors.textPrimary,
                        )
                    }
                }
            }
        }
        if (state.worksTotal == 0 && state.focusSearch && !state.listLoading) {
            // 确实没有作品可检索（不是还没读完）：立刻把标记消费掉，
            // 否则它会一直挂着，等下次进到有内容的视图时突然弹出键盘
            LaunchedEffect(Unit) { onSearchFocusConsumed() }
        }
        when {
            // 列表还在读：先给 loader，否则会闪一帧「这个收藏夹还没有投稿」
            state.listLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoaderDots(dark = dark)
            }
            state.worksTotal == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        if (state.allScope) {
                            R.string.collection_all_empty
                        } else {
                            R.string.collection_folder_empty
                        },
                    ),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            state.works.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // 有投稿但检索不到：空态要说清是"没匹配"而不是"夹是空的"
                Text(
                    text = stringResource(R.string.collection_search_empty),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            else -> {
                LazyVerticalStaggeredGrid(
                    columns = if (isTablet) {
                        StaggeredGridCells.Adaptive(220.dp)
                    } else {
                        StaggeredGridCells.Fixed(2)
                    },
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 2.dp,
                        bottom = if (state.selectionMode) 168.dp else 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                ) {
                    state.groups.forEach { group ->
                        if (group.label != null) {
                            item(
                                key = "header-${group.key}",
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                Column {
                                    // 分组头与上一段卡片之间留一拍呼吸，多段分组时尤其需要
                                    Spacer(Modifier.height(4.dp))
                                    AuthorSectionHeader(
                                        name = group.label,
                                        avatarUrl = group.authorAvatarUrl,
                                        workCount = group.works.size,
                                        dark = dark,
                                        onClick = {
                                            group.works.firstOrNull()?.let(onAuthorClick)
                                        },
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                        items(group.works, key = { it.id }) { work ->
                            SelectableWorkCard(
                                work = work,
                                selected = work.id in state.selectedIds,
                                selectionMode = state.selectionMode,
                                progress = state.progress[work.id].toCardProgress(work),
                                dark = dark,
                                onOpen = { onWorkClick(work) },
                                // 长按直接进多选并带上这张：单件操作走底部工具条，
                                // 不再为一件作品弹一个四选项的面板
                                onLongPress = { onEnterSelection(work.id) },
                                onToggleSelect = { onToggleSelect(work.id) },
                                onAuthorClick = onAuthorClick,
                            )
                        }
                    }
                }
            }
        }
        if (sortSheetOpen) {
            SortSheet(
                current = state.sort,
                dark = dark,
                onSelect = {
                    sortSheetOpen = false
                    onSortChange(it)
                },
                onDismiss = { sortSheetOpen = false },
            )
        }
    }
}

/**
 * 收藏夹内的作品卡：多选态下整卡点击改为切换选中，视觉上叠一层描边与勾选角标。
 * 遮罩不挂点击——点击统一由卡片自身分发，避免两层同时响应导致选中状态来回翻转。
 */
@Composable
private fun SelectableWorkCard(
    work: Work,
    selected: Boolean,
    selectionMode: Boolean,
    progress: CardProgress?,
    dark: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
    onAuthorClick: (Work) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val reduced = rememberReducedMotion()
    val accent = PikuColors.accent
    val checkedTint = if (dark) Color(0xFF1C1B19) else Color.White
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.95f else 1f,
        animationSpec = tween(motionDuration(reduced, 150)),
        label = "selectionScale",
    )
    val badge by animateFloatAsState(
        targetValue = if (selectionMode) 1f else 0f,
        animationSpec = tween(motionDuration(reduced, 150)),
        label = "selectionBadge",
    )
    Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        WorkCard(
            work = work,
            isFavorite = true,
            onToggleFavorite = {},
            onClick = { if (selectionMode) onToggleSelect() else onOpen() },
            dark = dark,
            onLongClick = { if (!selectionMode) onLongPress() },
            onAuthorClick = if (selectionMode) null else onAuthorClick,
            progress = progress,
        )
        if (selectionMode && selected) {
            // 只标记选中的卡：未选中的卡保持原样，避免整片压暗被看成"全都选中了"
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(accent.copy(alpha = 0.16f))
                    .border(2.dp, accent, shape),
            )
        }
        if (badge > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .graphicsLayer {
                        scaleX = badge
                        scaleY = badge
                        alpha = badge
                    }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (selected) accent else Color(0xB3FFFFFF),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = checkedTint,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** 多选工具条：玻璃面板 + 三个动作，浮在网格之上；一件未选时三个动作都不可点 */
@Composable
private fun SelectionActionBar(
    visible: Boolean,
    selectedCount: Int,
    removeLabel: String,
    dark: Boolean,
    onAdd: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = selectedCount > 0
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it },
        modifier = modifier,
    ) {
        GlassCard(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            bgColor = if (dark) Color(0xF2262421) else Color(0xF7FFFFFF),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarAction(
                    icon = Icons.Outlined.BookmarkAdd,
                    label = stringResource(R.string.collection_add_short),
                    dark = dark,
                    enabled = hasSelection,
                    onClick = onAdd,
                    modifier = Modifier.weight(1f),
                )
                BarAction(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    label = stringResource(R.string.collection_move_short),
                    dark = dark,
                    enabled = hasSelection,
                    onClick = onMove,
                    modifier = Modifier.weight(1f),
                )
                // 一根细线把「移出/取消收藏」和另外两个动作分开：不可逆的那个不该和它们连成一片
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .width(0.5.dp)
                        .height(22.dp)
                        .background(PikuColors.border),
                )
                BarAction(
                    icon = Icons.Outlined.DeleteOutline,
                    label = removeLabel,
                    dark = dark,
                    danger = true,
                    enabled = hasSelection,
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 工具条里的一颗动作：图标压着文字竖排，常态无底无框。
 *
 * 面板本身已经是玻璃面，再给每颗按钮套一层底色，整条工具条就成了"盒子里的三个盒子"，
 * 底色只在按下那一刻浮起来。竖排也不是随手排的：日文的「お気に入り解除」横排会把图标挤没。
 */
@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> PikuColors.textFaint
        danger -> PikuColors.error
        else -> PikuColors.textPrimary
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (pressed && enabled) {
                    if (dark) Color(0x14FFFFFF) else Color(0x0D2C2C2C)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 撤销条：承载移出/移动的结果反馈，常驻直到点撤销或关闭 */
@Composable
private fun CollectionUndoBar(
    visible: Boolean,
    label: String,
    pendingCount: Int,
    dark: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    AnimatedVisibility(
        visible = visible,
        // fadeIn 延迟 180ms：与多选工具条的退场时长错开，
        // 批量操作结束的瞬间两者锚在同一位置，同时可见会叠影闪烁
        enter = fadeIn(
            tween(160, delayMillis = 180),
        ) + slideInVertically(tween(200, delayMillis = 180)) { it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
                .clip(shape)
                .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
                .border(BorderStroke(0.5.dp, PikuColors.border), shape)
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = PikuColors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pendingCount > 1) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = stringResource(R.string.collection_undo_pending, pendingCount),
                        color = PikuColors.textFaint,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_undo),
                    color = PikuColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.search_cancel),
                    tint = PikuColors.textFaint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}


/** 选择目标收藏夹的面板：添加到与移动到共用，靠 mode 区分文案与不可选项 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickSheet(
    mode: FolderPickMode,
    folders: List<FavoriteFolder>,
    currentFolderId: Long,
    addedFolderIds: Set<Long>,
    dark: Boolean,
    onPick: (FavoriteFolder) -> Unit,
    onDismiss: () -> Unit,
) {
    val add = mode == FolderPickMode.ADD
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
        scrollable = true,
    ) {
            PikuSheetTitle(
                text = stringResource(
                    if (add) R.string.collection_add_title else R.string.collection_move_title,
                ),
            )
            Spacer(Modifier.height(4.dp))
            PikuSheetSubtitle(
                text = stringResource(
                    if (add) R.string.collection_add_subtitle else R.string.collection_move_subtitle,
                ),
            )
            Spacer(Modifier.height(14.dp))
            val targets = folders.filterNot { it.id == currentFolderId }
            if (targets.isEmpty()) {
                Text(
                    text = stringResource(R.string.collection_move_no_target),
                    color = PikuColors.textFaint,
                    fontSize = 13.sp,
                )
            } else {
                targets.forEach { folder ->
                    val alreadyAdded = add && folder.id in addedFolderIds
                    FolderPickRow(
                        folder = folder,
                        dark = dark,
                        disabled = alreadyAdded,
                        statusLabel = if (alreadyAdded) {
                            stringResource(R.string.collection_already_added)
                        } else {
                            null
                        },
                        onClick = { if (!alreadyAdded) onPick(folder) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
    }
}

@Composable
private fun FolderPickRow(
    folder: FavoriteFolder,
    dark: Boolean,
    onClick: () -> Unit,
    disabled: Boolean = false,
    statusLabel: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) Color(0x12FFFFFF) else Color(0x0A000000))
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (folder.isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = if (disabled) PikuColors.textFaint else PikuColors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = folder.name,
            color = if (disabled) PikuColors.textFaint else PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (folder.isDefault) {
            Spacer(Modifier.width(6.dp))
            DefaultFolderBadge(dark = dark)
        }
        Spacer(Modifier.width(8.dp))
        if (statusLabel != null) {
            Text(
                text = statusLabel,
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (dark) Color(0x22FFFFFF) else Color(0x142C2C2C))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.collection_work_count, folder.workCount),
                color = PikuColors.textFaint,
                fontSize = 12.sp,
            )
        }
    }
}

/** 底部操作面板中的动作行：图标气泡 + 标题/副标题 + 右箭头，支持危险区样式 */
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    dark: Boolean,
    subtitle: String? = null,
    tint: Color = PikuColors.textPrimary,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val dangerTint = PikuColors.error
    val faint = PikuColors.textFaint
    // 危险区：浅红底色容器 + 红图标气泡；普通动作：透明容器 + 中性图标气泡
    val rowBg = if (danger) {
        if (dark) Color(0x1AE08A8A) else Color(0x14C24B4B)
    } else {
        Color.Transparent
    }
    val bubbleBg = if (danger) {
        if (dark) Color(0x2EE08A8A) else Color(0x1FC24B4B)
    } else {
        if (dark) Color(0x14FFFFFF) else Color(0x0D2C2C2C)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(bubbleBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (danger) dangerTint else tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (danger) dangerTint else tint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = faint,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = faint,
            modifier = Modifier.size(16.dp),
        )
    }
}

internal fun ReadingProgress?.toCardProgress(work: Work): CardProgress? = when {
    this == null -> null
    hasImage && imagePage > 1 -> {
        val total = work.imageCount.takeIf { it > 1 }
        if (total == null) {
            null
        } else {
            val page = imagePage.coerceAtMost(total)
            CardProgress(page.toFloat() / total, "$page/$total")
        }
    }
    hasNovel -> CardProgress(novelPercent / 100f, "$novelPercent%")
    else -> null
}

/** 排序 chip 与排序面板共用的短标签 */
@Composable
private fun sortLabel(sort: FolderSort): String = stringResource(
    when (sort) {
        FolderSort.ADDED -> R.string.collection_sort_added
        FolderSort.TITLE -> R.string.collection_sort_title_name
        FolderSort.AUTHOR -> R.string.collection_sort_author
    },
)

@Composable
private fun sortSubtitle(sort: FolderSort): String? = when (sort) {
    FolderSort.ADDED -> stringResource(R.string.collection_sort_added_sub)
    FolderSort.AUTHOR -> stringResource(R.string.collection_sort_author_sub)
    FolderSort.TITLE -> null
}

@Composable
private fun CollectionSearchPill(
    query: String,
    hint: String,
    matchLabel: String?,
    autoFocus: Boolean,
    dark: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onCollapse: () -> Unit,
    onFocusConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // 挂上时也会回调一次未聚焦：用 wasFocused 区分"从没聚焦过"和"聚焦后又离开"
    var wasFocused by remember { mutableStateOf(false) }
    // 从放大镜进来（或点开圆钮）时抢一次焦点，让用户直接打字；消费掉标记，重组不再重复抢
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            onFocusConsumed()
        }
    }
    // 空查询且已失焦就收回。点清除未必会让输入框失焦，所以盯状态而不是某一次回调
    LaunchedEffect(query, focused) {
        if (wasFocused && query.isEmpty() && !focused) {
            wasFocused = false
            onCollapse()
        }
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(pill)
            // 与页面米灰底同族但更亮一级的半透明白：既成组又不刺眼
            .background(if (dark) Color(0x14FFFFFF) else Color(0xD9FFFFFF))
            .border(BorderStroke(0.5.dp, PikuColors.border), pill)
            // 点胶囊空白处也聚焦输入框，而不是毫无反应
            .clickable { focusRequester.requestFocus() }
            .padding(start = 13.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = hint,
                    color = PikuColors.textFaint,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = PikuColors.textPrimary),
                cursorBrush = SolidColor(PikuColors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // 收起键盘就等于"检索完毕"：空胶囊随后自己收回
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        focused = focusState.isFocused
                        if (focusState.isFocused) wasFocused = true
                    },
            )
        }
        if (matchLabel != null) {
            // 匹配数与总数都要在，只给一个数字容易让人以为投稿丢了
            Text(
                text = matchLabel,
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (query.isNotEmpty()) {
            // 清除做成 28dp 圆形热区：图标只有 14dp，热区不够点起来会难受
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClearQuery),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.collection_search_clear),
                    tint = PikuColors.textFaint,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 排序选择面板：单选列表，当前项打勾 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: FolderSort,
    dark: Boolean,
    onSelect: (FolderSort) -> Unit,
    onDismiss: () -> Unit,
) {
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
    ) {
        PikuSheetTitle(text = stringResource(R.string.collection_sort_title))
        Spacer(Modifier.height(12.dp))
        FolderSort.entries.forEachIndexed { index, sort ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            SortRow(
                label = sortLabel(sort),
                subtitle = sortSubtitle(sort),
                selected = sort == current,
                dark = dark,
                onClick = { onSelect(sort) },
            )
        }
    }
}

@Composable
private fun SortRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    PikuColors.accent.copy(alpha = if (dark) 0.16f else 0.10f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (selected) PikuColors.accent else PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PikuColors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 按作者分组时的段头：头像 + 作者名 + 数量，整行可点进作者作品页 */
@Composable
private fun AuthorSectionHeader(
    name: String,
    avatarUrl: String?,
    workCount: Int,
    dark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = 2.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            onClick = onClick,
            dark = dark,
            size = 28.dp,
            showIndication = false,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = name.ifBlank { stringResource(R.string.collection_unknown_author) },
            color = PikuColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.collection_work_count, workCount),
            color = PikuColors.textFaint,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(14.dp),
        )
    }
}
