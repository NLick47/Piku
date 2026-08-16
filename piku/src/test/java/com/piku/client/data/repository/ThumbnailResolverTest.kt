package com.piku.client.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailResolverTest {

    @Test
    fun isPlaceholderImageDetectsAllPlaceholderKinds() {
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/publish_login.png_640.jpg"))
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/publish_pass.png_360.jpg"))
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/warning.png_360.jpg"))
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/R-18.png_640.jpg"))
    }

    @Test
    fun isPlaceholderImageRejectsRealWorkImages() {
        assertFalse(
            ThumbnailResolver.isPlaceholderImage(
                "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg",
            ),
        )
        assertFalse(
            ThumbnailResolver.isPlaceholderImage(
                "https://cdn.poipiku.com/014194276/013349362_wCpodr7j2.jpeg_640.jpg",
            ),
        )
    }

    @Test
    fun mergeKeepsRealImagesInOrderAndDropsPlaceholders() {
        val detail = listOf(
            "https://cdn.poipiku.com/img/publish_login.png_640.jpg",
            "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg",
        )
        val append = listOf(
            "https://cdn.poipiku.com/013955571/013349459_030732415_1NkRpwujF.png_640.jpg",
            "https://cdn.poipiku.com/img/publish_pass.png_360.jpg",
        )

        assertEquals(
            listOf(
                "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg",
                "https://cdn.poipiku.com/013955571/013349459_030732415_1NkRpwujF.png_640.jpg",
            ),
            ThumbnailResolver.mergeWorkImages(detail, append),
        )
    }

    @Test
    fun mergeDeduplicatesAcrossSources() {
        val detail = listOf("https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg")
        val append = listOf("https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg")

        assertEquals(detail, ThumbnailResolver.mergeWorkImages(detail, append))
    }

    @Test
    fun mergeFallsBackToDetailImagesWhenOnlyPlaceholders() {
        val detail = listOf("https://cdn.poipiku.com/img/publish_login.png_640.jpg")
        val append = listOf("https://cdn.poipiku.com/img/publish_pass.png_360.jpg")

        assertEquals(detail, ThumbnailResolver.mergeWorkImages(detail, append))
    }

    @Test
    fun mergeWithEmptyAppendKeepsRealDetailImages() {
        val detail = listOf("https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg")

        assertEquals(detail, ThumbnailResolver.mergeWorkImages(detail, emptyList()))
    }
}