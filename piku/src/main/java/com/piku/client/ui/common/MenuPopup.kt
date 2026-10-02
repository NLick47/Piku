package com.piku.client.ui.common

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.piku.client.ui.theme.MenuPopupBgDark
import com.piku.client.ui.theme.MenuPopupBgLight
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.ShadowSpot
import com.piku.client.ui.theme.ShadowSpotHeavy
import com.piku.client.ui.theme.SoftBorderDark
import com.piku.client.ui.theme.SoftBorderLight

data class MenuPopupItem(
    val label: String,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun MenuPopup(
    groups: List<List<MenuPopupItem>>,
    dark: Boolean,
    anchorHeightPx: Int,
    onDismiss: () -> Unit,
    width: Dp = 150.dp,
    belowAnchor: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val offset = if (belowAnchor) {
        IntOffset(0, anchorHeightPx + gap)
    } else {
        IntOffset(0, -panelHeightPx - gap)
    }
    Popup(
        alignment = Alignment.TopEnd,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .onSizeChanged { panelHeightPx = it.height }
                .width(width)
                .shadow(14.dp, shape, ambientColor = ShadowSpot, spotColor = ShadowSpotHeavy)
                .clip(shape)
                .background(if (dark) MenuPopupBgDark else MenuPopupBgLight)
                .border(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight, shape)
                .padding(vertical = 6.dp),
        ) {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = if (dark) SoftBorderDark else SoftBorderLight,
                    )
                }
                group.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = item.enabled, onClick = item.onClick)
                            .alpha(if (item.enabled) 1f else 0.38f)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = PikuColors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = item.label,
                            color = if (item.selected) PikuColors.accent else PikuColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (item.selected) {
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
}
