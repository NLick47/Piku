package com.piku.client.data.repository

import com.piku.client.data.local.FavoriteEntity
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteRowForTest {

    private val work = Work(
        id = 123L,
        authorId = 7L,
        authorName = "作者",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "新标题",
        thumbnailUrl = "https://x/new.jpg",
        imageCount = 5,
        r18 = false,
        source = WorkSource.POIPIKU,
    )

    private fun existingRow(
        addedAt: Long = 1_000L,
        contentBackedUp: Boolean = true,
        cloudSynced: Boolean = true,
    ) = FavoriteEntity(
        source = WorkSource.POIPIKU,
        workId = "123",
        authorId = 7L,
        title = "旧标题",
        authorName = "作者",
        thumbnailUrl = "https://x/old.jpg",
        authorAvatarUrl = null,
        imageCount = 1,
        r18 = false,
        addedAt = addedAt,
        contentBackedUp = contentBackedUp,
        cloudSynced = cloudSynced,
    )

    @Test
    fun existingRowKeepsAddedAtAndLocalFlags() {
        val row = favoriteRowFor(work, existingRow(), newAddedAt = 9_999L)

        assertEquals("重新收藏改写了首次收藏时间", 1_000L, row.addedAt)
        assertTrue("内容备份标记被抹掉了", row.contentBackedUp)
        assertTrue("云端镜像标记被抹掉了", row.cloudSynced)
    }

    @Test
    fun existingRowStillRefreshesWorkFields() {
        val row = favoriteRowFor(work, existingRow(), newAddedAt = 9_999L)

        assertEquals("新标题", row.title)
        assertEquals("https://x/new.jpg", row.thumbnailUrl)
        assertEquals(5, row.imageCount)
    }

    @Test
    fun newRowTakesGivenAddedAt() {
        val row = favoriteRowFor(work, existing = null, newAddedAt = 9_999L)

        assertEquals(9_999L, row.addedAt)
        assertEquals("123", row.workId)
        assertFalse(row.cloudSynced)
    }
}
