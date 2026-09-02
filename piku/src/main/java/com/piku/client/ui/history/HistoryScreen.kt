package com.piku.client.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import com.piku.client.ui.theme.LocalDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.domain.model.HistoryTimeRange
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onWorkClick: (Work) -> Unit,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val gridState = rememberLazyStaggeredGridState()
    var showClearConfirm by remember { mutableStateOf(false) }

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
            HistoryTopBar(
                onBack = onBack,
                onClear = { showClearConfirm = true },
                clearEnabled = state.works.isNotEmpty(),
                dark = dark,
            )
            HistoryRangeBar(
                selected = state.selectedRange,
                onSelect = viewModel::selectRange,
                dark = dark,
            )
            when {
                !state.loaded -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
                state.works.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HistoryEmptyState(
                            dark = dark,
                            filtered = state.selectedRange != HistoryTimeRange.ALL,
                        )
                    }
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
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp,
                    ) {
                        items(state.works, key = { it.id }) { work ->
                            WorkCard(
                                work = work,
                                isFavorite = work.id in state.favoriteIds,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onClick = onWorkClick,
                                dark = dark,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = PikuColors.surface,
            title = {
                Text(
                    text = stringResource(R.string.history_clear_confirm_title),
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.history_clear_confirm_message),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clear()
                }) {
                    Text(
                        text = stringResource(R.string.history_clear),
                        color = PikuColors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(
                        text = stringResource(R.string.search_cancel),
                        color = PikuColors.textSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun HistoryTopBar(
    onBack: () -> Unit,
    onClear: () -> Unit,
    clearEnabled: Boolean,
    dark: Boolean,
) {
    val primary = PikuColors.textPrimary
    val faint = PikuColors.textFaint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = primary,
            )
        }
        Text(
            text = stringResource(R.string.history_title),
            color = primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (clearEnabled) {
            TextButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = faint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.history_clear),
                    color = faint,
                    fontSize = 13.sp,
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun HistoryRangeBar(
    selected: HistoryTimeRange,
    onSelect: (HistoryTimeRange) -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryTimeRange.entries.forEach { range ->
            HistoryRangeChip(
                text = stringResource(range.labelRes()),
                active = range == selected,
                onClick = { onSelect(range) },
                dark = dark,
            )
        }
    }
}

@Composable
private fun HistoryRangeChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    val container = when {
        active -> if (dark) LoginTextPrimaryDark.copy(alpha = 0.14f) else AccentDark
        else -> if (dark) GlassCardBgDark else Color.White
    }
    val borderColor = when {
        active -> PikuColors.accent
        else -> PikuColors.border
    }
    val contentColor = when {
        active -> if (dark) LoginTextPrimaryDark else Color.White
        dark -> LoginTextSecondaryDark
        else -> Color(0xFF5A5A5A)
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(container)
            .border(BorderStroke(0.5.dp, borderColor), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun HistoryEmptyState(dark: Boolean, filtered: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (filtered) R.string.history_empty_range else R.string.history_empty,
            ),
            color = PikuColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
