package com.piku.client.ui.home

import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedSnapshotCacheTest {

    private fun work(id: Long, thumb: String = "https://img/$id.jpg") = Work(
        id = id,
        authorId = id * 100,
        authorName = "author$id",
        authorAvatarUrl = null,
        categoryCd = 0,
        categoryName = "all",
        title = "title$id",
        thumbnailUrl = thumb,
        imageCount = 1,
        r18 = false,
    )

    private fun key(tab: FeedTab, tag: String? = null) =
        FeedKey(tab, PoipikuCategory.ALL, tag)

    private fun snapshot(vararg works: Work, page: Int = 0, endReached: Boolean = false) =
        FeedSnapshot(works.toList(), page, endReached)

    @Test
    fun putGetRoundtrip() {
        val cache = FeedSnapshotCache()
        val k = key(FeedTab.HOT)
        val s = snapshot(work(1), work(2), page = 3, endReached = true)

        cache[k] = s

        assertEquals(s, cache[k])
    }

    @Test
    fun missReturnsNull() {
        val cache = FeedSnapshotCache()

        assertNull(cache[key(FeedTab.LATEST)])
    }

    @Test
    fun evictsEldestBeyondCapacity() {
        val cache = FeedSnapshotCache(maxSize = 2)
        val hot = key(FeedTab.HOT)
        val latest = key(FeedTab.LATEST)
        val follow = key(FeedTab.FOLLOW)
        cache[hot] = snapshot(work(1))
        cache[latest] = snapshot(work(2))
        cache[follow] = snapshot(work(3))

        assertNull(cache[hot])
        assertEquals(snapshot(work(2)), cache[latest])
        assertEquals(snapshot(work(3)), cache[follow])
    }

    @Test
    fun getRefreshesLruOrder() {
        val cache = FeedSnapshotCache(maxSize = 2)
        val hot = key(FeedTab.HOT)
        val latest = key(FeedTab.LATEST)
        val follow = key(FeedTab.FOLLOW)
        cache[hot] = snapshot(work(1))
        cache[latest] = snapshot(work(2))
        cache[hot] // 访问 hot，使其比 latest 更“新”
        cache[follow] = snapshot(work(3))

        assertEquals(snapshot(work(1)), cache[hot])
        assertNull(cache[latest])
        assertEquals(snapshot(work(3)), cache[follow])
    }

    @Test
    fun clearEmptiesAll() {
        val cache = FeedSnapshotCache()
        cache[key(FeedTab.HOT)] = snapshot(work(1))

        cache.clear()

        assertNull(cache[key(FeedTab.HOT)])
    }

    @Test
    fun updateThumbnailReplacesAcrossEntries() {
        val cache = FeedSnapshotCache()
        val hot = key(FeedTab.HOT)
        val latest = key(FeedTab.LATEST, tag = "pixiv")
        cache[hot] = snapshot(work(1), work(2))
        cache[latest] = snapshot(work(2), work(3))

        cache.updateThumbnail(2, "https://new/2.jpg")

        assertEquals(
            snapshot(work(1), work(2, thumb = "https://new/2.jpg")),
            cache[hot],
        )
        assertEquals(
            snapshot(work(2, thumb = "https://new/2.jpg"), work(3)),
            cache[latest],
        )
    }

    @Test
    fun updateThumbnailKeepsOtherFields() {
        val cache = FeedSnapshotCache()
        val original = work(5).copy(title = "kept", r18 = true, loginRequired = true)
        cache[key(FeedTab.RANDOM)] = snapshot(original)

        cache.updateThumbnail(5, "https://new/5.jpg")

        val updated = cache[key(FeedTab.RANDOM)]!!.works.single()
        assertEquals("kept", updated.title)
        assertEquals(true, updated.r18)
        assertEquals(true, updated.loginRequired)
    }

    @Test
    fun updateThumbnailNoMatchIsNoOp() {
        val cache = FeedSnapshotCache()
        val k = key(FeedTab.HOT)
        val s = snapshot(work(1))
        cache[k] = s

        cache.updateThumbnail(999, "https://new/x.jpg")

        assertEquals(s, cache[k])
    }
}
