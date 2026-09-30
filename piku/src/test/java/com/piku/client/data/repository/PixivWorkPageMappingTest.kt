package com.piku.client.data.repository

import com.piku.client.data.remote.pixiv.PixivPageUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class PixivWorkPageMappingTest {

    private val small = "https://i.pximg.net/c/540x540_70/img-master/a_p0_master1200.jpg"
    private val master = "https://i.pximg.net/img-master/a_p0_master1200.jpg"
    private val original = "https://i.pximg.net/img-original/a_p0.jpg"

    @Test
    fun smallIsTheFirstPaintAndMasterIsTheSharpOne() {
        val page = PixivPageUrls(thumbMini = "mini", small = small, regular = master, original = original)
            .toSourceWorkPage(width = 1200, height = 686)

        assertEquals(small, page.url)
        assertEquals(master, page.fullUrl)
        assertEquals(original, page.originalUrl)
        assertEquals(1200, page.width)
    }

    @Test
    fun missingSmallFallsBackToMasterSoThePageIsNeverEmpty() {
        val page = PixivPageUrls(regular = master, original = original).toSourceWorkPage(0, 0)

        assertEquals(master, page.url)
        assertEquals(master, page.fullUrl)
    }

    @Test
    fun missingOriginalLeavesOriginalUrlBlankSoSaveFallsBackOneStep() {
        val page = PixivPageUrls(small = small, regular = master).toSourceWorkPage(0, 0)

        assertEquals("", page.originalUrl)
        assertEquals(master, page.fullUrl)
    }

    @Test
    fun everyUrlMissingStillYieldsAnEmptyPageTheCallerCanFilterOut() {
        val page = PixivPageUrls().toSourceWorkPage(0, 0)

        assertEquals("", page.url)
        assertEquals("", page.fullUrl)
    }
}
