package com.piku.client.ui.home.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piku.client.data.local.SampledImage
import com.piku.client.ui.home.FeedTabColors
import com.piku.client.ui.home.HomeUiState
import com.piku.client.ui.home.feedTabColors

/**
 * 首页背景域的派生状态：头部/雾化两层的取景几何、遮罩色，以及标签行底色（深浅）。
 * 全部由 [HomeUiState] 的背景字段与屏幕视口推导而来——背景层绘制、编辑手势、
 * 主源壳与通用壳的标签行都从这里读同一份结果，取景算法只此一处。
 */
internal class HomeBackdropState(
    /** 屏幕视口宽高（像素）：编辑手势的可平移量按它反算 */
    val viewWidthPx: Float,
    val viewHeightPx: Float,
    /** 头部清晰区高度：绘制用 dp，手势与取色用 px */
    val zoneHeightDp: Dp,
    val zoneHeightPx: Float,
    /** 头部图取景（可视矩形）；null = 图片尺寸未就绪，退回居中裁切 */
    val heroFrame: ContentFrame?,
    /** 雾化层取景：独立背景层用自己的取景，跟随模式下沿用头部 */
    val frostFrame: ContentFrame?,
    /** 雾化层的缩放与对齐偏置：独立背景层用自己的值，跟随模式下沿用头部 */
    val frostScale: Float,
    val frostOffsetX: Float,
    val frostOffsetY: Float,
    /** 遮罩色：标签行取色按它在图片亮度上衰减 */
    val veil: Color,
    /** 标签行在窗口坐标里的横带：壳经 [onTabBand] 上报，取色据此取样 */
    private val tabBandState: MutableState<TabBand?>,
    private val tabColorsState: State<FeedTabColors?>,
) {
    var tabBand: TabBand?
        get() = tabBandState.value
        private set(value) {
            tabBandState.value = value
        }

    /** 标签行字色；null = 没有自定义背景或量不到，壳退回主题默认 */
    val tabColors: FeedTabColors?
        get() = tabColorsState.value

    /** 壳在布局阶段上报标签行横带（见 reportTabBand） */
    val onTabBand: (TabBand) -> Unit
        get() = { tabBand = it }
}

@Composable
internal fun rememberHomeBackdropState(
    state: HomeUiState,
    dark: Boolean,
    /** 读背景图小图（标签行判深浅底用），宿主传入 viewModel 的取样实现 */
    sampleBackgroundImage: suspend (String) -> SampledImage?,
): HomeBackdropState {
    // 取景几何的视口：用配置里的屏幕尺寸同步算。别走 onSizeChanged——那要等一帧，
    // 首帧只能按 null 取景（忽略偏移），图片已在缓存时（旋转/重建）会看到取景跳一下
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.toFloat()
    val viewWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val viewHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val zoneHeightDp = heroZoneHeightDp(
        screenHeightDp = screenHeightDp,
        heroFraction = state.backgroundHeroFraction,
    ).dp
    val zoneHeightPx = with(density) { zoneHeightDp.toPx() }

    val heroFrame = contentFrame(
        imgWidth = state.backgroundImgWidth,
        imgHeight = state.backgroundImgHeight,
        viewWidth = viewWidthPx,
        viewHeight = zoneHeightPx,
        scale = state.heroScale,
        offsetX = state.heroOffsetX,
        offsetY = state.heroOffsetY,
    )
    val separatedBackdrop = state.backdropPath != null
    val frostScale = if (separatedBackdrop) state.backgroundScale else state.heroScale.coerceAtLeast(1f)
    val frostOffsetX = if (separatedBackdrop) state.backgroundOffsetX else state.heroOffsetX
    val frostOffsetY = if (separatedBackdrop) state.backgroundOffsetY else state.heroOffsetY
    val frostFrame = contentFrame(
        imgWidth = if (separatedBackdrop) state.backdropImgWidth else state.backgroundImgWidth,
        imgHeight = if (separatedBackdrop) state.backdropImgHeight else state.backgroundImgHeight,
        viewWidth = viewWidthPx,
        viewHeight = viewHeightPx,
        scale = frostScale,
        offsetX = frostOffsetX,
        offsetY = frostOffsetY,
    )
    val veil = veilColor(dark, state.backgroundScrimDark, state.backgroundScrimLight)

    val tabBandState = remember { mutableStateOf<TabBand?>(null) }
    val tabBand = tabBandState.value

    // 标签行字色：量的是标签行压到的那块图片。换图、改头部清晰区高度、缩放、拖取景都会改
    // 取样矩形，颜色跟着变；遮罩按停顶状态算——滚动时头部另有玻璃底衬接管，取色不该随滚动跳
    val tabLuma = produceState<Float?>(
        initialValue = null,
        state.customBackgroundPath,
        state.backdropPath,
        heroFrame,
        frostFrame,
        tabBand,
        state.backgroundDim,
        dark,
        veil,
        zoneHeightPx,
        viewHeightPx,
    ) {
        val band = tabBand
        val sample = band?.let {
            tabBandSample(
                bandTop = it.topPx,
                bandBottom = it.bottomPx,
                heroPath = state.customBackgroundPath,
                heroFrame = heroFrame,
                frostPath = state.backdropPath ?: state.customBackgroundPath,
                frostFrame = frostFrame,
            )
        }
        val imageLuma = sample?.let { sampleBackgroundImage(it.path) }
            ?.let { meanLumaOfRect(it, sample.rect) }
        // 量不到（没设背景/标签行不在图上/图读不出来）就退回主题色；
        // 只是取景变了的话保留上一次结果，拖缩放时字色才不会一闪一闪
        if (band == null || sample == null || imageLuma == null) {
            value = null
            return@produceState
        }
        val stops = veilStops(
            heroFrac = (zoneHeightPx / viewHeightPx).coerceIn(0f, 0.9f),
            dimBase = veilDim(dark, state.backgroundDim),
            midFactor = veilMidFactor(dark),
        )
        val bandMidT = ((band.topPx + band.bottomPx) / 2f / viewHeightPx).coerceIn(0f, 1f)
        value = veiledLuma(imageLuma, veilAlphaAt(stops, bandMidT), veil)
    }

    val tabColorsState = rememberUpdatedState(
        feedTabColors(
            hasCustomBackground = state.customBackgroundPath != null,
            bandLuma = tabLuma.value,
        )
    )

    return HomeBackdropState(
        viewWidthPx = viewWidthPx,
        viewHeightPx = viewHeightPx,
        zoneHeightDp = zoneHeightDp,
        zoneHeightPx = zoneHeightPx,
        heroFrame = heroFrame,
        frostFrame = frostFrame,
        frostScale = frostScale,
        frostOffsetX = frostOffsetX,
        frostOffsetY = frostOffsetY,
        veil = veil,
        tabBandState = tabBandState,
        tabColorsState = tabColorsState,
    )
}
