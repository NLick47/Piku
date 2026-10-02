package com.piku.client.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.data.remote.translation.ModelEntry
import com.piku.client.data.remote.translation.Role
import com.piku.client.ui.common.MenuPopup
import com.piku.client.ui.common.MenuPopupItem
import com.piku.client.ui.theme.PikuColors

internal fun retranslatePickableModels(models: List<ModelEntry>): List<ModelEntry> =
    models.filter { entry ->
        entry.available && !entry.apiKey.isNullOrBlank() && Role.TEXT in entry.roles
    }

@Composable
internal fun ModelPickerRow(
    entry: ModelEntry,
    onClick: () -> Unit,
) {
    val primary = PikuColors.textPrimary
    val hint = PikuColors.textSecondary
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
    }
}

/** 更多操作：poipiku 详情底栏 ⋮ 的菜单，壳走 ui/common MenuPopup（贴按钮上方、带图标） */
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
    /**
     * 屏蔽/解除屏蔽作者。null 表示不显示该入口（未登录、屏蔽状态未知等）；
     * [blocked] 决定文案是「屏蔽作者」还是「解除屏蔽」。
     */
    onToggleBlock: (() -> Unit)? = null,
    blocked: Boolean = false,
) {
    MenuPopup(
        groups = buildList {
            add(
                buildList {
                    add(
                        MenuPopupItem(
                            label = stringResource(R.string.detail_copy_link),
                            icon = Icons.Outlined.ContentCopy,
                            onClick = onCopyLink,
                        ),
                    )
                    add(
                        MenuPopupItem(
                            label = stringResource(R.string.detail_copy_description),
                            icon = Icons.AutoMirrored.Outlined.Article,
                            onClick = onCopyDescription,
                        ),
                    )
                    add(
                        MenuPopupItem(
                            label = stringResource(R.string.detail_open_browser),
                            icon = Icons.Outlined.OpenInBrowser,
                            onClick = onOpenBrowser,
                        ),
                    )
                    if (onOpenModelPicker != null) {
                        add(
                            MenuPopupItem(
                                label = stringResource(R.string.detail_menu_retry_with_model),
                                icon = Icons.Outlined.Translate,
                                onClick = onOpenModelPicker,
                            ),
                        )
                    }
                },
            )
            if (onToggleBlock != null) {
                // 破坏性操作单独成组，借组间分隔线与上方的复制/打开项隔开，避免误触
                add(
                    listOf(
                        MenuPopupItem(
                            label = stringResource(
                                if (blocked) R.string.detail_unblock else R.string.detail_block,
                            ),
                            icon = Icons.Outlined.Block,
                            onClick = onToggleBlock,
                        ),
                    ),
                )
            }
        },
        dark = dark,
        anchorHeightPx = 0,
        onDismiss = onDismiss,
        width = 200.dp,
        belowAnchor = false,
    )
}
