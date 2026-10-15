package com.piku.client.core.source

import com.piku.client.R
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPoolTest {

    private class FakeSource(
        override val id: WorkSource,
        private val declaredPool: String? = null,
        override val listLayout: SourceListLayout = SourceListLayout.Grid,
    ) : ContentSource {
        override val libraryPool: String
            get() = declaredPool ?: super.libraryPool

        override val labelRes = R.string.home_source_poipiku
        override val feeds = emptyList<SourceFeed>()
        override val facets = emptyList<SourceFacetGroup>()

        override suspend fun page(
            feedId: String,
            facets: Map<String, String>,
            page: Int,
            scope: SourceScope?,
        ): Result<SourcePage> = Result.success(SourcePage(items = emptyList()))

        override fun open(work: Work): SourceWorkOpen = SourceWorkOpen.InAppViewer

        override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
            Result.success(emptyList())
    }

    private fun registry(vararg sources: ContentSource) = SourceRegistry(sources.toSet())

    @Test
    fun sourceWithoutDeclarationGetsItsOwnPool() {
        val pool = registry(FakeSource(WorkSource.AO3, listLayout = SourceListLayout.List))
            .libraryPoolOf(WorkSource.AO3)

        assertEquals(setOf(WorkSource.AO3), pool.sources)
    }

    @Test
    fun sourcesSharingAPoolSeeEachOther() {
        val registry = registry(
            FakeSource(WorkSource.POIPIKU, declaredPool = LIBRARY_POOL_MEDIA),
            FakeSource(WorkSource.PIXIV, declaredPool = LIBRARY_POOL_MEDIA),
            FakeSource(WorkSource.AO3, listLayout = SourceListLayout.List),
        )

        assertEquals(
            setOf(WorkSource.POIPIKU, WorkSource.PIXIV),
            registry.libraryPoolOf(WorkSource.POIPIKU).sources,
        )
        // 没声明共池的源不混进来：AO3 侧只有它自己
        assertEquals(setOf(WorkSource.AO3), registry.libraryPoolOf(WorkSource.AO3).sources)
    }

    @Test
    fun singleColumnNeedsTheWholePoolToBeText() {
        val mixed = registry(
            FakeSource(WorkSource.POIPIKU, declaredPool = LIBRARY_POOL_MEDIA),
            FakeSource(
                WorkSource.PIXIV,
                declaredPool = LIBRARY_POOL_MEDIA,
                listLayout = SourceListLayout.List,
            ),
        )
        assertFalse(mixed.libraryPoolOf(WorkSource.POIPIKU).singleColumn)

        val textOnly = registry(FakeSource(WorkSource.AO3, listLayout = SourceListLayout.List))
        assertTrue(textOnly.libraryPoolOf(WorkSource.AO3).singleColumn)
    }

    @Test
    fun gridPoolIsNotSingleColumn() {
        val pool = registry(FakeSource(WorkSource.POIPIKU)).libraryPoolOf(WorkSource.POIPIKU)

        assertFalse(pool.singleColumn)
    }

    @Test
    fun unregisteredSourceFallsBackToItself() {
        val pool = registry(FakeSource(WorkSource.POIPIKU)).libraryPoolOf(WorkSource.AO3)

        assertEquals(setOf(WorkSource.AO3), pool.sources)
        assertFalse(pool.singleColumn)
    }
}
