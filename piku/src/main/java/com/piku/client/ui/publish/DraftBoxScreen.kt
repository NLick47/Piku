package com.piku.client.ui.publish

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DraftBoxScreen(
    onBack: () -> Unit,
    onDraftSelected: (Long) -> Unit,
    onDraftDeleted: (Long) -> Unit,
    onNewDraft: () -> Unit,
    sessionId: Long? = null,
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
            // 手写顶栏而非 Material3 TopAppBar：与详情页 / 收藏页头部用同一套玻璃底 + 0.5dp 分隔线
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    text = stringResource(R.string.menu_draft_box),
                    color = PikuColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
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
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
            ) {
                // 与搜索页同款搜索框：胶囊形 + 白底 + BasicTextField
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(if (dark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.9f))
                        .border(
                            BorderStroke(
                                0.5.dp,
                                if (dark) Color.White.copy(alpha = 0.3f) else LoginTextSecondaryLight.copy(alpha = 0.15f),
                            ),
                            RoundedCornerShape(50),
                        )
                        .height(42.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = PikuColors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.draft_box_search_hint),
                                color = if (dark) LoginTextSecondaryDark else LoginTextFaintLight,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = PikuColors.textPrimary),
                            cursorBrush = SolidColor(PikuColors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setQuery("") },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (dark) LoginTextSecondaryDark else LoginTextFaintLight,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(12.dp))

                if (visible.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(
                                if (query.isBlank() && filter == DraftFilter.ALL) {
                                    R.string.draft_box_empty
                                } else {
                                    R.string.draft_box_empty_search
                                },
                            ),
                            color = PikuColors.textFaint,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(visible, key = { it.draftId ?: 0L }) { draft ->
                            DraftItem(
                                draft = draft,
                                isEditing = draft.draftId != null && draft.draftId == sessionId,
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
                        deleteTargetId?.let(onDraftDeleted)
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

/** 与投稿页的 SelectableChip 保持同一套选中样式，避免同 App 两套 chip 语言 */
@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) PikuColors.accent
                else PikuColors.textSecondary.copy(alpha = 0.08f),
            )
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

/** 相对时间（"3 分钟前"），与投稿页自动保存提示保持同一口径 */
internal fun draftTime(ts: Long): String =
    DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString()

@Composable
private fun DraftItem(
    draft: PublishDraft,
    isEditing: Boolean,
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
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
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
                    text = stringResource(
                        if (draft.kind == UploadKind.NOVEL) R.string.draft_preview_novel
                        else R.string.draft_preview_image,
                    ),
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = draft.title.ifBlank {
                        stringResource(
                            if (draft.kind == UploadKind.NOVEL) R.string.draft_unnamed_novel
                            else R.string.draft_unnamed_images,
                        )
                    },
                    color = PikuColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isEditing) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.draft_box_editing),
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
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
            val timeTs = draft.savedAt ?: draft.draftId
            val kindLabel = if (draft.kind == UploadKind.NOVEL) {
                stringResource(R.string.draft_novel_stat, draft.body.length)
            } else {
                stringResource(R.string.draft_type_images, draft.imageFiles.size)
            }
            Text(
                text = (timeTs?.let { draftTime(it) } ?: "") + " · " + kindLabel,
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        // 用文字而不是图标：复制/删除做成图标时表意很弱（复制尤其容易被误认成关闭），文字更直接
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.draft_box_duplicate),
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDuplicate)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                text = stringResource(R.string.draft_box_delete),
                color = if (dark) ErrorRedDark else PikuColors.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
