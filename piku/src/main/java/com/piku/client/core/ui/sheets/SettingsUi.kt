package com.piku.client.core.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.translation.AuthMode
import com.piku.client.core.translation.ManualProvider
import com.piku.client.core.translation.Role
import com.piku.client.core.ui.theme.GlassHeaderTintDark
import com.piku.client.core.ui.theme.GlassHeaderTintLight
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.core.ui.theme.onAccent
import com.piku.client.source.poipiku.ui.home.ProviderTestState

private val CardShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(6.dp)
private val ButtonShape = RoundedCornerShape(10.dp)
private val OptionShape = RoundedCornerShape(8.dp)

@Composable
internal fun SettingsCard(
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (dark) 0.dp else 4.dp,
                shape = CardShape,
                ambientColor = Color(0x14000000),
                spotColor = Color(0x24000000),
            )
            .clip(CardShape)
            .background(if (dark) Color(0xFF262421) else Color.White)
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x26FFFFFF) else PikuColors.border),
                CardShape,
            ),
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = PikuColors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 2.dp, bottom = 9.dp),
    )
}

@Composable
internal fun MiniPill(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    val ink = color ?: PikuColors.textSecondary
    Text(
        text = text,
        color = ink,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = modifier
            .clip(ChipShape)
            .border(
                BorderStroke(0.5.dp, color?.copy(alpha = 0.5f) ?: PikuColors.border),
                ChipShape,
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
internal fun LlmChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = if (selected) PikuColors.textPrimary else PikuColors.textSecondary,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        modifier = modifier
            .clip(ChipShape)
            .background(if (selected) PikuColors.surfaceSoft else Color.Transparent)
            .border(
                BorderStroke(
                    0.5.dp,
                    if (selected) PikuColors.controlAccent.copy(alpha = 0.45f) else PikuColors.border,
                ),
                ChipShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LlmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    error: Boolean = false,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
    requester: BringIntoViewRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val editing = focused && WindowInsets.isImeVisible
    val lineColor = when {
        error -> PikuColors.error
        editing -> PikuColors.controlAccent
        else -> PikuColors.border
    }
    val lineWidth = if (error || editing) 1.5.dp else 0.5.dp
    val font = if (mono) FontFamily.Monospace else null
    val fontSize = if (mono) 12.sp else 13.sp

    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (requester != null) Modifier.bringIntoViewRequester(requester)
                else Modifier
            ),
    ) {
        if (label != null) {
            Text(text = label, color = PikuColors.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .drawBehind {
                    val y = size.height - lineWidth.toPx() / 2
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), lineWidth.toPx())
                }
                .padding(bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = TextStyle(
                    fontSize = fontSize,
                    color = if (enabled) PikuColors.textPrimary else PikuColors.textSecondary,
                    fontFamily = font,
                ),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(PikuColors.controlAccent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = PikuColors.textFaint,
                            fontSize = fontSize,
                            fontFamily = font,
                            maxLines = 1,
                        )
                    }
                    inner()
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
internal fun LlmTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    editorHeight: Dp = 240.dp,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .clip(shape)
            .background(PikuColors.surfaceSoft)
            .border(BorderStroke(0.5.dp, PikuColors.border), shape)
            .padding(12.dp),
    ) {
        if (readOnly) {
            Text(
                text = value,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = PikuColors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = PikuColors.textPrimary,
                ),
                cursorBrush = SolidColor(PikuColors.controlAccent),
                modifier = Modifier.fillMaxWidth().height(editorHeight),
            )
        }
    }
}

@Composable
internal fun LlmActionButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 48.dp,
) {
    val bg = if (primary) PikuColors.controlAccent else Color.Transparent
    val fg = if (primary) onAccent() else PikuColors.controlAccent
    Box(
        modifier
            .height(height)
            .clip(ButtonShape)
            .background(bg)
            .then(
                if (primary) Modifier
                else Modifier.border(BorderStroke(0.5.dp, PikuColors.border), ButtonShape)
            )
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = fg,
                strokeWidth = 1.5.dp,
            )
        } else {
            Text(text = text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun LlmDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = PikuColors.border,
        thickness = 0.5.dp,
        modifier = modifier.padding(vertical = 13.dp),
    )
}

@Composable
internal fun LlmChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = PikuColors.textFaint,
        modifier = modifier.size(14.dp),
    )
}

@Composable
internal fun LlmTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PikuColors.controlAccent,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        color = if (enabled) color else PikuColors.textSecondary.copy(alpha = 0.7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(ChipShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
internal fun LlmEntryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = PikuColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        LlmChevron()
    }
}

@Composable
internal fun LlmOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(OptionShape)
            .background(if (selected) PikuColors.surfaceSoft else Color.Transparent)
            .border(
                BorderStroke(
                    0.5.dp,
                    if (selected) PikuColors.controlAccent.copy(alpha = 0.35f) else PikuColors.border,
                ),
                OptionShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) PikuColors.textPrimary else PikuColors.textFaint,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = PikuColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            badge()
        }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PikuColors.textPrimary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun LlmStatusRow(
    text: String,
    color: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
internal fun TestStatusText(
    test: ProviderTestState?,
    provider: ManualProvider,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val secondary = PikuColors.textSecondary
    when (test) {
        ProviderTestState.Testing -> LlmStatusRow(
            text = stringResource(R.string.llm_manual_testing),
            color = secondary,
            modifier = modifier,
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    color = secondary,
                    strokeWidth = 1.4.dp,
                )
            },
            trailing = if (onCancel != null) {
                {
                    LlmTextAction(
                        text = stringResource(R.string.llm_provider_cancel),
                        onClick = onCancel,
                        color = secondary,
                    )
                }
            } else {
                null
            },
        )
        is ProviderTestState.Success -> LlmStatusRow(
            text = stringResource(R.string.llm_manual_connected_latency, test.latencyMs),
            color = secondary,
            modifier = modifier,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(13.dp),
                )
            },
        )
        is ProviderTestState.Failed -> LlmStatusRow(
            text = if (test.message.isNullOrBlank()) {
                stringResource(R.string.llm_manual_key_missing)
            } else {
                stringResource(R.string.llm_manual_failed_detail, test.message)
            },
            color = PikuColors.error,
            modifier = modifier,
            maxLines = 2,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = PikuColors.error,
                    modifier = Modifier.size(13.dp),
                )
            },
        )
        else -> Text(
            text = if (provider.authMode != AuthMode.NONE && !provider.hasKey) {
                stringResource(R.string.llm_manual_key_missing)
            } else {
                stringResource(R.string.llm_manual_untested)
            },
            color = secondary,
            fontSize = 11.sp,
            modifier = modifier,
        )
    }
}

@Composable
internal fun roleLabel(role: String): String = stringResource(
    when (role) {
        Role.TEXT -> R.string.llm_role_text
        Role.NOVEL -> R.string.llm_role_novel
        Role.IMAGE -> R.string.llm_role_image
        else -> R.string.llm_role_text
    },
)

@Composable
internal fun authLabel(provider: ManualProvider): String = stringResource(
    when (provider.authMode) {
        AuthMode.NONE -> R.string.llm_manual_no_auth
        AuthMode.BEARER -> R.string.llm_manual_bearer
        AuthMode.HEADER -> R.string.llm_manual_header
    },
)

@Composable
internal fun SettingsFooter(
    dark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val divider = PikuColors.border
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
            .drawBehind {
                drawLine(
                    color = divider,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        content = content,
    )
}

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        color = PikuColors.textSecondary,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 2.dp),
    )
}

@Composable
internal fun FieldError(text: String?) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        color = PikuColors.error,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
    )
}
