package com.piku.client.data.repository

import com.piku.client.data.remote.pixiv.PixivAppIllust
import com.piku.client.data.remote.pixiv.PixivAppImageUrls
import com.piku.client.data.remote.pixiv.PixivAppProfileImages
import com.piku.client.data.remote.pixiv.PixivAppUser
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixivRecommendedMappingTest {

    private val large = "https://i.pximg.net/c/600x1200_90/img-master/a_p0_master1200.jpg"
    private val medium = "https://i.pximg.net/c/540x540_70/img-master/a_p0_master1200.jpg"
    private val square = "https://i.pximg.net/c/360x360_70/img-master/a_p0_square1200.jpg"

    private fun illust(
        id: String = "112233",
        urls: PixivAppImageUrls = PixivAppImageUrls(squareMedium = square, medium = medium, large = large),
        user: PixivAppUser = PixivAppUser(id = "99", name = "画师", profileImageUrls = PixivAppProfileImages("头像")),
        width: Int = 1200,
        height: Int = 1800,
        xRestrict: Int = 0,
        pageCount: Int = 1,
    ) = PixivAppIllust(
        id = id,
        title = "标题",
        imageUrls = urls,
        user = user,
        pageCount = pageCount,
        width = width,
        height = height,
        xRestrict = xRestrict,
    )

    @Test
    fun thumbnailPicksTheUncroppedTierSoPortraitWorksStayPortrait() {
        assertEquals(large, illust().toWork()?.thumbnailUrl)
    }

    @Test
    fun croppedTiersAreOnlyAFallbackWhenLargeIsMissing() {
        val work = illust(urls = PixivAppImageUrls(squareMedium = square, medium = medium)).toWork()

        assertEquals(medium, work?.thumbnailUrl)
    }

    @Test
    fun squareIsTheLastResortSoACardIsNeverBlank() {
        val work = illust(urls = PixivAppImageUrls(squareMedium = square)).toWork()

        assertEquals(square, work?.thumbnailUrl)
    }

    @Test
    fun unparsableIdIsDroppedRatherThanTurningIntoCardZero() {
        assertNull(illust(id = "not-a-number").toWork())
    }

    @Test
    fun workWithoutAnyImageIsDropped() {
        assertNull(illust(urls = PixivAppImageUrls()).toWork())
    }

    @Test
    fun xRestrictAboveZeroIsR18() {
        assertEquals(false, illust(xRestrict = 0).toWork()?.r18)
        assertEquals(true, illust(xRestrict = 1).toWork()?.r18)
        assertEquals(true, illust(xRestrict = 2).toWork()?.r18)
    }

    @Test
    fun sizeAndAuthorFollowTheCardSoItCanLayOutAndShowTheAuthor() {
        val work = illust(width = 1200, height = 1800).toWork()!!

        assertEquals(1200, work.thumbWidth)
        assertEquals(1800, work.thumbHeight)
        assertEquals(99L, work.authorId)
        assertEquals("画师", work.authorName)
        assertEquals("头像", work.authorAvatarUrl)
        assertEquals(WorkSource.PIXIV, work.source)
    }

    @Test
    fun blankAvatarIsLeftNullInsteadOfAnEmptyImageRequest() {
        val work = illust(user = PixivAppUser(id = "99", name = "画师")).toWork()

        assertNull(work?.authorAvatarUrl)
    }

    @Test
    fun sanityGatedPlaceholderEntryPassesThrough() {
        // pixiv 对审查拦下的作品回匿名条目：缩略图是占位图 名字头像全空
        // 客户端不替 pixiv 过滤，原样透出（点击进详情由可见性接口自然给终态）
        val placeholder = "https://s.pximg.net/common/images/limit_sanity_level_360.png"
        val gated = illust(
            urls = PixivAppImageUrls(squareMedium = placeholder, medium = placeholder, large = placeholder),
            user = PixivAppUser(
                id = "99",
                name = "",
                profileImageUrls = PixivAppProfileImages("https://s.pximg.net/common/images/no_profile.png"),
            ),
        )

        val work = gated.toWork()
        assertEquals(99L, work?.authorId)
        assertEquals(placeholder, work?.thumbnailUrl)
        assertEquals("", work?.authorName)
    }
}
