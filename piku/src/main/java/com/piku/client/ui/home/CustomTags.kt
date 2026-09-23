package com.piku.client.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.InsetSurfaceDark
import com.piku.client.ui.theme.InsetSurfacePressedDark
import com.piku.client.ui.theme.OnAccentDark
import com.piku.client.ui.theme.PikuColors

/**
 * 自定义标签区块：胶囊输入行 + 标签 chips（点击看投稿、× 删除）。
 * 外层由标签页套一张玻璃卡，这里只管卡内内容。
 */
@Composable
fun CustomTagSection(
    tags: List<String>,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TagAddPill(onAdd = onAdd, dark = dark)
        if (tags.isEmpty()) {
            TagEmptyState(dark = dark)
        } else {
            Spacer(Modifier.size(12.dp))
            TagChipFlow(
                tags = tags,
                onSelect = onSelect,
                onRemove = onRemove,
                dark = dark,
            )
        }
    }
}

/** 添加标签的胶囊输入条：# 前缀 + 圆形加号，与收藏页检索胶囊同族 */
@Composable
private fun TagAddPill(
    onAdd: (String) -> Unit,
    dark: Boolean,
) {
    val focusManager = LocalFocusManager.current
    var input by rememberSaveable { mutableStateOf("") }
    val ready = input.trim().trimStart('#').trim().isNotEmpty()
    val pill = RoundedCornerShape(50)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(pill)
            .background(if (dark) InsetSurfaceDark else PikuColors.surfaceSoft)
            .border(BorderStroke(0.5.dp, PikuColors.border), pill)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            color = PikuColors.textFaint,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (input.isEmpty()) {
                Text(
                    text = stringResource(R.string.my_tags_add_hint),
                    // 行高必须与输入框一致，否则继承 M3 的 24sp 会把文字顶偏
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    color = PikuColors.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = PikuColors.textPrimary,
                ),
                cursorBrush = SolidColor(PikuColors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (ready) {
                            onAdd(input)
                            input = ""
                        }
                        focusManager.clearFocus()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (ready) {
                        PikuColors.accent
                    } else if (dark) {
                        InsetSurfaceDark
                    } else {
                        PikuColors.surfaceMuted
                    },
                )
                .clickable(enabled = ready, onClick = {
                    onAdd(input)
                    input = ""
                    focusManager.clearFocus()
                }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.my_tags_add),
                tint = if (ready) {
                    if (dark) OnAccentDark else Color.White
                } else {
                    PikuColors.textFaint
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChipFlow(
    tags: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    dark: Boolean,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            CustomTagChip(
                tag = tag,
                onSelect = { onSelect(tag) },
                onRemove = { onRemove(tag) },
                dark = dark,
            )
        }
    }
}

@Composable
private fun CustomTagChip(
    tag: String,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    pressed -> if (dark) InsetSurfacePressedDark else PikuColors.surfaceMuted
                    dark -> InsetSurfaceDark
                    else -> PikuColors.surfaceSoft
                },
            )
            .border(BorderStroke(0.5.dp, PikuColors.border), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onSelect,
            )
            .padding(start = 13.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$tag",
            color = PikuColors.textPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 132.dp),
        )
        // 删除热区与 chip 主体分开：24dp 圆形，避免点删除时误进标签详情
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.my_tags_delete),
                tint = PikuColors.textSecondary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** 空态：图标气泡 + 主副文案，一个标签都没有时替代 chips */
@Composable
private fun TagEmptyState(dark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (dark) InsetSurfaceDark else PikuColors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Label,
                contentDescription = null,
                tint = PikuColors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.my_tags_empty_title),
            color = PikuColors.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.my_tags_empty_subtitle),
            color = PikuColors.textFaint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
