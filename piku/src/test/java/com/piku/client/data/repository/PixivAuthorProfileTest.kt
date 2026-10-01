package com.piku.client.data.repository

import com.piku.client.data.remote.pixiv.PixivUserDetailResponse
import com.piku.client.data.remote.pixiv.PixivUserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixivAuthorProfileTest {

    /** 缺字段必须保持 null：DTO 默认值改成 0，界面就会把「接口没给」显示成「一条都没有」 */
    @Test
    fun missingCountsStayNullInsteadOfZero() {
        val result = PixivUserDetailResponse(
            profile = PixivUserProfile(totalIllusts = 12),
        ).toAuthorProfile(requestedId = 1L)

        assertEquals(12, result.illustCount)
        assertNull(result.mangaCount)
        assertNull(result.bookmarkCount)
        assertNull(result.followCount)
    }

    @Test
    fun bookmarkCursorReadsMaxBookmarkId() {
        val next = "https://app-api.pixiv.net/v1/user/bookmarks/illust" +
            "?user_id=1&restrict=public&max_bookmark_id=9876543&filter=for_android"

        assertEquals(9876543L, pixivBookmarkCursor(next))
    }

    @Test
    fun bookmarkCursorIsNullWhenThereIsNoNextPage() {
        assertNull(pixivBookmarkCursor(null))
        assertNull(pixivBookmarkCursor(""))
        assertNull(pixivBookmarkCursor("https://app-api.pixiv.net/v1/user/bookmarks/illust?user_id=1"))
        assertNull(pixivBookmarkCursor("https://app-api.pixiv.net/v1/user/bookmarks/illust?max_bookmark_id=abc"))
    }
}
