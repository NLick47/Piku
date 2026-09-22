package com.piku.client.ui.decoration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.data.local.DecorationItem
import com.piku.client.data.repository.DecorationRepository
import com.piku.client.ui.common.FeedbackChannel
import com.piku.client.ui.common.FeedbackHost
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 管理页网格单元：白名单条目 + 已解码的缩略图文件路径 */
data class DecorationUiItem(
    val item: DecorationItem,
    val imagePath: String,
)

@HiltViewModel
class DecorationManageViewModel @Inject constructor(
    private val decorationRepository: DecorationRepository,
) : ViewModel() {

    val feedback = FeedbackChannel()

    val items: StateFlow<List<DecorationUiItem>> = decorationRepository.observeAll()
        .map { list ->
            list.map { DecorationUiItem(it, decorationRepository.imageFile(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(workId: Long) {
        viewModelScope.launch {
            decorationRepository.remove(workId)
            feedback.show(R.string.decoration_removed)
        }
    }
}

/**
 * 装饰管理页：白名单缩略图平铺预览，逐个移除。
 * 人工复审是白名单方案的第二道防线——缩略图阶段成人内容已可见。
 */
@Composable
fun DecorationManageScreen(
    onBack: () -> Unit,
    viewModel: DecorationManageViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRemove by remember { mutableStateOf<DecorationUiItem?>(null) }

    FeedbackHost(channel = viewModel.feedback, snackbarHostState = snackbarHostState)

    Box(Modifier.fillMaxSize()) {
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
                        text = stringResource(R.string.decoration_manage_title),
                        color = PikuColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.decoration_manage_summary, items.size),
                        color = PikuColors.textFaint,
                        fontSize = 11.sp,
                    )
                }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.decoration_manage_empty),
                        color = PikuColors.textSecondary,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.item.workId }) { entry ->
                        DecorationCell(
                            entry = entry,
                            onRemove = { pendingRemove = entry },
                        )
                    }
                }
            }
        }

        // 反馈浮层的宿主：没有它 showSnackbar 会挂起等一个不存在的 host，提示永远不出现
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }

    pendingRemove?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = PikuColors.surface,
            title = {
                Text(
                    text = stringResource(R.string.decoration_remove_title),
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.decoration_remove_text,
                        entry.item.title.ifBlank { entry.item.workId.toString() },
                    ),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(entry.item.workId)
                    pendingRemove = null
                }) {
                    Text(
                        text = stringResource(R.string.decoration_remove_confirm),
                        color = PikuColors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(
                        text = stringResource(R.string.decoration_cancel),
                        color = PikuColors.textSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun DecorationCell(entry: DecorationUiItem, onRemove: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(PikuColors.border.copy(alpha = 0.25f)),
    ) {
        val path = entry.imagePath
        if (File(path).exists()) {
            AsyncImage(
                model = File(path),
                contentDescription = entry.item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.decoration_image_missing),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.decoration_remove_confirm),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        // 底部渐变上叠标题，装饰也要能认出是哪张画
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = entry.item.title.ifBlank { entry.item.authorName },
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
