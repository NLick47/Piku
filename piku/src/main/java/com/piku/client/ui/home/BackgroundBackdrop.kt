package com.piku.client.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.PillBorderLight
import kotlin.math.PI
import kotlin.math.sin

private val ScrollProgressThickness = 1.5.dp

/** 高光推进的最小步长（约 30fps）：慢速漂移不需要每帧重绘整个头部 */
private const val ShimmerStepNanos = 33_000_000L

/** 滚出顶部 / 回到顶部时玻璃底衬浮出与退场的时长 */
private const val GlassVeilMillis = 220

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
 * 绘制对象只跟尺寸与主题有关，交给 drawWithCache 缓存，避免每帧重建 Brush/Shader。
 */
internal class HomeBackdrop(
    val base: Brush,
    val blobs: List<Blob>,
) {
    class Blob(val brush: Brush, val center: Offset, val radius: Float)
}

internal fun DrawScope.drawBackdrop(backdrop: HomeBackdrop, baseOnly: Boolean = false) {
    drawRect(brush = backdrop.base)
    if (baseOnly) return
    backdrop.blobs.forEach { drawCircle(brush = it.brush, radius = it.radius, center = it.center) }
}

internal fun Density.homeBackdrop(dark: Boolean, size: Size): HomeBackdrop {
    val blobWarm = if (dark) Color(0x33C98A2D) else Color(0x4DC98A2D)
    val blobPink = if (dark) Color(0x33D8A8B8) else Color(0x4DD8A8B8)
    fun blob(color: Color, cx: Float, cy: Float, radius: Float): HomeBackdrop.Blob {
        val center = Offset(cx, cy)
        return HomeBackdrop.Blob(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = center,
                radius = radius,
            ),
            center = center,
            radius = radius,
        )
    }
    return HomeBackdrop(
        base = Brush.verticalGradient(
            if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
            else listOf(HomeBgTopLight, HomeBgBottomLight),
        ),
        blobs = listOf(
            if (dark) {
                blob(Color(0x409A7FC9), size.width - 40.dp.toPx(), 96.dp.toPx(), 120.dp.toPx())
            } else {
                blob(Color(0x4D9A7FC9), size.width - 36.dp.toPx(), 140.dp.toPx(), 76.dp.toPx())
            },
            blob(blobWarm, 0f, 400.dp.toPx(), 100.dp.toPx()),
            blob(blobPink, size.width, 620.dp.toPx(), 90.dp.toPx()),
        ),
    )
}

/** 头部底衬的两段透明度（顶部 / 中段），绘制时再乘上让位系数 */
internal data class GlassTint(val top: Float, val mid: Float)

/**
 * 头部让位系数：自定义背景下列表停在顶部时取 0，底衬连同装饰全部退场，
 * 头图从头部控件后面完整露出来；一旦滚动就回到 1，恢复原来的玻璃底衬。
 * 非自定义背景（底衬就是页面本身）恒为 1。
 */
internal fun headerVeilTarget(translucent: Boolean, atTop: Boolean): Float =
    if (translucent && atTop) 0f else 1f

/** 头部底衬透明度，与浮层化之前逐值一致 */
internal fun headerTintAlphas(
    translucent: Boolean,
    dark: Boolean,
    deepen: Float,
): GlassTint = when {
    translucent && dark -> GlassTint(top = 0.16f + 0.05f * deepen, mid = 0.10f + 0.04f * deepen)
    translucent -> GlassTint(top = 0f, mid = 0f)
    dark -> GlassTint(top = 0.50f + 0.10f * deepen, mid = 0.32f + 0.14f * deepen)
    else -> GlassTint(top = 0.95f + 0.03f * deepen, mid = 0.80f + 0.08f * deepen)
}

private val GlassBandDark = listOf(
    Color.Transparent,
    Color.White.copy(alpha = 0.10f),
    Color.White.copy(alpha = 0.14f),
    Color.Transparent,
)

private val GlassBandLight = listOf(
    Color.Transparent,
    Color.White.copy(alpha = 0.12f),
    Color.White.copy(alpha = 0.20f),
    Color.Transparent,
)

@Composable
internal fun LiquidGlassBackdrop(
    dark: Boolean,
    isScrolling: State<Boolean>,
    drawerIsOpen: Boolean = false,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    /** 滚动进度 0~1：在底边那条线上从左往右染色。在绘制阶段读取，滚动不会触发重组 */
    progress: () -> Float = { 0f },
    /** 列表停在顶部：自定义背景下头部把画面让给头图，底衬整体退场 */
    atTop: Boolean = false,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var acc by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isResumed, drawerIsOpen) {
        if (!isResumed || drawerIsOpen) return@LaunchedEffect
        var lastUpdateNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val elapsed = frameNanos - lastUpdateNanos
                if (lastUpdateNanos == 0L) {
                    lastUpdateNanos = frameNanos
                } else if (elapsed >= ShimmerStepNanos) {
                    acc = (acc + elapsed / 1_000_000_000f / 12f) % 1f
                    lastUpdateNanos = frameNanos
                }
            }
        }
    }
    val deepen = animateFloatAsState(
        targetValue = if (isScrolling.value) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "glassDeepen",
    )
    val veil by animateFloatAsState(
        targetValue = headerVeilTarget(translucent, atTop),
        animationSpec = tween(durationMillis = GlassVeilMillis),
        label = "glassVeil",
    )
    val tintTop = if (dark) HomeBgTopDark else HomeBgTopLight
    val tintBottom = if (dark) Color(0xFF2B2533) else HomeBgTopLight
    Box(modifier) {
        if (!translucent) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val backdrop = homeBackdrop(dark, size)
                        onDrawBehind { drawBackdrop(backdrop, baseOnly = !dark) }
                    },
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val tint = headerTintAlphas(translucent, dark, deepen.value)
                    val topAlpha = tint.top * veil
                    val midAlpha = tint.mid * veil
                    drawRect(
                        brush = if (translucent) {
                            Brush.verticalGradient(
                                0f to tintTop.copy(alpha = topAlpha),
                                0.6f to tintTop.copy(alpha = midAlpha),
                                1f to Color.Transparent,
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    tintTop.copy(alpha = topAlpha),
                                    tintBottom.copy(alpha = midAlpha),
                                ),
                            )
                        },
                    )
                },
        )
        Box(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val topSheen = Brush.verticalGradient(
                        listOf(
                            if (dark) {
                                Color.White.copy(alpha = 0.15f * veil)
                            } else {
                                Color.White.copy(alpha = 0.55f * veil)
                            },
                            Color.Transparent,
                        ),
                    )
                    val bandSource = if (dark) GlassBandDark else GlassBandLight
                    val bandColors = if (veil == 1f) {
                        bandSource
                    } else {
                        bandSource.map { it.copy(alpha = it.alpha * veil) }
                    }
                    onDrawBehind {
                        val liquid = acc
                        val sheen = -0.5f + ((acc * 2f) % 1f) * 2f
                        drawGlassShine(
                            sheen = sheen,
                            liquid = liquid,
                            dark = dark,
                            topSheen = topSheen,
                            bandColors = bandColors,
                            decorations = !translucent,
                            progress = progress,
                            veil = veil,
                        )
                    }
                },
        )
    }
}

private fun DrawScope.drawGlassShine(
    sheen: Float,
    liquid: Float,
    dark: Boolean,
    topSheen: Brush,
    bandColors: List<Color>,
    decorations: Boolean = true,
    progress: () -> Float = { 0f },
    veil: Float = 1f,
) {
    drawRect(
        brush = topSheen,
        size = Size(size.width, 2.dp.toPx()),
    )
    val band = size.width * 0.5f
    val centerX = (sheen - 0.5f) * (size.width + band * 2f)
    drawRect(
        brush = Brush.linearGradient(
            colors = bandColors,
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
        Color.White.copy(alpha = 0.18f * veil)
    } else {
        Color(0xFF2C2C2C).copy(alpha = 0.12f * veil)
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
    val hairline = if (dark) Color.White.copy(alpha = 0.14f) else PillBorderLight
    drawLine(
        color = hairline.copy(alpha = hairline.alpha * veil),
        start = Offset(0f, size.height - 0.5.dp.toPx()),
        end = Offset(size.width, size.height - 0.5.dp.toPx()),
        strokeWidth = 0.5.dp.toPx(),
    )

    // 滚动进度直接压在底边那条线上：左边已读过的部分染成强调色，线同时充当进度轨道
    val filled = progress().coerceIn(0f, 1f)
    if (filled > 0f) {
        val thickness = ScrollProgressThickness.toPx()
        drawRoundRect(
            color = if (dark) LoginTextPrimaryDark else AccentDark,
            topLeft = Offset(0f, size.height - thickness),
            size = Size(size.width * filled, thickness),
            cornerRadius = CornerRadius(thickness / 2f, thickness / 2f),
        )
    }
}
