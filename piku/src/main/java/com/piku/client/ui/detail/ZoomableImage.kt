package com.piku.client.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.piku.client.R
import com.piku.client.ui.theme.TameWhiteColorFilter
import com.piku.client.ui.theme.ViewerBackgroundDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val MIN_SCALE = 1f
internal const val MAX_SCALE = 4f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val SCALE_EPSILON = 0.01f
private const val AUTO_HIDE_DELAY_MS = 2500L

/** 缩放状态（纯数据，便于测试） */
data class ZoomState(val scale: Float = MIN_SCALE, val offset: Offset = Offset.Zero)

/**
 * 纯缩放/平移变换计算，与 UI 无关，便于单测。
 */
object ZoomTransform {

    /** 缩放时以 pivot 为锚点保持手指位置不动 */
    fun zoomTo(
        current: ZoomState,
        targetScale: Float,
        pivot: Offset,
        viewport: IntSize,
    ): ZoomState {
        if (!targetScale.isFinite()) return current
        val clamped = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (clamped == current.scale) return current
        if (clamped <= MIN_SCALE + SCALE_EPSILON) return ZoomState(MIN_SCALE, Offset.Zero)
        val k = clamped / current.scale
        return ZoomState(
            scale = clamped,
            offset = clampOffset(pivot - (pivot - current.offset) * k, clamped, viewport),
        )
    }

    /** 双指捏合/单指拖动：以 centroid 为锚点缩放（手指下的内容点不动），pan 为屏幕位移 */
    fun transform(
        current: ZoomState,
        zoomChange: Float,
        panChange: Offset,
        centroid: Offset,
        viewport: IntSize,
    ): ZoomState {
        // 第二根手指按下的瞬间上一帧距离为 0，calculateZoom 可能产生 Inf/NaN，跳过该帧
        if (!zoomChange.isFinite()) return current
        val newScale = (current.scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        if (!newScale.isFinite()) return current
        if (newScale <= MIN_SCALE + SCALE_EPSILON) return ZoomState(MIN_SCALE, Offset.Zero)
        val k = newScale / current.scale
        val targetOffset = current.offset * k + centroid * (1f - k) + panChange
        return ZoomState(
            scale = newScale,
            offset = clampOffset(targetOffset, newScale, viewport),
        )
    }

    /** 缩放时边界约束：围绕左上角原点缩放时，内容可平移范围为 [w*(1-scale), 0]（保证盖满屏幕） */
    fun clampOffset(target: Offset, scale: Float, viewport: IntSize): Offset {
        if (!target.x.isFinite() || !target.y.isFinite()) return Offset.Zero
        if (scale <= MIN_SCALE + SCALE_EPSILON) return Offset.Zero
        val minX = viewport.width * (1f - scale)
        val minY = viewport.height * (1f - scale)
        return Offset(
            target.x.coerceIn(minX, 0f),
            target.y.coerceIn(minY, 0f),
        )
    }
}

/**
 * 单页查看器图片：
 * - 缩略图常驻打底（Coil 缓存秒显），原图经 SubcomposeAsyncImage 就绪后无闪覆盖，避免空白
 * - 手势：1x 单指拖动不消费事件（留给 pager 翻页）；第二根手指按下即消费，捏合优先于翻页；
 *   已放大时消费 down，pager 不会误翻页；缩放后拖动、双击缩放/还原
 *
 * 手势循环要点（容易踩坑）：
 * - touchSlop 必须按帧累计判断，否则慢速拖动每帧位移都小于 slop，手势永远不会开始；
 * - 手指数量变化（第二根手指落下/抬起）的帧上 calculateZoom/calculatePan 跨指针集计算，
 *   会产生跳变，必须跳过该帧；
 * - pager 一旦消费了事件（1x 单指翻页中），本轮手势整体让给 pager，捏合从下一轮手势开始，
 *   避免与 pager 抢占导致页面卡在半页。
 */
@Composable
private fun ZoomableImage(
    image: ViewerImage,
    contentDescription: String?,
    dark: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var zoom by remember { mutableStateOf(ZoomState()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(viewport) {
                detectTapGestures(
                    onDoubleTap = { pivot ->
                        val zoomed = zoom.scale > MIN_SCALE + SCALE_EPSILON
                        val target = if (zoomed) MIN_SCALE else DOUBLE_TAP_SCALE
                        zoom = ZoomTransform.zoomTo(zoom, target, pivot, viewport)
                    },
                    onLongPress = { currentOnLongPress() },
                    onTap = { currentOnTap() },
                )
            }
            .pointerInput(viewport) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 已放大时立即消费 down，pager 的滑动判定直接取消，水平拖动/慢速拖动不会误翻页
                    if (zoom.scale > MIN_SCALE + SCALE_EPSILON) down.consume()

                    var pastTouchSlop = false
                    var accumulatedZoom = 1f
                    var accumulatedPan = Offset.Zero
                    var lastPointerCount = 1

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        // pager 已消费（1x 单指翻页中）：本轮手势整体让给 pager
                        if (event.changes.fastAny { it.isConsumed }) continue

                        val zoomed = zoom.scale > MIN_SCALE + SCALE_EPSILON

                        // 1x 单指拖动：不消费，交给 pager 翻页
                        if (!zoomed && pointerCount < 2) continue

                        // 已放大或第二根手指已按下：立即消费，
                        // pager 的触摸判定（会把捏合误判为翻页）看到消费后直接取消
                        event.changes.fastForEach { if (it.positionChanged()) it.consume() }

                        // 手指数量变化的帧上 calculateZoom/calculatePan 跨指针集计算会跳变，跳过
                        val pointerSetChanged = pointerCount != lastPointerCount
                        lastPointerCount = pointerCount
                        if (pointerSetChanged) continue

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        // 慢速拖动/慢速捏合按帧累计后再判断 touchSlop，
                        // 否则每帧位移都小于 slop，手势永远不会开始
                        if (!pastTouchSlop) {
                            accumulatedZoom *= zoomChange
                            accumulatedPan += panChange
                            val slop = viewConfiguration.touchSlop
                            if (accumulatedPan.getDistance() > slop ||
                                abs(1f - accumulatedZoom) * maxOf(viewport.width, viewport.height) > slop
                            ) {
                                pastTouchSlop = true
                            } else {
                                continue
                            }
                        }

                        zoom = ZoomTransform.transform(
                            current = zoom,
                            zoomChange = zoomChange,
                            panChange = panChange,
                            centroid = event.calculateCentroid(),
                            viewport = viewport,
                        )
                    } while (event.changes.fastAny { it.pressed })
                }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = image.thumbnailUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = if (dark) TameWhiteColorFilter else null,
            modifier = Modifier.fillMaxSize(),
        )
        if (image.fullUrl != null && image.fullUrl != image.thumbnailUrl) {
            SubcomposeAsyncImage(
                model = image.fullUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = if (dark) TameWhiteColorFilter else null,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = {},
            )
        }
    }
}

@Composable
fun FullScreenViewer(
    images: List<ViewerImage>,
    startPage: Int,
    dark: Boolean,
    onClose: () -> Unit,
    onSaveImage: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(
        pageCount = { images.size },
        initialPage = startPage,
    )
    var controlsVisible by remember { mutableStateOf(true) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshAutoHide() {
        controlsVisible = true
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        refreshAutoHide()
    }
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) refreshAutoHide()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 暗色下用深灰而非纯黑，降低白底大图与背景的对比，减少刺眼感
            .background(if (dark) ViewerBackgroundDark else Color.Black),
    ) {
        BackHandler(onBack = onClose)
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(
                image = images[page],
                contentDescription = null,
                dark = dark,
                onTap = {
                    if (controlsVisible) {
                        controlsVisible = false
                        autoHideJob?.cancel()
                    } else {
                        refreshAutoHide()
                    }
                },
                onLongPress = { onSaveImage(page) },
            )
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(500)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.detail_fullscreen_close),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.detail_image_index,
                        pagerState.currentPage + 1,
                        images.size,
                    ),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}