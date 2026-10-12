package com.piku.client.ui.search

import com.piku.client.domain.source.SourceTrendingTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendingSkeletonTest {

    private val tag = SourceTrendingTag(name = "東方", thumbnailUrl = "https://example.test/a.jpg")

    @Test
    fun `slow request shows placeholder after grace`() {
        assertTrue(shouldShowTrendingSkeleton(trending = emptyList(), loading = true, hintReady = true))
    }

    @Test
    fun `fast request never shows placeholder`() {
        assertFalse(shouldShowTrendingSkeleton(trending = emptyList(), loading = true, hintReady = false))
    }

    @Test
    fun `data wins over placeholder`() {
        assertFalse(shouldShowTrendingSkeleton(trending = listOf(tag), loading = true, hintReady = true))
    }

    @Test
    fun `finished request shows empty state not placeholder`() {
        assertFalse(shouldShowTrendingSkeleton(trending = emptyList(), loading = false, hintReady = true))
    }
}
