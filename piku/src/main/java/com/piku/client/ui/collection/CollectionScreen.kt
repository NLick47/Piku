package com.piku.client.ui.collection
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.TameWhiteColorFilter

/**
 * 玻璃卡片顶部的高光渐变，增强玻璃质感。
 */
private fun glassSheen(dark: Boolean): Brush =
    Brush.verticalGradient(
        0f to if (dark) Color(0x17FFFFFF) else Color(0x33FFFFFF),
        0.45f to Color.Transparent,
    )

@Composable
fun CollectionScreen(
    onWorkClick: (Work) -> Unit,
    onBack: () -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: CollectionViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val snackbarHostState = remember { SnackbarHostState() }

    val movedMessage = state.movedToFolder?.let { stringResource(R.string.collection_moved_to, it) }
    LaunchedEffect(movedMessage) {
        if (movedMessage != null) {
            snackbarHostState.showSnackbar(movedMessage)
            viewModel.clearFeedback()
        }
    }
    val actionFeedbackMessage = state.actionFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(actionFeedbackMessage) {
        if (actionFeedbackMessage != null) {
            snackbarHostState.showSnackbar(actionFeedbackMessage)
            viewModel.clearFeedback()
        }
    }

    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<FavoriteFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<FavoriteFolder?>(null) }

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
        val selectedFolderId = state.selectedFolderId
        if (selectedFolderId == null) {
            FolderListContent(
                folders = state.folders,
                loaded = state.loaded,
                dark = dark,
                isTablet = isTablet,
                onBack = onBack,
                onNewFolder = { creatingFolder = true },
                onFolderClick = viewModel::selectFolder,
                onRenameFolder = { renamingFolder = it },
                onDeleteFolder = { deletingFolder = it },
            )
        } else {
            FolderDetailContent(
                folderId = selectedFolderId,
                folderName = state.selectedFolderName,
                works = state.works,
                folders = state.folders,
                dark = dark,
                isTablet = isTablet,
                onBack = viewModel::backToFolders,
                onWorkClick = onWorkClick,
                onRemoveWork = viewModel::removeWorkFromFolder,
                onMoveWork = viewModel::moveWork,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        )
    }

    if (creatingFolder) {
        FolderNameDialog(
            title = stringResource(R.string.collection_create),
            initialName = "",
            confirmLabel = stringResource(R.string.detail_favorite_create),
            dark = dark,
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
            dark = dark,
            onConfirm = {
                viewModel.renameFolder(folder.id, it)
                renamingFolder = null
            },
            onDismiss = { renamingFolder = null },
        )
    }
    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            containerColor = if (dark) LoginCardDark else Color.White,
            title = {
                Text(
                    text = stringResource(R.string.collection_delete_confirm_title),
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.collection_delete_confirm_message, folder.name),
                    color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder.id)
                    deletingFolder = null
                }) {
                    Text(
                        text = stringResource(R.string.collection_delete),
                        color = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) {
                    Text(
                        text = stringResource(R.string.detail_favorite_cancel),
                        color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
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
    dark: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginCardDark else Color.White,
        title = {
            Text(
                text = title,
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
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
                    focusedContainerColor = if (dark) LoginCardDark else Color(0xFFEAE8E3),
                    unfocusedContainerColor = if (dark) LoginCardDark else Color(0xFFEAE8E3),
                    focusedBorderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                    unfocusedBorderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                    cursorColor = AccentDark,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(
                    text = confirmLabel,
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.detail_favorite_cancel),
                    color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                )
            }
        },
    )
}

@Composable
private fun FolderListContent(
    folders: List<FavoriteFolder>,
    loaded: Boolean,
    dark: Boolean,
    isTablet: Boolean,
    onBack: () -> Unit,
    onNewFolder: () -> Unit,
    onFolderClick: (FavoriteFolder) -> Unit,
    onRenameFolder: (FavoriteFolder) -> Unit,
    onDeleteFolder: (FavoriteFolder) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                )
            }
            Text(
                text = stringResource(R.string.collection_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewFolder) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.collection_create),
                    tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
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
                            tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.collection_empty),
                            color = if (dark) {
                                LoginTextSecondaryDark
                            } else {
                                LoginTextSecondaryLight
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(18.dp))
                        TextButton(onClick = onNewFolder) {
                            Text(
                                text = stringResource(R.string.collection_create),
                                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
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
                            tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
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
                                        colorFilter = if (dark) TameWhiteColorFilter else null,
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
                        color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
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
                        tint = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.collection_work_count, folder.workCount),
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = folder.name,
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

@Composable
private fun FolderDetailContent(
    folderId: Long,
    folderName: String,
    works: List<Work>,
    folders: List<FavoriteFolder>,
    dark: Boolean,
    isTablet: Boolean,
    onBack: () -> Unit,
    onWorkClick: (Work) -> Unit,
    onRemoveWork: (Long, Long) -> Unit,
    onMoveWork: (Work, Long, Long, String) -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    var actionWork by remember { mutableStateOf<Work?>(null) }
    var moveWorkTarget by remember { mutableStateOf<Work?>(null) }
    var removeConfirmWork by remember { mutableStateOf<Work?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.collection_work_count, works.size),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 12.sp,
                )
            }
        }
        if (works.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.collection_folder_empty),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 14.sp,
                )
            }
        } else {
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
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
            ) {
                items(works, key = { it.id }) { work ->
                    WorkCard(
                        work = work,
                        isFavorite = true,
                        onToggleFavorite = {},
                        onClick = { onWorkClick(work) },
                        dark = dark,
                        onLongClick = { actionWork = work },
                    )
                }
            }
        }
        actionWork?.let { work ->
            WorkActionSheet(
                work = work,
                dark = dark,
                onMove = {
                    actionWork = null
                    moveWorkTarget = work
                },
                onRemove = {
                    actionWork = null
                    removeConfirmWork = work
                },
                onDismiss = { actionWork = null },
            )
        }
        moveWorkTarget?.let { work ->
            MoveWorkSheet(
                work = work,
                currentFolderId = folderId,
                folders = folders,
                dark = dark,
                onMove = { target ->
                    moveWorkTarget = null
                    onMoveWork(work, folderId, target.id, target.name)
                },
                onDismiss = { moveWorkTarget = null },
            )
        }
        removeConfirmWork?.let { work ->
            AlertDialog(
                onDismissRequest = { removeConfirmWork = null },
                containerColor = if (dark) LoginCardDark else Color.White,
                title = {
                    Text(
                        text = stringResource(R.string.collection_remove_confirm_title),
                        color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            R.string.collection_remove_confirm_message,
                            work.title.ifBlank { stringResource(R.string.collection_work_actions) },
                        ),
                        color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        removeConfirmWork = null
                        onRemoveWork(folderId, work.id)
                    }) {
                        Text(
                            text = stringResource(R.string.collection_remove_confirm),
                            color = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { removeConfirmWork = null }) {
                        Text(
                            text = stringResource(R.string.detail_favorite_cancel),
                            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                        )
                    }
                },
            )
        }
    }
}

/** 长按作品弹出的操作面板：移动到其他收藏夹 / 移出该收藏夹 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkActionSheet(
    work: Work,
    dark: Boolean,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = work.title.ifBlank { stringResource(R.string.collection_work_actions) },
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (work.authorName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = work.authorName,
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            SheetActionRow(
                icon = Icons.Outlined.DriveFileMove,
                label = stringResource(R.string.collection_move_to),
                subtitle = stringResource(R.string.collection_move_subtitle),
                dark = dark,
                onClick = onMove,
            )
            Spacer(Modifier.height(8.dp))
            SheetActionRow(
                icon = Icons.Outlined.DeleteOutline,
                label = stringResource(R.string.collection_remove_from_folder),
                subtitle = stringResource(R.string.collection_remove_subtitle),
                dark = dark,
                danger = true,
                onClick = onRemove,
            )
        }
    }
}

/** 选择目标收藏夹的移动面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveWorkSheet(
    work: Work,
    currentFolderId: Long,
    folders: List<FavoriteFolder>,
    dark: Boolean,
    onMove: (FavoriteFolder) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.collection_move_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = work.title,
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            val targets = folders.filter { it.id != currentFolderId }
            if (targets.isEmpty()) {
                Text(
                    text = stringResource(R.string.collection_move_no_target),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 13.sp,
                )
            } else {
                targets.forEach { folder ->
                    FolderPickRow(
                        folder = folder,
                        dark = dark,
                        onClick = { onMove(folder) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun FolderPickRow(
    folder: FavoriteFolder,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) Color(0x12FFFFFF) else Color(0x0A000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (folder.isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = folder.name,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
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
        Text(
            text = stringResource(R.string.collection_work_count, folder.workCount),
            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
            fontSize = 12.sp,
        )
    }
}

/** 底部操作面板中的动作行：图标气泡 + 标题/副标题 + 右箭头，支持危险区样式 */
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    dark: Boolean,
    subtitle: String? = null,
    tint: Color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val dangerTint = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B)
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
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
