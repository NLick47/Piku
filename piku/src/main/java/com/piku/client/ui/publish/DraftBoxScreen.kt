package com.piku.client.ui.publish

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.PublishDraft
import com.piku.client.domain.model.UploadKind
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.theme.ErrorRedDark
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.GlassCardBgLight
import com.piku.client.ui.theme.GlassCardBorderDark
import com.piku.client.ui.theme.GlassCardBorderLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftBoxScreen(
    onBack: () -> Unit,
    onDraftSelected: (Long) -> Unit,
    onNewDraft: () -> Unit,
) {
    val viewModel: DraftBoxViewModel = hiltViewModel()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val scope = rememberCoroutineScope()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }

    val visible = remember(drafts, query, filter) {
        viewModel.visible(drafts, query, filter)
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
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.menu_draft_box),
                        color = PikuColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    PikuBackButton(
                        onClick = onBack,
                        dark = dark,
                        contentDescription = stringResource(R.string.back),
                    )
                },
                actions = {
                    Text(
                        text = stringResource(R.string.draft_box_new),
                        color = PikuColors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onNewDraft() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.draft_box_search_hint),
                            fontSize = 13.sp,
                            color = PikuColors.textFaint,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = PikuColors.textFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = PikuColors.textFaint,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.setQuery("") },
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PikuColors.accent.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = PikuColors.textSecondary.copy(alpha = 0.08f),
                        unfocusedContainerColor = PikuColors.textSecondary.copy(alpha = 0.08f),
                        cursorColor = PikuColors.accent,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = PikuColors.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        text = stringResource(R.string.draft_box_filter_all, drafts.size),
                        selected = filter == DraftFilter.ALL,
                        onClick = { viewModel.setFilter(DraftFilter.ALL) },
                    )
                    FilterChip(
                        text = stringResource(R.string.draft_box_filter_novel, drafts.count { it.kind == UploadKind.NOVEL }),
                        selected = filter == DraftFilter.NOVEL,
                        onClick = { viewModel.setFilter(DraftFilter.NOVEL) },
                    )
                    FilterChip(
                        text = stringResource(R.string.draft_box_filter_illust, drafts.count { it.kind == UploadKind.ILLUST }),
                        selected = filter == DraftFilter.ILLUST,
                        onClick = { viewModel.setFilter(DraftFilter.ILLUST) },
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (visible.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.draft_box_empty),
                            color = PikuColors.textFaint,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible, key = { it.draftId ?: 0L }) { draft ->
                            DraftItem(
                                draft = draft,
                                onClick = { draft.draftId?.let(onDraftSelected) },
                                onDuplicate = {
                                    draft.draftId?.let { id ->
                                        scope.launch {
                                            viewModel.duplicate(id)
                                        }
                                    }
                                },
                                onDelete = {
                                    deleteTargetId = draft.draftId
                                    showDeleteConfirm = true
                                },
                                dark = dark,
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirm = false
                    deleteTargetId = null
                },
                title = { Text(stringResource(R.string.draft_box_delete_confirm_title)) },
                text = { Text(stringResource(R.string.draft_box_delete_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTargetId?.let { viewModel.delete(it) }
                        showDeleteConfirm = false
                        deleteTargetId = null
                    }) {
                        Text(stringResource(R.string.draft_box_delete), color = PikuColors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        deleteTargetId = null
                    }) {
                        Text(stringResource(R.string.publish_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = PikuColors.accent
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = text,
        color = if (selected) accent else PikuColors.textSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .then(
                if (!selected) Modifier.border(BorderStroke(0.5.dp, PikuColors.textFaint.copy(alpha = 0.3f)), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

@Composable
private fun DraftItem(
    draft: PublishDraft,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    dark: Boolean,
) {
    val accent = PikuColors.accent
    val bg = if (dark) GlassCardBgDark else GlassCardBgLight
    val border = if (dark) GlassCardBorderDark else GlassCardBorderLight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = draft.title.ifBlank {
                    if (draft.kind == UploadKind.NOVEL) "未命名小说" else "未命名图集"
                },
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val snippet = if (draft.kind == UploadKind.NOVEL && draft.body.isNotBlank()) {
                draft.body.trim().replace(Regex("\\s+"), " ").take(50)
            } else if (draft.tags.isNotBlank()) {
                draft.tags
            } else if (draft.description.isNotBlank()) {
                draft.description.take(50)
            } else ""
            if (snippet.isNotBlank()) {
                Text(
                    text = snippet,
                    color = PikuColors.textFaint,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
            }
            val saved = draft.savedAt
            val kindLabel = if (draft.kind == UploadKind.NOVEL) {
                "${draft.body.length} 字"
            } else {
                "${draft.imageFiles.size} 张"
            }
            Text(
                text = "${dateFormat.format(Date(saved ?: 0))} · $kindLabel",
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.draft_box_duplicate),
                tint = accent,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDuplicate),
            )
            Spacer(Modifier.height(6.dp))
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.draft_box_delete),
                tint = if (dark) ErrorRedDark else PikuColors.error,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDelete),
            )
        }
    }
}
