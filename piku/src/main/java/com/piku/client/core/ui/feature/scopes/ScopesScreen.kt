package com.piku.client.core.ui.feature.scopes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.piku.client.R
import com.piku.client.core.source.MAX_SCOPES
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceScopeKind
import com.piku.client.core.source.SourceScopeSuggestion
import com.piku.client.core.ui.common.LoaderDots
import com.piku.client.core.ui.common.PikuBackButton
import com.piku.client.core.ui.common.formatCount
import com.piku.client.core.ui.theme.GlassHeaderTintDark
import com.piku.client.core.ui.theme.GlassHeaderTintLight
import com.piku.client.core.ui.theme.HomeBgBottomDark
import com.piku.client.core.ui.theme.HomeBgBottomLight
import com.piku.client.core.ui.theme.HomeBgTopDark
import com.piku.client.core.ui.theme.HomeBgTopLight
import com.piku.client.core.ui.theme.LocalDarkTheme
import com.piku.client.core.ui.theme.PikuColors

/** 搜索条高度：整条都是输入区，点哪都能进输入 */
private val SEARCH_PILL_HEIGHT = 42.dp

@Composable
fun ScopesScreen(
    onBack: () -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: ScopesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            ScopesTopBar(
                count = state.scopes.size,
                onBack = onBack,
                dark = dark,
            )
            ScopeSearchPill(
                query = state.query,
                kindLabel = kindLabel(state),
                dark = dark,
                onQueryChange = viewModel::setQuery,
                onClear = viewModel::clearQuery,
            )
            if (state.kinds.size > 1) {
                KindChips(
                    kinds = state.kinds,
                    selected = state.kind,
                    onSelect = viewModel::selectKind,
                )
            }
            when {
                state.searchMode -> SearchResults(
                    state = state,
                    dark = dark,
                    onAdd = viewModel::add,
                    onRetry = viewModel::retry,
                )

                else -> ManageContent(
                    state = state,
                    dark = dark,
                    onPick = viewModel::setActive,
                    onRemove = viewModel::remove,
                    onAdd = viewModel::add,
                )
            }
        }
    }
}

/** 当前维度的显示名；维度还没加载出来时给空串，占位退化成"搜索" */
@Composable
private fun kindLabel(state: ScopesUiState): String =
    state.kinds.firstOrNull { it.id == state.kind }?.let { stringResource(it.labelRes) }.orEmpty()

@Composable
private fun ScopesTopBar(
    count: Int,
    onBack: () -> Unit,
    dark: Boolean,
) {
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
                text = stringResource(R.string.ao3_scope_title),
                color = PikuColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (count > 0) {
                Text(
                    text = stringResource(R.string.ao3_scope_fold_count, count).trimStart('·', ' '),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ScopeSearchPill(
    query: String,
    /** 当前维度名，占位文案按它走：搜的到底是同人还是配对/标签，一眼看出来 */
    kindLabel: String,
    dark: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(if (dark) Color(0x14FFFFFF) else PikuColors.surfaceMuted),
    ) {
        // 输入框铺满整条：点图标、边距任何一处都能进输入，占位文字与光标也在同一条基线上
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = PikuColors.textPrimary),
            cursorBrush = SolidColor(PikuColors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(SEARCH_PILL_HEIGHT)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 6.dp),
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
                                text = stringResource(R.string.ao3_scope_search_kind, kindLabel),
                                color = PikuColors.textFaint,
                                fontSize = 14.sp,
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onClear),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.collection_search_clear),
                                tint = PikuColors.textFaint,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

/** 维度片选：同人 / 配对 / 标签 */
@Composable
private fun KindChips(
    kinds: List<SourceScopeKind>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
    ) {
        kinds.forEach { item ->
            val isSelected = item.id == selected
            Text(
                text = stringResource(item.labelRes),
                color = if (isSelected) PikuColors.surface else PikuColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) PikuColors.controlAccent else PikuColors.surfaceMuted)
                    .clickable { onSelect(item.id) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 管理态：我的订阅 + 当前维度的热门榜。
 * 不输入也有东西可点——热门榜就是发现入口，换维度（同人/配对/标签）换的是这一块
 */
@Composable
private fun ManageContent(
    state: ScopesUiState,
    dark: Boolean,
    onPick: (SourceScope) -> Unit,
    onRemove: (SourceScope) -> Unit,
    onAdd: (SourceScope) -> Unit,
) {
    val popular = state.popularCandidates
    val listState = rememberLazyListState()
    // 换维度换的是整张榜：停在上一张榜的滚动位置会看到新榜的中段
    LaunchedEffect(state.kind) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.scopes.isEmpty()) {
            item(key = "empty") { EmptyHint() }
        } else {
            item(key = "added-label") {
                SectionLabel(
                    title = stringResource(R.string.ao3_scope_added),
                    hint = stringResource(R.string.ao3_scope_row_hint),
                )
            }
            items(state.scopes, key = { it.id }) { scope ->
                SubscribedRow(
                    scope = scope,
                    kinds = state.kinds,
                    active = scope.id == state.active?.id,
                    onPick = { onPick(scope) },
                    onRemove = { onRemove(scope) },
                )
            }
        }
        if (!state.canAdd) {
            item(key = "limit-notice") {
                Text(
                    text = stringResource(R.string.ao3_scope_limit_reached, MAX_SCOPES),
                    color = PikuColors.textFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        if (state.popularLoading || popular.isNotEmpty()) {
            item(key = "popular-label") {
                SectionLabel(
                    title = stringResource(R.string.ao3_scope_popular, kindLabel(state)),
                    hint = null,
                )
            }
            if (state.popularLoading) {
                item(key = "popular-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) { LoaderDots(dark = dark) }
                }
            } else {
                items(popular, key = { it.name }) { item ->
                    SuggestionRow(
                        item = item,
                        enabled = state.canAdd,
                        onAdd = { onAdd(SourceScope(state.kind, item.name)) },
                    )
                }
            }
        }
    }
}

/** 段标题：左标题 + 右提示（提示可为空） */
@Composable
private fun SectionLabel(title: String, hint: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Text(
            text = title,
            color = PikuColors.textSecondary,
            fontSize = 12.sp,
        )
        if (hint != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = hint,
                color = PikuColors.textFaint,
                fontSize = 11.sp,
            )
        }
    }
}

/** 一个圈层都没有时的引导：图标 + 一行说明，正下方就是热门榜 */
@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(PikuColors.controlAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalOffer,
                contentDescription = null,
                tint = PikuColors.controlAccent,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ao3_scope_hint),
            color = PikuColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun SubscribedRow(
    scope: SourceScope,
    kinds: List<SourceScopeKind>,
    active: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onPick)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = scope.name,
                color = if (active) PikuColors.accent else PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            kinds.firstOrNull { it.id == scope.kind }?.let { kind ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(kind.labelRes),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        if (active) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PikuColors.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.ao3_scope_remove),
                tint = PikuColors.textFaint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: ScopesUiState,
    dark: Boolean,
    onAdd: (SourceScope) -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            state.searching && state.candidates.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center,
            ) { LoaderDots(dark = dark) }

            state.failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.ao3_scope_search_failed),
                    color = PikuColors.textFaint,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                RetryChip(onRetry)
            }

            state.searched && state.candidates.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.ao3_scope_nothing_found),
                    color = PikuColors.textFaint,
                    fontSize = 13.sp,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            ) {
                if (!state.canAdd) {
                    item(key = "limit-notice") {
                        Text(
                            text = stringResource(R.string.ao3_scope_limit_reached, MAX_SCOPES),
                            color = PikuColors.textFaint,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(state.candidates, key = { it.name }) { item ->
                    SuggestionRow(
                        item = item,
                        enabled = state.canAdd,
                        onAdd = { onAdd(SourceScope(state.kind, item.name)) },
                    )
                }
            }
        }
    }
}

/** 一条候选：整行可点即订阅，右侧作品数与加号（订阅满了置灰） */
@Composable
private fun SuggestionRow(
    item: SourceScopeSuggestion,
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onAdd)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = item.name,
            color = if (enabled) PikuColors.textPrimary else PikuColors.textFaint,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 作品数：同名圈层（"Harry Potter" 既能是同人也能是别的）靠它区分
        item.count?.let { count ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatCount(count),
                color = PikuColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        if (enabled) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.ao3_scope_add),
                tint = PikuColors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RetryChip(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.common_retry),
        color = PikuColors.controlAccent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PikuColors.controlAccent.copy(alpha = 0.12f))
            .clickable(onClick = onRetry)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

