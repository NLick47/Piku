package com.piku.client.ui.home

import androidx.compose.ui.graphics.Color
import com.piku.client.data.local.SampledImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabBackdropTest {

    @Test
    fun swappingTheImageSwapsTheColorImmediately() {
        // 同一取景、同一横带，只换图：亮底图压深字、暗底图压浅字。
        // 取色只依赖"当前这张图的这一小块"，所以换图当帧就会重算；
        // 旧实现把亮度在存图时算好存起来（background_top_luma），存图那次没算出来
        // 就只剩"下次启动补算"这一条路——"要重启才生效"就是这么来的。
        val bright = SampledImage(brightPixels(), width = 16, height = 100)
        val dark = SampledImage(darkPixels(), width = 16, height = 100)
        val rect = ImageRect(0f, 0.381f, 1f, 0.503f)

        val brightLuma = veiledLuma(meanLumaOfRect(bright, rect)!!, 0f, Color.Black)
        val darkLuma = veiledLuma(meanLumaOfRect(dark, rect)!!, 0f, Color.Black)
        assertEquals(Color.Black, feedTabColors(true, brightLuma)!!.active)
        assertEquals(Color.White, feedTabColors(true, darkLuma)!!.active)
        assertTrue("换图后判色必须换边：$brightLuma / $darkLuma", brightLuma > TAB_LUMA_THRESHOLD)
        assertTrue(darkLuma < TAB_LUMA_THRESHOLD)
    }

    @Test
    fun meanLumaAveragesOnlyTheRequestedRect() {
        val image = SampledImage(skyOverGround(), width = 16, height = 100)
        // 上半亮（1.0）、下半暗（16/255≈0.063）
        assertEquals(1f, meanLumaOfRect(image, ImageRect(0f, 0f, 1f, 0.5f))!!, 1e-3f)
        assertEquals(0.0627f, meanLumaOfRect(image, ImageRect(0f, 0.5f, 1f, 1f))!!, 1e-3f)
        assertEquals(0.531f, meanLumaOfRect(image, ImageRect(0f, 0f, 1f, 1f))!!, 1e-3f)
        // 横向切一半也不影响：这一列像素同上亮下暗
        assertEquals(1f, meanLumaOfRect(image, ImageRect(0.5f, 0f, 1f, 0.5f))!!, 1e-3f)
        assertNull(meanLumaOfRect(null, ImageRect(0f, 0f, 1f, 1f)))
    }

    @Test
    fun guessedBandHidesTheRealContrast() {
        val image = SampledImage(skyOverGround(), width = 16, height = 100)
        // 标签行真实覆盖（真机 321~424px 反算出来的那条）
        val measured = meanLumaOfRect(image, ImageRect(0f, 0.381f, 1f, 0.503f))!!
        // 旧口径：图片高度 0.30~0.75 整条
        val guessed = meanLumaOfRect(image, ImageRect(0f, 0.30f, 1f, 0.75f))!!

        assertTrue("标签行底下其实很亮：$measured", measured > TAB_LUMA_THRESHOLD)
        assertTrue("旧口径把同一张图判成暗底：$guessed", guessed < TAB_LUMA_THRESHOLD)
        // 于是同一张亮底图：新口径压深字，旧口径压浅字（白底白字）
        assertEquals(Color.Black, feedTabColors(hasCustomBackground = true, bandLuma = measured)!!.active)
        assertEquals(Color.White, feedTabColors(hasCustomBackground = true, bandLuma = guessed)!!.active)
    }

    @Test
    fun realFramingDecidesColorOnABrightSkyImage() {
        // 全链路：真机取景 → 标签行横带 → 图片归一化矩形 → 亮度 → 判色
        val frame = contentFrame(
            imgWidth = 1685,
            imgHeight = 934,
            viewWidth = 1240f,
            viewHeight = 836.7f,
            scale = 1.014174f,
            offsetX = -0.9292047f,
            offsetY = -0.6029628f,
        )!!
        val sample = tabBandSample(
            bandTop = 321f,
            bandBottom = 424f,
            heroPath = "hero.png",
            heroFrame = frame,
            frostPath = null,
            frostFrame = null,
        )!!
        assertEquals("hero.png", sample.path)
        val image = SampledImage(skyOverGround(), width = 16, height = 100)
        val luma = veiledLuma(meanLumaOfRect(image, sample.rect)!!, veilAlpha = 0f, veilColor = Color.Black)
        assertTrue("亮底该判深字：$luma", luma > TAB_LUMA_THRESHOLD)
    }

    @Test
    fun bandSamplePrefersHeroAndFallsBackToFrost() {
        val heroFrame = contentFrame(1000, 2000, 1240f, 836.7f, 1f, 0f, 0f)!!
        val frostFrame = contentFrame(1000, 2000, 1240f, 2772f, 1f, 0f, 0f)!!

        val onHero = tabBandSample(321f, 424f, "hero", heroFrame, "frost", frostFrame)!!
        assertEquals("hero", onHero.path)

        // 画框式把图拖到上缘时，标签行会整条落在留白上——那时露出来的是毛玻璃层
        val cardAtTop = contentFrame(2000, 1000, 1240f, 836.7f, 0.5f, 0f, -1f)!!
        val overFrost = tabBandSample(500f, 600f, "hero", cardAtTop, "frost", frostFrame)!!
        assertEquals("frost", overFrost.path)

        // 两层都盖不到（带子在图外）→ 没有可判的底
        assertNull(tabBandSample(500f, 600f, "hero", cardAtTop, "frost", null))
        assertNull(tabBandSample(424f, 321f, "hero", heroFrame, "frost", frostFrame))
        // 没有路径（还没设背景）→ 退回主题
        assertNull(tabBandSample(321f, 424f, null, heroFrame, null, frostFrame))
    }

    @Test
    fun veilStopsReproduceDrawnGradient() {
        val dim = 0.4f
        val heroFrac = 0.3f
        val stops = veilStops(heroFrac = heroFrac, dimBase = dim, midFactor = 0.5f)

        // 与绘制时那四个色标逐值一致
        assertEquals(dim, veilAlphaAt(stops, 0f), 1e-4f)
        assertEquals(dim * 0.5f, veilAlphaAt(stops, heroFrac * 0.18f), 1e-4f)
        assertEquals(dim, veilAlphaAt(stops, heroFrac + 0.04f), 1e-4f)
        assertEquals(dim * 0.6f, veilAlphaAt(stops, 1f), 1e-4f)
        // 段内线性
        val midT = (heroFrac * 0.18f + heroFrac + 0.04f) / 2f
        assertEquals((dim * 0.5f + dim) / 2f, veilAlphaAt(stops, midT), 1e-3f)
        // 超出端点按端点取值（Clamp）
        assertEquals(dim, veilAlphaAt(stops, -0.2f), 1e-4f)
        assertEquals(dim * 0.6f, veilAlphaAt(stops, 1.4f), 1e-4f)
    }

    @Test
    fun veiledLumaReadsTheCompositedBand() {
        // 压暗遮罩是压在图片上的：标签行读的是合成结果
        assertEquals(0.7f, veiledLuma(1f, veilAlpha = 0.3f, veilColor = Color.Black), 1e-3f)
        assertEquals(0.5f, veiledLuma(0f, veilAlpha = 0.5f, veilColor = Color.White), 1e-3f)
        assertEquals(0.9f, veiledLuma(0.9f, veilAlpha = 0f, veilColor = Color.Black), 1e-3f)
        // 压得够狠时亮底也成了暗底：字色该跟着遮罩走，而不是跟着原图
        val deepDim = veiledLuma(0.9f, veilAlpha = 0.8f, veilColor = Color.Black)
        assertNotNull(feedTabColors(hasCustomBackground = true, bandLuma = deepDim))
        assertEquals(Color.White, feedTabColors(true, deepDim)!!.active)
    }

    @Test
    fun veilColorFollowsThemeAndExtractedSwatches() {
        // 亮色：取到的浅色遮罩直接用，取不到用白
        assertEquals(Color.White, veilColor(dark = false, scrimDark = 0xFF123456.toInt(), scrimLight = null))
        assertEquals(
            Color(0xFFEEDDCC.toInt()),
            veilColor(dark = false, scrimDark = null, scrimLight = 0xFFEEDDCC.toInt()),
        )
        // 暗色：图片主色融进主题深色，没有主色就用黑
        val darkVeil = veilColor(dark = true, scrimDark = 0xFF446688.toInt(), scrimLight = null)
        assertNotNull(darkVeil)
        assertTrue(darkVeil.luma() < 0.5f)
        // 压暗强度与中段系数：暗色在用户 dim 上再压一点并封顶
        assertEquals(0.2f, veilDim(dark = false, dim = 0.2f), 1e-4f)
        assertTrue(veilDim(dark = true, dim = 0.2f) > 0.2f)
        assertEquals(DARK_DIM_MAX, veilDim(dark = true, dim = 1f), 1e-4f)
        assertEquals(LIGHT_DIM_MID_FACTOR, veilMidFactor(dark = false), 1e-4f)
        assertEquals(DARK_DIM_MID_FACTOR, veilMidFactor(dark = true), 1e-4f)
    }

    private fun Color.luma(): Float = luma(this)

    /** 上半亮、下半暗的一张图（16×100）：像"白纸/天空压着暗部"的常见底图 */
    private fun skyOverGround(): IntArray = IntArray(16 * 100) { index ->
        if (index / 16 < 50) 0xFFFFFFFF.toInt() else 0xFF101010.toInt()
    }

    /** 整张都亮（纸白插画） */
    private fun brightPixels(): IntArray = IntArray(16 * 100) { 0xFFF2F0EA.toInt() }

    /** 整张都暗（夜景） */
    private fun darkPixels(): IntArray = IntArray(16 * 100) { 0xFF14161C.toInt() }
}
