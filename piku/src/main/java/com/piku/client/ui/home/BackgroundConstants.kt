package com.piku.client.ui.home

import com.piku.client.data.local.SettingsRepository
import kotlin.math.exp
import kotlin.math.ln

/**
 * 首页背景相关常量与编辑目标枚举集中处。
 * HomeScreen / HomeBackground / BackgroundEditor 等文件共用，避免每个文件重复私有 const。
 */

/** 背景编辑预览模式：真实主页（所见即所得） */
internal const val BG_PREVIEW_REAL = 0

/** 背景编辑预览模式：仅背景 + 完整蓝图辅助 */
internal const val BG_PREVIEW_HIDDEN = 2

/** 背景编辑对象：头部层（清晰大图） */
internal const val BG_EDIT_TARGET_HERO = 0

/** 背景编辑对象：背景层（毛玻璃，需已分离独立图） */
internal const val BG_EDIT_TARGET_BACKDROP = 1

/** 暗色下自定义背景的额外压暗量（叠加在用户 dim 之上），让图片融入深色主题 */
internal const val DARK_DIM_EXTRA = 0.08f

/** 暗色下自定义背景的压暗上限 */
internal const val DARK_DIM_MAX = 0.85f

/** 暗色下 hero 中段遮罩透明度相对压暗值的系数（亮色沿用 0.15，保持艺术图清透） */
internal const val DARK_DIM_MID_FACTOR = 0.50f

/** 暗色 tint 融合主题深色 HomeBgTopDark 的比例（0=纯图片色，1=纯主题色） */
internal const val DARK_TINT_BLEND = 0.5f

/**
 * 按比例把 color 融合进 target（仅 RGB，保留 alpha），让图片色融入主题色
 */
internal fun androidx.compose.ui.graphics.Color.blendInto(
    target: androidx.compose.ui.graphics.Color,
    t: Float,
): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(
    red = red * (1f - t) + target.red * t,
    green = green * (1f - t) + target.green * t,
    blue = blue * (1f - t) + target.blue * t,
    alpha = alpha,
)

/**
 * 按图片在视口中的填满缩放 + 当前编辑缩放，计算水平/垂直方向超出视口的像素量。
 * 无有效图片尺寸时返回一个保守的"足够大"值，保证手势除法不 0。
 */
internal fun cropOverflowPx(
    imgWidth: Int?,
    imgHeight: Int?,
    viewWidth: Int,
    viewHeight: Int,
    scale: Float,
): Pair<Float, Float> {
    if (imgWidth == null || imgHeight == null || imgWidth <= 0 || imgHeight <= 0 ||
        viewHeight <= 0
    ) {
        return (viewWidth * 0.25f) to (viewHeight * 0.25f)
    }
    val fillScale = maxOf(
        viewWidth.toFloat() / imgWidth,
        viewHeight.toFloat() / imgHeight,
    ) * scale
    val overflowX = ((imgWidth * fillScale - viewWidth) / 2f).coerceAtLeast(0f)
    val overflowY = ((imgHeight * fillScale - viewHeight) / 2f).coerceAtLeast(0f)
    return overflowX to overflowY
}

/** hero 缩放采用对数滑杆，避免 0.x 段被压成无效空间 */
internal fun sliderToHeroScale(t: Float): Float =
    exp(
        ln(SettingsRepository.HERO_SCALE_MIN) +
            t.coerceIn(0f, 1f) * (ln(SettingsRepository.HERO_SCALE_MAX) - ln(SettingsRepository.HERO_SCALE_MIN))
    )

internal fun heroScaleToSlider(scale: Float): Float =
    ((ln(scale) - ln(SettingsRepository.HERO_SCALE_MIN)) /
        (ln(SettingsRepository.HERO_SCALE_MAX) - ln(SettingsRepository.HERO_SCALE_MIN)))
        .coerceIn(0f, 1f)
