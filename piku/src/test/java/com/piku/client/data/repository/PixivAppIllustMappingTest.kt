package com.piku.client.data.repository

import com.piku.client.data.remote.pixiv.PixivAppImageUrls
import com.piku.client.data.remote.pixiv.PixivAppIllustFull
import com.piku.client.data.remote.pixiv.PixivAppMetaPage
import com.piku.client.data.remote.pixiv.PixivAppMetaSinglePage
import org.junit.Assert.assertEquals
import org.junit.Test

class PixivAppIllustMappingTest {

    private val medium = "https://i.pximg.net/c/540x540_70/img-master/a_p0_master1200.jpg"
    private val large = "https://i.pximg.net/c/1200x1200_90_a2/img-master/a_p0_master1200.jpg"
    private val original = "https://i.pximg.net/img-original/a_p0.jpg"

    @Test
    fun multiPageMapsEachMetaPageWithOriginal() {
        val pages = PixivAppIllustFull(
            width = 1200, height = 686, pageCount = 2,
            metaPages = listOf(
                PixivAppMetaPage(
                    imageUrls = PixivAppImageUrls(medium = medium, large = large),
                    originalImageUrl = original,
                ),
                PixivAppMetaPage(imageUrls = PixivAppImageUrls(large = large)),
            ),
        ).toSourceWorkPages()

        assertEquals(2, pages.size)
        assertEquals(medium, pages[0].url)
        assertEquals(large, pages[0].fullUrl)
        assertEquals(original, pages[0].originalUrl)
        // app-api 不带逐页尺寸，只有作品本体尺寸（= 首页）
        assertEquals(1200, pages[0].width)
        assertEquals(0, pages[1].width)
    }

    @Test
    fun singlePageTakesOriginalFromMetaSinglePage() {
        val pages = PixivAppIllustFull(
            width = 1200, height = 686,
            imageUrls = PixivAppImageUrls(medium = medium, large = large),
            metaSinglePage = PixivAppMetaSinglePage(originalImageUrl = original),
        ).toSourceWorkPages()

        assertEquals(1, pages.size)
        assertEquals(medium, pages[0].url)
        assertEquals(large, pages[0].fullUrl)
        assertEquals(original, pages[0].originalUrl)
    }

    @Test
    fun missingMediumFallsBackToLargeSoThePageIsNeverEmpty() {
        val pages = PixivAppIllustFull(
            imageUrls = PixivAppImageUrls(large = large),
            metaSinglePage = PixivAppMetaSinglePage(originalImageUrl = original),
        ).toSourceWorkPages()

        assertEquals(large, pages[0].url)
        assertEquals(large, pages[0].fullUrl)
    }

    @Test
    fun missingPageOriginalLeavesItBlankSoSaveFallsBackOneStep() {
        val pages = PixivAppIllustFull(
            metaPages = listOf(PixivAppMetaPage(imageUrls = PixivAppImageUrls(large = large))),
        ).toSourceWorkPages()

        assertEquals("", pages[0].originalUrl)
    }
}
