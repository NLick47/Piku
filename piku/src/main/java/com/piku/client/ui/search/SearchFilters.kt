package com.piku.client.ui.search

/** 检索筛选：结果页摘要 chips 行 + 底部筛选面板（声明照渲染） */


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.key
import com.piku.client.domain.source.FILTER_TOGGLE_ON
import com.piku.client.domain.source.SearchFilterGroupSpec
import com.piku.client.domain.source.SearchFilterToggleSpec
import com.piku.client.ui.common.PikuBottomSheet
import com.piku.client.ui.common.PikuSheetHandle
import com.piku.client.ui.common.PikuSheetTitle
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.PikuColors

// ---------------- 检索筛选：chips 行 + 底部面板 ----------------

/** 已生效且非默认的筛选数量，作为"筛选"入口的角标 */
private fun nonDefaultFilterCount(
    groups: List<SearchFilterGroupSpec>,
    toggles: List<SearchFilterToggleSpec>,
    selected: Map<String, String>,
): Int {
    var count = 0
    groups.forEach { group ->
        val defaultId = group.options.firstOrNull { it.default }?.id
        if (defaultId != null && selected[group.id] != defaultId) count++
    }
    toggles.forEach { toggle ->
        if (selected[toggle.id] == FILTER_TOGGLE_ON) count++
    }
    return count
}

/**
 * 结果页上方的换源入口 + 筛选摘要：源 chip 常驻居首（页面级模式项，无 ✕ 不可移除），
 * 后面是已生效条件高亮可单独移除的 chips，末尾是带角标的"筛选"入口。
 * [showFilters] 为 false 时整行只剩源 chip（poipiku 无筛选声明，或非作品 tab）。
 */
@Composable
internal fun SearchFilterBar(
    sourceLabel: String,
    showFilters: Boolean,
    groups: List<SearchFilterGroupSpec>,
    toggles: List<SearchFilterToggleSpec>,
    selected: Map<String, String>,
    onSourceClick: () -> Unit,
    onResetGroup: (String) -> Unit,
    onResetToggle: (String) -> Unit,
    onOpenSheet: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "source") {
            SourceChip(label = sourceLabel, onClick = onSourceClick)
        }
        if (showFilters) {
            groups.forEach { group ->
                val current = selected[group.id]
                val defaultId = group.options.firstOrNull { it.default }?.id
                if (current != null && current != defaultId) {
                    val labelRes = group.options.firstOrNull { it.id == current }?.labelRes
                    if (labelRes != null) {
                        item(key = group.id) {
                            ActiveFilterChip(
                                text = stringResource(labelRes),
                                onRemove = { onResetGroup(group.id) },
                                onOpen = onOpenSheet,
                            )
                        }
                    }
                }
            }
            toggles.forEach { toggle ->
                if (selected[toggle.id] == FILTER_TOGGLE_ON) {
                    item(key = toggle.id) {
                        ActiveFilterChip(
                            text = stringResource(toggle.labelRes),
                            onRemove = { onResetToggle(toggle.id) },
                            onOpen = onOpenSheet,
                        )
                    }
                }
            }
            item(key = "filter_open") {
                FilterOpenChip(
                    count = nonDefaultFilterCount(groups, toggles, selected),
                    onOpen = onOpenSheet,
                )
            }
        }
    }
}

/** 换源入口：源名 + ›，点开换源面板；文案与首页换源面板、结果卡角标同一套 */
@Composable
private fun SourceChip(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (LocalDarkTheme.current) Color(0x40FFFFFF) else Color(0xE6FFFFFF))
            .border(BorderStroke(0.5.dp, PikuColors.border), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PikuColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = PikuColors.textSecondary,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun ActiveFilterChip(
    text: String,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PikuColors.accent)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.search_clear),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun FilterOpenChip(
    count: Int,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (LocalDarkTheme.current) Color(0x40FFFFFF) else Color(0xE6FFFFFF))
            .border(BorderStroke(0.5.dp, PikuColors.border), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = null,
            tint = PikuColors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = stringResource(R.string.search_filter),
            color = PikuColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(PikuColors.accent),
                contentAlignment = Alignment.Center,
            ) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SearchFilterSheet(
    groups: List<SearchFilterGroupSpec>,
    toggles: List<SearchFilterToggleSpec>,
    selected: Map<String, String>,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    var draft by remember { mutableStateOf(selected) }
    PikuBottomSheet(onDismissRequest = onDismiss, dark = dark, scrollable = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PikuSheetTitle(text = stringResource(R.string.search_filter))
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.search_filter_reset),
                color = PikuColors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        draft = buildMap {
                            groups.forEach { group ->
                                group.options.firstOrNull { it.default }?.let { put(group.id, it.id) }
                            }
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        PikuSheetHandle()
        Spacer(Modifier.height(4.dp))
        groups.forEach { group ->
            Text(
                text = stringResource(group.labelRes),
                color = PikuColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.options.forEach { option ->
                    val active = draft[group.id] == option.id
                    Text(
                        text = stringResource(option.labelRes),
                        color = if (active) {
                            if (dark) LoginBackgroundDark else Color.White
                        } else {
                            PikuColors.textPrimary
                        },
                        fontSize = 12.5.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(17.dp))
                            .background(
                                if (active) PikuColors.accent
                                else if (dark) Color(0x40FFFFFF) else Color(0xE6FFFFFF),
                            )
                            .border(
                                BorderStroke(0.5.dp, if (active) PikuColors.accent else PikuColors.border),
                                RoundedCornerShape(17.dp),
                            )
                            .clickable { draft = draft + (group.id to option.id) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        toggles.forEach { toggle ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(toggle.labelRes),
                    color = PikuColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft[toggle.id] == FILTER_TOGGLE_ON,
                    onCheckedChange = { checked ->
                        draft = if (checked) draft + (toggle.id to FILTER_TOGGLE_ON) else draft - toggle.id
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = PikuColors.accent),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(23.dp))
                .background(PikuColors.accent)
                .clickable { onApply(draft) }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.search_filter_apply),
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
