package com.piku.client.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.PillBorderLight
import kotlin.math.PI
import kotlin.math.sin

private class GlassBubble(
    val xFrac: Float,
    val phase: Float,
    val radius: androidx.compose.ui.unit.Dp,
    val alpha: Float,
    val sway: androidx.compose.ui.unit.Dp,
    val swayWaves: Float,
)

private val GlassBubbles = listOf(
    GlassBubble(xFrac = 0.12f, phase = 0.00f, radius = 4.dp, alpha = 0.22f, sway = 8.dp, swayWaves = 1.5f),
    GlassBubble(xFrac = 0.35f, phase = 0.33f, radius = 3.dp, alpha = 0.16f, sway = 6.dp, swayWaves = 2.0f),
    GlassBubble(xFrac = 0.58f, phase = 0.66f, radius = 3.5.dp, alpha = 0.24f, sway = 10.dp, swayWaves = 1.0f),
    GlassBubble(xFrac = 0.78f, phase = 0.20f, radius = 2.5.dp, alpha = 0.14f, sway = 5.dp, swayWaves = 2.5f),
    GlassBubble(xFrac = 0.92f, phase = 0.50f, radius = 2.dp, alpha = 0.12f, sway = 4.dp, swayWaves = 3.0f),
)

private class GlassSparkle(
    val xFrac: Float,
    val yFrac: Float,
    val phase: Float,
    val speed: Float,
)

private val GlassSparkles = listOf(
    GlassSparkle(0.06f, 0.30f, 0.00f, 1.5f),
    GlassSparkle(0.24f, 0.68f, 0.35f, 2.1f),
    GlassSparkle(0.48f, 0.18f, 0.70f, 1.2f),
    GlassSparkle(0.68f, 0.52f, 0.22f, 2.4f),
    GlassSparkle(0.90f, 0.35f, 0.55f, 1.8f),
)

/**
 * 页面背景（渐变 + 彩色光斑）：
 * 首页 Canvas 与头部毛玻璃衬底共用同一绘制，保证模糊层与页面背景严格对齐。
 */
internal fun DrawScope.drawHomeBackdrop(dark: Boolean) {
    drawRect(
        brush = Brush.verticalGradient(
            if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
            else listOf(HomeBgTopLight, HomeBgBottomLight),
        ),
    )
    val blobWarm = if (dark) Color(0x33C98A2D) else Color(0x4DC98A2D)
    val blobPink = if (dark) Color(0x33D8A8B8) else Color(0x4DD8A8B8)
    fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = Offset(cx, cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )
    }
    if (dark) {
        blob(Color(0x409A7FC9), size.width - 40.dp.toPx(), 96.dp.toPx(), 120.dp.toPx())
    } else {
        blob(Color(0x4D9A7FC9), size.width - 36.dp.toPx(), 140.dp.toPx(), 76.dp.toPx())
    }
    blob(blobWarm, 0f, 400.dp.toPx(), 100.dp.toPx())
    blob(blobPink, size.width, 620.dp.toPx(), 90.dp.toPx())
}

private fun DrawScope.drawHeaderBackdrop(dark: Boolean) {
    if (!dark) {
        drawRect(
            brush = Brush.verticalGradient(listOf(HomeBgTopLight, HomeBgBottomLight)),
        )
        return
    }
    drawHomeBackdrop(dark)
}

@Composable
internal fun LiquidGlassBackdrop(
    dark: Boolean,
    isScrolling: State<Boolean>,
    drawerIsOpen: Boolean = false,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var acc by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isResumed, drawerIsOpen) {
        if (!isResumed || drawerIsOpen) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val elapsedSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                    acc = (acc + elapsedSeconds / 12f) % 1f
                }
                lastFrameNanos = frameNanos
            }
        }
    }
    val deepen by animateFloatAsState(
        targetValue = if (isScrolling.value) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "glassDeepen",
    )
    val tintTop = if (dark) HomeBgTopDark else HomeBgTopLight
    val tintBottom = if (dark) Color(0xFF2B2533) else HomeBgTopLight
    val tintTopAlpha = when {
        translucent && dark -> 0.16f + 0.05f * deepen
        translucent -> 0f
        dark -> 0.50f + 0.10f * deepen
        else -> 0.95f + 0.03f * deepen
    }
    val tintMidAlpha = when {
        translucent && dark -> 0.10f + 0.04f * deepen
        translucent -> 0f
        dark -> 0.32f + 0.14f * deepen
        else -> 0.80f + 0.08f * deepen
    }
    Box(modifier) {
        if (!translucent) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind { drawHeaderBackdrop(dark) },
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    if (translucent) {
                        Brush.verticalGradient(
                            0f to tintTop.copy(alpha = tintTopAlpha),
                            0.6f to tintTop.copy(alpha = tintMidAlpha),
                            1f to Color.Transparent,
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                tintTop.copy(alpha = tintTopAlpha),
                                tintBottom.copy(alpha = tintMidAlpha),
                            ),
                        )
                    },
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val liquid = acc
                    val sheen = -0.5f + ((acc * 2f) % 1f) * 2f
                    drawGlassShine(sheen, liquid, dark, decorations = !translucent)
                },
        )
    }
}

private fun DrawScope.drawGlassShine(sheen: Float, liquid: Float, dark: Boolean, decorations: Boolean = true) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                if (dark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.55f),
                Color.Transparent,
            ),
        ),
        size = Size(size.width, 2.dp.toPx()),
    )
    val band = size.width * 0.5f
    val centerX = (sheen - 0.5f) * (size.width + band * 2f)
    drawRect(
        brush = Brush.linearGradient(
            colors = if (dark) {
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.14f),
                    Color.Transparent,
                )
            } else {
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.20f),
                    Color.Transparent,
                )
            },
            start = Offset(centerX - band / 2f, -size.height * 0.5f),
            end = Offset(centerX + band / 2f, size.height * 1.5f),
        ),
    )
    if (decorations) {
        GlassBubbles.forEach { b ->
            val t = (liquid + b.phase) % 1f
            val fade = when {
                t < 0.12f -> t / 0.12f
                t > 0.88f -> (1f - t) / 0.12f
                else -> 1f
            }
            val x = size.width * b.xFrac +
                (sin((liquid + b.phase) * 2 * PI * b.swayWaves) * b.sway.toPx()).toFloat()
            val y = size.height * (1f - t)
            val center = Offset(x, y)
            val radius = b.radius.toPx()
            if (dark) {
                drawCircle(
                    color = Color.White.copy(alpha = b.alpha * fade * 1.4f),
                    radius = radius,
                    center = center,
                )
            } else {
                drawCircle(
                    color = Color.White.copy(alpha = (0.10f + b.alpha * 2.0f) * fade),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = Color(0xFF2C2C2C).copy(alpha = 0.12f * fade),
                    radius = radius,
                    center = center,
                    style = Stroke(0.8.dp.toPx()),
                )
            }
        }
        GlassSparkles.forEach { s ->
            val twinkle = (0.5f + 0.5f * sin(liquid * 2 * PI * s.speed + s.phase * 2 * PI)).toFloat()
            val alpha = if (dark) 0.30f * twinkle else 0.22f * twinkle
            val sparkleColor = if (dark) Color(0xFFC9B8E8) else Color(0xFFFFFFFF)
            drawCircle(
                color = sparkleColor.copy(alpha = alpha),
                radius = 1.2.dp.toPx(),
                center = Offset(size.width * s.xFrac, size.height * s.yFrac),
            )
        }
    }
    val waveBase = size.height - 3.dp.toPx()
    val waveAmp = (if (dark) 1.6.dp else 2.2.dp).toPx()
    val waveColor = if (dark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color(0xFF2C2C2C).copy(alpha = 0.12f)
    }
    val steps = 32
    val stepW = size.width / steps
    val wavePhase = liquid * 4 * PI
    var prevX = 0f
    var prevY = waveBase + (sin(wavePhase) * waveAmp).toFloat()
    for (i in 1..steps) {
        val x = stepW * i
        val y = waveBase + (sin(wavePhase + x * 0.03) * waveAmp).toFloat()
        drawLine(
            color = waveColor,
            start = Offset(prevX, prevY),
            end = Offset(x, y),
            strokeWidth = 1.dp.toPx(),
        )
        prevX = x
        prevY = y
    }
    drawLine(
        color = if (dark) Color.White.copy(alpha = 0.14f) else PillBorderLight,
        start = Offset(0f, size.height - 0.5.dp.toPx()),
        end = Offset(size.width, size.height - 0.5.dp.toPx()),
        strokeWidth = 0.5.dp.toPx(),
    )
}
