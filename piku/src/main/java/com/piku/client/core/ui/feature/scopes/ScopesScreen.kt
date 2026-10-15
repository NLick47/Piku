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

                state.scopes.isEmpty() -> EmptyScopes()

                else -> SubscribedList(
                    state = state,
                    dark = dark,
                    onPick = viewModel::setActive,
                    onRemove = viewModel::remove,
                )
            }
        }
    }
}

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
    dark: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(if (dark) Color(0x14FFFFFF) else PikuColors.surfaceMuted)
            // 点空白处也聚焦输入框，而不是毫无反应
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.ao3_scope_search_hint),
                    color = PikuColors.textFaint,
                    fontSize = 14.sp,
                )
            }
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
                    .focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
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

/** 订阅管理：点一行设为当前（打勾那行），右侧 × 退订 */
@Composable
private fun SubscribedList(
    state: ScopesUiState,
    dark: Boolean,
    onPick: (SourceScope) -> Unit,
    onRemove: (SourceScope) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "added-label") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ao3_scope_added),
                    color = PikuColors.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.ao3_scope_row_hint),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                )
            }
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
                    ResultRow(
                        item = item,
                        enabled = state.canAdd,
                        onAdd = { onAdd(SourceScope(state.kind, item.name)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
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

@Composable
private fun EmptyScopes() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PikuColors.controlAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalOffer,
                contentDescription = null,
                tint = PikuColors.controlAccent,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.ao3_scope_title),
            color = PikuColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ao3_scope_hint),
            color = PikuColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 44.dp),
        )
    }
}
