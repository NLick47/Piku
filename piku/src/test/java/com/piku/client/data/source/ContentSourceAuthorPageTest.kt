package com.piku.client.data.source

import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceFacetGroup
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.source.SourceWorkPage
import org.junit.Assert.assertNull
import org.junit.Test

class ContentSourceAuthorPageTest {

    private class BareSource : ContentSource {
        override val id = WorkSource.POIPIKU
        override val labelRes = R.string.home_source_poipiku
        override val feeds = emptyList<SourceFeed>()
        override val facets = emptyList<SourceFacetGroup>()

        override suspend fun page(feedId: String, facets: Map<String, String>, page: Int): Result<SourcePage> =
            Result.success(SourcePage(items = emptyList()))

        override fun open(work: Work): SourceWorkOpen = SourceWorkOpen.InAppViewer

        override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
            Result.success(emptyList())
    }

    @Test
    fun sourceWithoutAuthorPageDeclaresNothing() {
        val work = Work(
            id = 1L,
            authorId = 2L,
            authorName = "作者",
            authorAvatarUrl = null,
            categoryCd = -1,
            categoryName = "",
            title = "标题",
            thumbnailUrl = "",
            imageCount = 1,
            r18 = false,
        )

        assertNull(BareSource().authorPage(work))
    }
}
