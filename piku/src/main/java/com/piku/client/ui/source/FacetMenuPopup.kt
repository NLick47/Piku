package com.piku.client.ui.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.piku.client.domain.source.SourceFacet
import com.piku.client.ui.common.MenuPopup
import com.piku.client.ui.common.MenuPopupItem

/** 首页内容类型筛选的面板：视觉壳在 [MenuPopup]（ui/common），这里只做 facet → 条目的映射 */
@Composable
internal fun FacetMenuPopup(
    options: List<SourceFacet>,
    selectedId: String?,
    dark: Boolean,
    /** 入口（筛选按钮）自身高度 px，面板用它落到入口正下方 */
    anchorHeightPx: Int,
    onSelect: (optionId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPopup(
        groups = listOf(
            options.map { option ->
                MenuPopupItem(
                    label = stringResource(option.labelRes),
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                )
            },
        ),
        dark = dark,
        anchorHeightPx = anchorHeightPx,
        onDismiss = onDismiss,
    )
}
