package com.piku.client.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 背景编辑蓝图覆盖层（高对比分区可视化）：
 * - 清晰区：四角取景括号框出头部取样范围（相机取景框样式）；
 * - 头部画框式（缩放 <1）：虚线圆角矩形框出整幅图的实际占位，直观看到留白范围；
 * - 左右/上下刻线：该方向当前可平移才画，和手势用同一份几何（[ContentFrame]）。
 */
@Composable
internal fun BackgroundBlueprintOverlay(
    dark: Boolean,
    /** 头部清晰区高度（像素）：边界线与分区标签都锚在它上面 */
    zoneHeightPx: Float,
    /** 头部图绘制矩形；null 表示没有图片尺寸信息（只画头部区） */
    heroFrame: ContentFrame?,
    /** 头部图进入画框式的程度 0~1：圆角与投影随之过渡 */
    framedT: Float,
    /** 当前编辑对象的取景偏移读数（-1~1） */
    readoutX: Float,
    readoutY: Float,
    minimal: Boolean = false,
    editingBackdrop: Boolean = false,
) {
    val density = LocalDensity.current
    val zoneHeightDp = with(density) { zoneHeightPx.toDp() }
    val lineColor = if (dark) {
        Color.White.copy(alpha = if (minimal) 0.55f else 0.7f)
    } else {
        Color.Black.copy(alpha = if (minimal) 0.45f else 0.55f)
    }
    val textColor = if (dark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.6f)
    val heroLineColor = if (editingBackdrop) {
        lineColor.copy(alpha = lineColor.alpha * 0.4f)
    } else {
        lineColor
    }
    val antsPhase by rememberInfiniteTransition(label = "ants").animateFloat(
        initialValue = 0f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "antsPhase",
    )
    val antsEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -antsPhase)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val heroPx = zoneHeightPx.coerceIn(0f, size.height)

            if (!minimal) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.08f),
                    topLeft = Offset(0f, heroPx),
                    size = Size(size.width, size.height - heroPx),
                )
                val hatchGap = 16.dp.toPx()
                val zoneH = size.height - heroPx
                var hx = -zoneH
                while (hx < size.width) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.25f),
                        start = Offset(hx, heroPx),
                        end = Offset(hx + zoneH, size.height),
                        strokeWidth = 1f,
                    )
                    hx += hatchGap
                }
            }

            val inset = 12.dp.toPx()
            val bracketLen = 22.dp.toPx()
            val bracketStroke = 2.5.dp.toPx()
            listOf(
                Triple(Offset(inset, inset), 1f, 1f),
                Triple(Offset(size.width - inset, inset), -1f, 1f),
                Triple(Offset(inset, heroPx - inset), 1f, -1f),
                Triple(Offset(size.width - inset, heroPx - inset), -1f, -1f),
            ).forEach { (pos, dx, dy) ->
                drawLine(heroLineColor, pos, Offset(pos.x + bracketLen * dx, pos.y), bracketStroke)
                drawLine(heroLineColor, pos, Offset(pos.x, pos.y + bracketLen * dy), bracketStroke)
            }

            if (!minimal && heroFrame != null && framedT > 0f) {
                drawRoundRect(
                    color = heroLineColor,
                    topLeft = Offset(heroFrame.left, heroFrame.top),
                    size = Size(heroFrame.width, heroFrame.height),
                    cornerRadius = CornerRadius(24.dp.toPx() * framedT),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect),
                )
            }

            if (!minimal && editingBackdrop) {
                val borderInset = 10.dp.toPx()
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(borderInset, borderInset),
                    size = Size(
                        size.width - borderInset * 2,
                        size.height - borderInset * 2,
                    ),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            drawLine(
                color = heroLineColor,
                start = Offset(0f, heroPx),
                end = Offset(size.width, heroPx),
                strokeWidth = 2.dp.toPx(),
                pathEffect = antsEffect,
            )

            if (!minimal) {
                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 0.8f,
                    pathEffect = dashEffect,
                )
                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 0.8f,
                    pathEffect = dashEffect,
                )
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 1.2f),
                )
            }

            if (!minimal && heroFrame != null) {
                val markLen = 48.dp.toPx()
                if (abs(heroFrame.slackX) > 1f) {
                    listOf(
                        Offset(6f, size.height / 2f),
                        Offset(size.width - 6f, size.height / 2f),
                    ).forEach { c ->
                        drawLine(
                            color = lineColor,
                            start = Offset(c.x, c.y - markLen / 2f),
                            end = Offset(c.x, c.y + markLen / 2f),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
                if (abs(heroFrame.slackY) > 1f) {
                    listOf(
                        Offset(size.width / 2f, 6f),
                        Offset(size.width / 2f, size.height - 6f),
                    ).forEach { c ->
                        drawLine(
                            color = lineColor,
                            start = Offset(c.x - markLen / 2f, c.y),
                            end = Offset(c.x + markLen / 2f, c.y),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
            }
        }

        if (!minimal) {
            Text(
                text = stringResource(R.string.background_zone_sharp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = (zoneHeightDp - 44.dp).coerceAtLeast(8.dp))
                    .background(Color(0xE600BCD4), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.background_zone_blur),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = zoneHeightDp + 12.dp)
                    .background(Color(0xE67C4DFF), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Text(
            text = stringResource(
                R.string.background_offset_readout,
                (readoutX * 100).roundToInt(),
                (readoutY * 100).roundToInt(),
            ),
            color = textColor,
            fontSize = if (minimal) 10.sp else 11.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (minimal) zoneHeightDp + 10.dp else zoneHeightDp + 56.dp)
                .background(
                    color = if (dark) {
                        Color.Black.copy(alpha = if (minimal) 0.25f else 0.35f)
                    } else {
                        Color.White.copy(alpha = if (minimal) 0.45f else 0.55f)
                    },
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = if (minimal) 8.dp else 12.dp, vertical = 3.dp),
        )
    }
}
