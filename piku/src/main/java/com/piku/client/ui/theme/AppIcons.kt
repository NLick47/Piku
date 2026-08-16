package com.piku.client.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 圆润胖房子轮廓：纯框架无内层细节，用于"返回主页"按钮 */
val HomeFrameIcon: ImageVector = ImageVector.Builder(
    name = "HomeFrame",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString("M3,11.5 L12,4.5 L21,11.5 L21,19.5 L3,19.5 Z").toNodes(),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2.6f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
).build()
