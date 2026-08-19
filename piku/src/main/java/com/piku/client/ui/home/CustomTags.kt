package com.piku.client.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.GlassCardBgDark
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight
import com.piku.client.ui.theme.SwitchUncheckedTrackDark
import com.piku.client.ui.theme.SwitchUncheckedTrackLight

/**
 * 自定义标签区块：内联添加输入框 + 标签 chips（点击筛选、× 删除）。
 * 在右侧抽屉与标签筛选面板中复用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomTagSection(
    tags: List<String>,
    currentTag: String?,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    var input by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.my_tags_add_hint),
                        color = faint,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = primary),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentDark.copy(alpha = 0.8f),
                    unfocusedBorderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                    focusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
                    unfocusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
                    cursorColor = AccentDark,
                    focusedTextColor = primary,
                    unfocusedTextColor = primary,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            val addEnabled = input.trim().removePrefix("#").trim().isNotEmpty()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
.background(
                        if (addEnabled) AccentDark
                        else if (dark) SwitchUncheckedTrackDark else SwitchUncheckedTrackLight,
                    )
                    .clickable(enabled = addEnabled, onClick = {
                        val tag = input.trim().removePrefix("#").trim()
                        if (tag.isNotEmpty()) {
                            onAdd(tag)
                            input = ""
                        }
                    }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.my_tags_add),
                    tint = if (addEnabled) Color.White else faint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.my_tags_empty),
                color = faint,
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    CustomTagChip(
                        tag = tag,
                        active = tag == currentTag,
                        onSelect = { onSelect(tag) },
                        onRemove = { onRemove(tag) },
                        dark = dark,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomTagChip(
    tag: String,
    active: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val chipBg = when {
        active -> if (dark) LoginTextPrimaryDark else AccentDark.copy(alpha = 0.10f)
        else -> if (dark) GlassCardBgDark else Color.White
    }
    val chipBorder = when {
        active -> if (dark) LoginTextPrimaryDark else AccentDark.copy(alpha = 0.30f)
        else -> if (dark) PillBorderDark else PillBorderLight
    }
    val textColor = when {
        active -> if (dark) LoginBackgroundDark else AccentDark
        else -> if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
    }
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    val delTint = when {
        active -> if (dark) LoginBackgroundDark else AccentDark
        else -> faint
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(chipBg)
            .border(BorderStroke(0.5.dp, chipBorder), shape)
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$tag",
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 132.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.my_tags_delete),
                tint = delTint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
