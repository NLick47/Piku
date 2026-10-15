package com.piku.client.core.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.source.MAX_SCOPES
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceScopeKind
import com.piku.client.core.source.SourceScopeSuggestion
import com.piku.client.core.source.canAddScope
import com.piku.client.core.ui.common.LoaderDots
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuSheetHandle
import com.piku.client.core.ui.common.PikuSheetTitle
import com.piku.client.core.ui.common.formatCount
import com.piku.client.core.ui.theme.PikuColors
import kotlinx.coroutines.delay

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SourceScopeSheet(
    kinds: List<SourceScopeKind>,
    scopes: List<SourceScope>,
    dark: Boolean,
    labels: Map<String, String> = emptyMap(),
    maxScopes: Int = MAX_SCOPES,    suggest: suspend (kind: String, term: String) -> Result<List<SourceScopeSuggestion>>,
    onAdd: (SourceScope) -> Unit,
    onRemove: (SourceScope) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(kinds.firstOrNull()?.id.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SourceScopeSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(kind, query, retryTick) {
        val term = query.trim()
        if (term.isEmpty()) {
            results = emptyList()
            searched = false
            searching = false
            failed = false
            return@LaunchedEffect
        }
        searching = true
        failed = false
        delay(300)
        suggest(kind, term)
            .onSuccess { found ->
                results = found
                searched = true
            }
            .onFailure {
                results = emptyList()
                failed = true
            }
        searching = false
    }
    // 已订阅的从结果里滤掉，且按订阅状态实时滤：点过一条它会立刻消失，
    // 不会留一条还能再点一次的旧行（选择器加了圈层不关闭，露出旧行很显眼）
    val candidates = remember(results, scopes, kind) {
        results.filterNot { item -> scopes.any { it.kind == kind && it.name == item.name } }
    }
    // 软上限：满了之后结果只读——移除照常，添加入口停用并说明缘由
    val canAdd = canAddScope(scopes, maxScopes)
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            PikuSheetHandle()
            PikuSheetTitle(stringResource(R.string.ao3_scope_picker_title))
        }

        if (scopes.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.ao3_scope_added),
                color = PikuColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(scopes, key = { it.id }) { scope ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PikuColors.controlAccent.copy(alpha = 0.12f))
                            .clickable { onRemove(scope) }
                            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Text(
                            text = labels[scope.id] ?: scope.name,
                            color = PikuColors.controlAccent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.ao3_scope_remove),
                            tint = PikuColors.controlAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // 维度片选：同人/配对/标签
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            kinds.forEach { item ->
                val selected = item.id == kind
                Text(
                    text = stringResource(item.labelRes),
                    color = if (selected) PikuColors.surface else PikuColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) PikuColors.controlAccent else PikuColors.surfaceMuted)
                        .clickable { kind = item.id }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PikuColors.surfaceMuted)
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = PikuColors.textPrimary),
                    cursorBrush = SolidColor(PikuColors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = PikuColors.textFaint,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { query = "" },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 380.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                searching && candidates.isEmpty() -> LoaderDots(dark = dark)
                failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.ao3_scope_search_failed),
                        color = PikuColors.textFaint,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.common_retry),
                        color = PikuColors.controlAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PikuColors.controlAccent.copy(alpha = 0.12f))
                            .clickable { retryTick++ }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                searched && candidates.isEmpty() -> Text(
                    text = stringResource(R.string.ao3_scope_nothing_found),
                    color = PikuColors.textFaint,
                    fontSize = 13.sp,
                )
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    if (!canAdd) {
                        item(key = "limit-notice") {
                            Text(
                                text = stringResource(R.string.ao3_scope_limit_reached, maxScopes),
                                color = PikuColors.textFaint,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(candidates, key = { it.name }) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { if (!canAdd) it else it.clickable { onAdd(SourceScope(kind, item.name)) } }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = item.name,
                                color = if (!canAdd) PikuColors.textFaint else PikuColors.textPrimary,
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
                            if (canAdd) {
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
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
