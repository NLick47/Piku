package com.piku.client.ui.myposts

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.FeedbackHost
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.SkeletonBlock
import com.piku.client.ui.common.WorkPrivateBadge
import com.piku.client.ui.common.localizedCategoryName
import com.piku.client.ui.common.rememberSkeletonPulse
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
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight

@Composable
fun MyPostsScreen(
    onBack: () -> Unit,
    onWorkClick: (Work) -> Unit,
    /** 编辑该作品：进入发布页的编辑模式 */
    onEditClick: (Work) -> Unit = {},
    /** 删除成功后回调：AppNavHost 写返回标记，通知用户主页刷新列表 */
    onDeleted: () -> Unit = {},
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: MyPostsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 删除成功 → 立即写返回标记通知个人主页刷新。独立于 snackbar：收集体不挂起，
    // 用户删完立刻回退也不会漏掉这次通知（旧写法把通知放在 showSnackbar 之后，会被取消）。
    LaunchedEffect(Unit) {
        viewModel.workDeleted.collect { onDeleted() }
    }
    FeedbackHost(channel = viewModel.feedback, snackbarHostState = snackbarHostState)

    var deleteTarget by remember { mutableStateOf<Work?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                        else listOf(HomeBgTopLight, HomeBgBottomLight),
                    ),
                ),
        ) {
            // 顶栏：与全站规范一致（玻璃底 + 0.5dp 分隔线）
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
                    text = stringResource(R.string.my_posts_title),
                    color = PikuColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                // 委托属性无法 smart cast，先捕获局部变量
                val firstError = state.errorRes
                when {
                    state.loading && state.works.isEmpty() -> MyPostsSkeleton(dark = dark)
                    firstError != null && state.works.isEmpty() -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(firstError),
                            color = PikuColors.textFaint,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.publish_failed_retry), color = PikuColors.accent)
                        }
                    }
                    state.works.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.my_posts_empty),
                            color = PikuColors.textFaint,
                            fontSize = 13.sp,
                        )
                    }
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    ) {
                        items(state.works, key = { it.id }) { work ->
                            MyPostRow(
                                work = work,
                                deleting = state.deletingId == work.id,
                                onClick = { onWorkClick(work) },
                                onEdit = { onEditClick(work) },
                                onDelete = { deleteTarget = work },
                                dark = dark,
                            )
                        }
                        item(key = "footer") {
                            val moreError = state.loadMoreErrorRes
                            when {
                                // 加载更多静默进行：不显示指示器，避免列表下方残留加载动画；
                                // footer item 仍保留——它是触发 loadMore 的哨兵
                                moreError != null -> TextButton(
                                    onClick = viewModel::retryLoadMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        stringResource(moreError),
                                        color = PikuColors.textSecondary,
                                    )
                                }
                                !state.endReached -> LaunchedEffect(state.works.size) {
                                    viewModel.loadMore()
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.my_posts_delete_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.my_posts_delete_confirm_body,
                            target.title.ifBlank {
                                localizedCategoryName(target.categoryCd, target.categoryName)
                            },
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteWork(target)
                        deleteTarget = null
                    }) {
                        Text(stringResource(R.string.draft_box_delete), color = PikuColors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.publish_cancel), color = PikuColors.textSecondary)
                    }
                },
            )
        }
    }
}

/** 列表行：缩略图 + 标题 + 分类·张数 + 文字「编辑」「删除」（与草稿箱一致：操作用文字不用图标） */
@Composable
private fun MyPostRow(
    work: Work,
    deleting: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (dark) GlassCardBgDark else GlassCardBgLight)
            .border(0.5.dp, if (dark) GlassCardBorderDark else GlassCardBorderLight, RoundedCornerShape(24.dp))
            .clickable(enabled = !deleting, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PikuColors.textSecondary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (work.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = work.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "#${work.id}",
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // 分类命中本地枚举用本地化名（WorkGrid / 详情页同款），否则回退站点原文
            val categoryName = localizedCategoryName(work.categoryCd, work.categoryName)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = work.title.ifBlank { categoryName },
                    color = PikuColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 非公開投稿在列表里要能一眼认出来：它没有公开主页入口，但仍可编辑/删除
                if (work.isPrivate) {
                    Spacer(Modifier.width(6.dp))
                    WorkPrivateBadge()
                }
            }
            Spacer(Modifier.height(2.dp))
            val countText = if (work.imageCount > 0) {
                stringResource(R.string.draft_type_images, work.imageCount)
            } else {
                null
            }
            val meta = listOf(categoryName, countText)
                .filterNotNull()
                .joinToString(" · ")
            Text(
                text = meta,
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.my_posts_edit),
            color = PikuColors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !deleting, onClick = onEdit)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            text = stringResource(R.string.draft_box_delete),
            color = if (deleting) PikuColors.textFaint else PikuColors.error,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !deleting, onClick = onDelete)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** 首屏加载骨架：与 MyPostRow 同构（玻璃卡 + 缩略图 + 两行文字 + 右侧操作位），呼吸脉冲 */
@Composable
private fun MyPostsSkeleton(dark: Boolean) {
    val pulse = rememberSkeletonPulse()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(7) {
            MyPostRowSkeleton(pulse.value, dark = dark)
        }
    }
}

@Composable
private fun MyPostRowSkeleton(pulse: Float, dark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (dark) GlassCardBgDark else GlassCardBgLight)
            .border(0.5.dp, if (dark) GlassCardBorderDark else GlassCardBorderLight, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(pulse, Modifier.size(52.dp), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBlock(pulse, Modifier.fillMaxWidth(0.55f).height(12.dp), shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(pulse, Modifier.fillMaxWidth(0.35f).height(9.dp), shape = RoundedCornerShape(5.dp))
        }
        Spacer(Modifier.width(4.dp))
        SkeletonBlock(pulse, Modifier.size(34.dp, 12.dp), shape = RoundedCornerShape(4.dp))
        Spacer(Modifier.width(10.dp))
        SkeletonBlock(pulse, Modifier.size(34.dp, 12.dp), shape = RoundedCornerShape(4.dp))
    }
}
