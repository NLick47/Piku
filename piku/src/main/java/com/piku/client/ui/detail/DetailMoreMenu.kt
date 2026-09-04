package com.piku.client.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.piku.client.R
import com.piku.client.data.remote.translation.ModelEntry
import com.piku.client.data.remote.translation.Role
import com.piku.client.ui.theme.PikuColors

@Composable
internal fun ModelPickerRow(
    entry: ModelEntry,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val primary = PikuColors.textPrimary
    val hint = PikuColors.textSecondary
    val kindLabel = when {
        Role.NOVEL in entry.roles -> stringResource(R.string.detail_model_kind_novel)
        Role.IMAGE in entry.roles -> stringResource(R.string.detail_model_kind_image)
        else -> stringResource(R.string.detail_model_kind_text)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                color = primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (entry.hint.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = entry.hint, color = hint, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = kindLabel,
            color = PikuColors.controlAccent,
            fontSize = 11.sp,
            modifier = Modifier
                .border(
                    BorderStroke(0.5.dp, PikuColors.border),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 更多操作：锚定在按钮上方的玻璃风格小弹窗，比系统菜单精致、比整页弹层省空间 */
@Composable
internal fun MoreMenuPopup(
    dark: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyDescription: () -> Unit,
    onOpenBrowser: () -> Unit,
    /**
     * 换模型重翻入口。顶栏翻译按钮的长按也能触发，但长按是隐藏手势、用户发现不了，
     * 这里给一个看得见的落点；没有可用模型时传 null，整项不显示。
     */
    onOpenModelPicker: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    var offsetY by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(18.dp)
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .onSizeChanged { size ->
                    offsetY = -size.height - with(density) { 8.dp.roundToPx() }
                }
                .shadow(14.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x55000000))
                .clip(shape)
                .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
                .border(
                    BorderStroke(0.5.dp, if (dark) Color(0x59FFFFFF) else Color(0x59C8C2B8)),
                    shape,
                )
                .padding(vertical = 6.dp),
        ) {
            MoreMenuRow(
                iconVector = Icons.Outlined.ContentCopy,
                label = stringResource(R.string.detail_copy_link),
                dark = dark,
                onClick = onCopyLink,
            )
            MoreMenuRow(
                // 与上面的"复制链接"区分开：同为复制动作，图标必须不同
                iconVector = Icons.AutoMirrored.Outlined.Article,
                label = stringResource(R.string.detail_copy_description),
                dark = dark,
                onClick = onCopyDescription,
            )
            MoreMenuRow(
                iconVector = Icons.Outlined.OpenInBrowser,
                label = stringResource(R.string.detail_open_browser),
                dark = dark,
                onClick = onOpenBrowser,
            )
            if (onOpenModelPicker != null) {
                MoreMenuRow(
                    iconVector = Icons.Outlined.Translate,
                    label = stringResource(R.string.detail_menu_retry_with_model),
                    dark = dark,
                    onClick = onOpenModelPicker,
                )
            }
        }
    }
}

@Composable
private fun MoreMenuRow(
    iconVector: ImageVector,
    label: String,
    dark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = PikuColors.accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = PikuColors.textPrimary,
            fontSize = 13.sp,
        )
    }
}
