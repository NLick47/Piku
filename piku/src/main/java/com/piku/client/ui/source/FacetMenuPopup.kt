package com.piku.client.ui.source

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.piku.client.domain.source.SourceFacet
import com.piku.client.ui.theme.MenuPopupBgDark
import com.piku.client.ui.theme.MenuPopupBgLight
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.ShadowSpot
import com.piku.client.ui.theme.ShadowSpotHeavy
import com.piku.client.ui.theme.SoftBorderDark
import com.piku.client.ui.theme.SoftBorderLight

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
    val shape = RoundedCornerShape(16.dp)
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, anchorHeightPx + gap),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(150.dp)
                .shadow(14.dp, shape, ambientColor = ShadowSpot, spotColor = ShadowSpotHeavy)
                .clip(shape)
                .background(if (dark) MenuPopupBgDark else MenuPopupBgLight)
                .border(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight, shape)
                .padding(vertical = 6.dp),
        ) {
            options.forEach { option ->
                val selected = option.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(option.id) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        color = if (selected) PikuColors.accent else PikuColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (selected) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = PikuColors.accent,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}
