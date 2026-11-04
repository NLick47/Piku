package com.piku.client.core.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 卡片：暖白/深灰底 + 细边框 + 阴影。底色不透明，避免阴影透进卡片内形成一圈深色描边 */
@Composable
fun GlassCard(
    dark: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    bgColor: Color = if (dark) Color(0xFF262421) else Color(0xFFFBFAF9),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x33000000),
            )
            .clip(shape)
            .background(bgColor)
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x26FFFFFF) else Color(0x66FFFFFF)),
                shape,
            ),
        content = content,
    )
}