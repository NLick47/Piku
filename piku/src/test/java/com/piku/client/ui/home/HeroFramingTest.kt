package com.piku.client.ui.home

import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class HeroFramingTest {

    // 真机实测的一组取景参数
    private val screenW = 1240f
    private val screenH = 2772f
    private val zoneH = 836.7f
    private val imgW = 1685
    private val imgH = 934
    private val heroScale = 1.014174f
    private val heroOffsetX = -0.9292047f

    @Test
    fun zoneHeightFollowsScreenHeightWithFloors() {
        // 354×792dp 的机器：0.34 → 269dp
        assertEquals(269.28f, heroZoneHeightDp(792f, 0.34f), 0.01f)
        // 比例再小也在 200dp 以上，比例再大也封顶 420dp
        assertEquals(200f, heroZoneHeightDp(792f, 0.1f), 0.01f)
        assertEquals(420f, heroZoneHeightDp(2000f, 0.5f), 0.01f)
    }

    @Test
    fun sliderRangeMatchesTheZoneClamp() {
        // 本机 792dp：下限 200/792=0.2525、上限 420/792=0.53 被外围 0.45 收住
        val own = heroFractionRange(792f)
        assertEquals(0.2525f, own.start, 0.001f)
        assertEquals(0.45f, own.endInclusive, 0.001f)
        // 下限正好落在清晰区的 200dp 地板上：以前量程写死 0.22~0.45，底部 14% 推不动
        assertEquals(HERO_ZONE_MIN_DP, heroZoneHeightDp(792f, own.start), 0.01f)
        // 这台机不够高，量程上限用不到 420dp 封顶（0.45*792=356dp），顶部本来就没有死区
        assertEquals(356.4f, heroZoneHeightDp(792f, own.endInclusive), 0.1f)

        // 短屏 640dp：下限抬到 0.3125（写死量程时底部 40% 是死区）
        val short = heroFractionRange(640f)
        assertEquals(0.3125f, short.start, 0.001f)
        assertEquals(200f, heroZoneHeightDp(640f, short.start), 0.01f)

        // 高屏 1000dp：上限收到 0.42，顶部不再有死区
        val tall = heroFractionRange(1000f)
        assertEquals(0.22f, tall.start, 0.001f)
        assertEquals(0.42f, tall.endInclusive, 0.001f)
        assertEquals(420f, heroZoneHeightDp(1000f, tall.endInclusive), 0.01f)

        // 极端屏高也不能出现 start > endInclusive
        listOf(100f, 320f, 5000f).forEach { h ->
            val range = heroFractionRange(h)
            assertTrue("h=$h ${range.start}..${range.endInclusive}", range.start <= range.endInclusive)
        }
    }

    @Test
    fun cropModeMatchesCoverCropAtScaleOne() {
        // 缩放 1x：内容尺寸 = 铺满头部区的大小，居中——与旧 Crop 画面一致，缩放临界处不跳
        val frame = frame(scale = 1f, offsetX = 0f, offsetY = 0f)!!
        val fill = max(screenW / imgW, zoneH / imgH)
        assertEquals(imgW * fill, frame.width, 0.01f)
        assertEquals(imgH * fill, frame.height, 0.01f)
        assertEquals(-(frame.width - screenW) / 2f, frame.left, 0.01f)
        assertEquals(-(frame.height - zoneH) / 2f, frame.top, 0.01f)
        assertFalse("裁切态不算画框", frame.framed)
    }

    @Test
    fun cardModeMatchesLegacySlackPlacement() {
        // 缩放 <1：与旧画框公式 topLeft = slack + offset*slack 逐值一致，老设置换过来不位移
        val scale = 0.7f
        val frame = frame(scale = scale, offsetX = -0.4f, offsetY = 0.25f)!!
        val fill = max(screenW / imgW, zoneH / imgH)
        val cardW = imgW * fill * scale
        val cardH = imgH * fill * scale
        val slackX = (screenW - cardW) / 2f
        val slackY = (zoneH - cardH) / 2f
        assertEquals(slackX + (-0.4f) * slackX, frame.left, 0.01f)
        assertEquals(slackY + 0.25f * slackY, frame.top, 0.01f)
        assertTrue("整幅落在头部区内", frame.framed)
    }

    @Test
    fun cropModeOffsetsUseSignedSlackSoImageFollowsFinger() {
        // 裁切态：向右拖 100px → 图片右移 100px（旧实现方向相反）
        val before = frame(scale = heroScale, offsetX = 0f, offsetY = 0f)!!
        val offset = dragOffset(current = 0f, panPx = 100f, slack = before.slackX)
        val after = frame(scale = heroScale, offsetX = offset, offsetY = 0f)!!
        assertEquals(100f, after.left - before.left, 1f)
    }

    @Test
    fun cardModeOffsetsAlsoFollowFinger() {
        val before = frame(scale = 0.7f, offsetX = 0f, offsetY = 0f)!!
        val offset = dragOffset(current = 0f, panPx = 60f, slack = before.slackX)
        val after = frame(scale = 0.7f, offsetX = offset, offsetY = 0f)!!
        assertEquals(60f, after.left - before.left, 1f)
    }

    @Test
    fun dragIsOneToOneAtEveryZoom() {
        // 缩放不该改变手感到位移的比例：任何缩放级别下拖多少就走多少
        listOf(1f, heroScale, 1.2f, 1.5f).forEach { scale ->
            val before = frame(scale = scale, offsetX = 0f, offsetY = 0f)!!
            val offset = dragOffset(current = 0f, panPx = 120f, slack = before.slackX)
            val after = frame(scale = scale, offsetX = offset, offsetY = 0f)!!
            assertEquals("scale=$scale", 120f, after.left - before.left, 1f)
        }
    }

    @Test
    fun zoomExtendsPanRange() {
        // 放大后可平移量跟着变大（底图本身已溢出，所以增长比缩放更快），
        // 才能靠放大看到图片边缘；旧实现放大不改范围，拖到底也到不了边
        val base = abs(frame(scale = 1f, offsetX = 0f, offsetY = 0f)!!.slackX)
        val zoomed = abs(frame(scale = 1.5f, offsetX = 0f, offsetY = 0f)!!.slackX)
        assertEquals(134.7f, base, 0.1f)
        assertEquals(512f, zoomed, 1f)
        assertTrue(zoomed > base * 2f)
    }

    @Test
    fun inertAxisKeepsOffset() {
        // 该轴没有可平移量时不写偏移，免得存下一堆对画面无效的值
        val frame = frame(scale = heroScale, offsetX = 0f, offsetY = 0f)!!
        assertEquals(-0.6f, dragOffset(current = -0.6f, panPx = 300f, slack = 0.4f), 0.0001f)
        // 有可平移量时照常跟手并夹在 -1~1
        assertEquals(-1f, dragOffset(current = 0.9f, panPx = 300f, slack = frame.slackX), 0.0001f)
        assertEquals(1f, dragOffset(current = 0.9f, panPx = -300f, slack = frame.slackX), 0.0001f)
    }

    @Test
    fun legacyScreenViewportBugIsReproduced() {
        // 旧实现按整屏高度算溢出：横图的竖轴分母只剩约 20px（手指一动偏移就饱和到 ±1），
        // 而真正能平移的竖向量只有几 px——写进去的偏移对画面几乎没有意义
        val (legacyOverflowX, legacyOverflowY) = legacyScreenOverflow(heroScale)
        val frame = frame(scale = heroScale, offsetX = 0f, offsetY = 0f)!!
        assertTrue("旧竖轴分母只有 ${legacyOverflowY}px", legacyOverflowY < 25f)
        assertTrue("真实竖向量只有 ${abs(frame.slackY)}px", abs(frame.slackY) < 10f)

        // 横轴：旧分母 1918px 对真实可平移量 145px，手指走 100px 只换来约 7px 位移
        val legacyShiftPx = 100f / legacyOverflowX *
            ((imgW * max(screenW / imgW, zoneH / imgH) - screenW) / 2f)
        assertEquals(7f, legacyShiftPx, 0.2f)
        assertTrue("旧分母比真实可平移量大一个量级", legacyOverflowX / abs(frame.slackX) > 10f)
    }

    @Test
    fun tabBandMapsToNarrowStripNotTheGuessedThirtyToSeventyFive() {
        // 标签行实测横带（该机头部区里 321~424px）反算回图片：只覆盖图片中段的一窄条
        val frame = frame(scale = heroScale, offsetX = heroOffsetX, offsetY = -0.6029628f)!!
        val rect = frame.imageRect(0f, 321f, screenW, 424f)!!
        assertEquals(0.381f, rect.y0, 0.01f)
        assertEquals(0.503f, rect.y1, 0.01f)
        assertTrue("取样带只覆盖图片的一小条", rect.y1 - rect.y0 < 0.15f)

        // 旧实现固定取图片 0.30~0.75 高度整条求平均：高度是标签行实际覆盖的 3.7 倍，
        // 且整条的中点在标签行下方约 8% 图高（该机约 70px），把下方的明暗一起平均进来
        val guessedCenter = (0.30f + 0.75f) / 2f
        val realCenter = (rect.y0 + rect.y1) / 2f
        assertTrue("旧取样带比实际覆盖宽得多", (0.75f - 0.30f) / (rect.y1 - rect.y0) > 3f)
        assertTrue("旧取样带明显偏低", guessedCenter > realCenter + 0.05f)
    }

    @Test
    fun imageSizeMissingReturnsNull() {
        assertNull(contentFrame(null, null, screenW, zoneH, 1f, 0f, 0f))
        assertNull(contentFrame(0, 0, screenW, zoneH, 1f, 0f, 0f))
        assertNull(contentFrame(imgW, imgH, 0f, zoneH, 1f, 0f, 0f))
    }

    @Test
    fun fullyPannedImageStopsAtItsEdge() {
        // 取景偏移拉满时图片边缘正好贴住头部区边缘，不会露出后面
        val frame = frame(scale = 1.2f, offsetX = -1f, offsetY = -1f)!!
        assertEquals(0f, frame.left, 0.01f)
        assertEquals(0f, frame.top, 0.01f)
        val far = frame(scale = 1.2f, offsetX = 1f, offsetY = 1f)!!
        assertEquals(screenW, far.left + far.width, 0.01f)
        assertEquals(zoneH, far.top + far.height, 0.01f)
    }

    private fun frame(scale: Float, offsetX: Float, offsetY: Float): ContentFrame? =
        contentFrame(
            imgWidth = imgW,
            imgHeight = imgH,
            viewWidth = screenW,
            viewHeight = zoneH,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
        )

    /** 改动前的溢出算式：按整屏高度算视口（此处按原样保留，用来复现旧手感） */
    private fun legacyScreenOverflow(scale: Float): Pair<Float, Float> {
        val fill = max(screenW / imgW, screenH / imgH) * scale
        return ((imgW * fill - screenW) / 2f).coerceAtLeast(0f) to
            ((imgH * fill - screenH) / 2f).coerceAtLeast(0f)
    }
}

/** 视差与标签行取样的联动：毛玻璃层要更慢、位移一律按比例给 */
class FrostParallaxTest {

    @Test
    fun frostShiftIsSlowerThanHero() {
        assertEquals(0f, frostShiftPx(0f), 0.0001f)
        assertEquals(24f, frostShiftPx(HERO_PARALLAX_MAX_PX), 0.0001f)
        assertTrue(frostShiftPx(45f) < 45f)
        assertNotNull(frostShiftPx(90f))
    }
}
