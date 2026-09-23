package com.piku.client.ui.home

import kotlin.math.abs
import kotlin.math.max

/**
 * 头部图／背景图在视口里的摆放几何：绘制、拖拽手势、编辑蓝图、标签行取样全都走这一处。
 *
 * 两种形态共用同一套算式，不按缩放值分叉：
 * - 缩放 ≥1：图片铺满视口后继续放大，挂出视口两侧（裁切取景），可平移量 = 挂出的那半；
 * - 缩放 <1：图片小于视口时整幅可见（画框式），可平移量 = 四周留白。
 * 取景偏移 -1~1 线性铺在 |slack| 上：拖动位移与手指 1:1，缩放同步放大可平移范围。
 */
internal data class ContentFrame(
    val viewWidth: Float,
    val viewHeight: Float,
    /** 内容矩形，相对视口左上角（像素）：裁切态会挂出视口外 */
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    /** 单侧可平移量：正=图片比视口小（画框留白），负=图片挂出视口；符号即取景偏移的正方向 */
    val slackX: Float get() = (viewWidth - width) / 2f
    val slackY: Float get() = (viewHeight - height) / 2f

    /** 整幅落在视口内：画框式呈现（圆角 + 投影），没有下缘渐隐 */
    val framed: Boolean get() = width <= viewWidth && height <= viewHeight

    /**
     * 视口矩形 → 图片归一化矩形（0~1）。头部区只占视口顶部一段，标签行取样就是拿它的
     * 窗口横带反算回图片；与图片不相交（画框留白压在标签行上）时返回 null。
     */
    fun imageRect(viewLeft: Float, viewTop: Float, viewRight: Float, viewBottom: Float): ImageRect? {
        if (width <= 0f || height <= 0f) return null
        val rect = ImageRect(
            x0 = ((viewLeft - left) / width).coerceIn(0f, 1f),
            y0 = ((viewTop - top) / height).coerceIn(0f, 1f),
            x1 = ((viewRight - left) / width).coerceIn(0f, 1f),
            y1 = ((viewBottom - top) / height).coerceIn(0f, 1f),
        )
        if (rect.x1 - rect.x0 < MIN_VISIBLE_FRACTION || rect.y1 - rect.y0 < MIN_VISIBLE_FRACTION) {
            return null
        }
        return rect
    }

    companion object {
        /** 反算出的取样片段小于该比例就当作没压到图上，避免拿几个像素判一屏字色 */
        private const val MIN_VISIBLE_FRACTION = 0.01f
    }
}

/** 图片归一化矩形（0~1，左上为原点） */
internal data class ImageRect(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/**
 * 图片铺进 [viewWidth]×[viewHeight] 后按 [scale] 缩放，再按归一化取景偏移 [offsetX]/[offsetY]
 * 在溢出量内平移。图片尺寸未知（老数据）时返回 null，调用方退回按容器裁切、不可拖拽取景。
 */
internal fun contentFrame(
    imgWidth: Int?,
    imgHeight: Int?,
    viewWidth: Float,
    viewHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): ContentFrame? {
    val iw = imgWidth?.takeIf { it > 0 } ?: return null
    val ih = imgHeight?.takeIf { it > 0 } ?: return null
    if (viewWidth <= 0f || viewHeight <= 0f || scale <= 0f) return null
    val fill = max(viewWidth / iw, viewHeight / ih)
    val width = iw * fill * scale
    val height = ih * fill * scale
    return ContentFrame(
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        left = (viewWidth - width) / 2f * (1f + offsetX.coerceIn(-1f, 1f)),
        top = (viewHeight - height) / 2f * (1f + offsetY.coerceIn(-1f, 1f)),
        width = width,
        height = height,
    )
}

/**
 * 手指位移 → 新的取景偏移：只在真能平移的轴上跟手，图片永远跟着手指走。
 * [slack] 用原始符号：画框为正因为图片小于视口，裁切为负，两边除以它都得到同一个方向。
 */
internal fun dragOffset(current: Float, panPx: Float, slack: Float): Float =
    if (abs(slack) > 1f) {
        (current + panPx / slack).coerceIn(-1f, 1f)
    } else {
        current.coerceIn(-1f, 1f)
    }

/** 头部清晰区高度：屏高比例，钳在 200~420dp。绘制/手势/蓝图/取样共用，别各写一遍 */
internal fun heroZoneHeightDp(screenHeightDp: Float, heroFraction: Float): Float =
    (screenHeightDp * heroFraction).coerceIn(200f, 420f)

/** 毛玻璃层视差位移：按封顶比例缓动，比头部慢 */
internal fun frostShiftPx(heroShiftPx: Float): Float =
    heroShiftPx * (BACKDROP_PARALLAX_MAX_PX / HERO_PARALLAX_MAX_PX)
