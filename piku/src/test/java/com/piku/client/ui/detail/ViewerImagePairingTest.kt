package com.piku.client.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerImagePairingTest {

    @Test
    fun pairsThumbAndFullByIndex() {
        val state = DetailUiState(
            detail = baseDetail(imageUrls = listOf("t1", "t2", "t3")),
            fullImageUrls = listOf("f1", "f2", "f3"),
        )
        assertEquals(
            listOf(
                ViewerImage("t1", "f1"),
                ViewerImage("t2", "f2"),
                ViewerImage("t3", "f3"),
            ),
            state.viewerImages,
        )
    }

    @Test
    fun fallsBackToLastThumbWhenFullsLonger() {
        val state = DetailUiState(
            detail = baseDetail(imageUrls = listOf("t1")),
            fullImageUrls = listOf("f1", "f2"),
        )
        assertEquals(
            listOf(
                ViewerImage("t1", "f1"),
                ViewerImage("t1", "f2"),
            ),
            state.viewerImages,
        )
    }

    @Test
    fun leavesFullNullWhenNotLoaded() {
        val state = DetailUiState(detail = baseDetail(imageUrls = listOf("t1", "t2")))
        assertEquals(
            listOf(
                ViewerImage("t1", null),
                ViewerImage("t2", null),
            ),
            state.viewerImages,
        )
    }

    @Test
    fun emptyWhenNoDetail() {
        assertTrue(DetailUiState().viewerImages.isEmpty())
    }

    @Test
    fun emptyWhenNoThumbs() {
        val state = DetailUiState(
            detail = baseDetail(imageUrls = emptyList()),
            fullImageUrls = listOf("f1"),
        )
        assertTrue(state.viewerImages.isEmpty())
    }

    private fun baseDetail(imageUrls: List<String>) =
        com.piku.client.domain.model.WorkDetail(
            title = "",
            authorName = "",
            authorAvatarUrl = "",
            categoryCd = -1,
            categoryName = "",
            imageUrls = imageUrls,
            tags = emptyList(),
            r18 = false,
        )
}