package com.piku.client.data.repository

import com.piku.client.domain.model.WorkDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailLoadPolicyTest {

    private val mainImage = "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg"

    private fun detail(
        imageUrls: List<String> = listOf(mainImage),
        novelText: String = "",
        passwordProtected: Boolean = false,
    ): WorkDetail = WorkDetail(
        title = "title",
        description = "desc",
        authorName = "author",
        authorAvatarUrl = "",
        categoryCd = 0,
        categoryName = "",
        imageUrls = imageUrls,
        tags = listOf("#tag"),
        r18 = false,
        novelText = novelText,
        passwordProtected = passwordProtected,
    )

    /** 常态作品：HTML 已给出真实主图，首屏只等这一个往返 */
    @Test
    fun paintsPartialForWorkWithRealMainImage() {
        assertTrue(DetailLoadPolicy.canPaintPartialFromHtml(detail()))

        val partial = DetailLoadPolicy.partialFromHtml(detail())
        assertEquals(listOf(mainImage), partial?.imageUrls)
        assertEquals("title", partial?.title)
    }

    /**
     * 纯文本作品与门卡作品（登录墙/关注墙）的 HTML 里没有图，图区要展示的正文
     * 或门卡态同样来自 append——提前画只会得到一个"暂无图片"的空壳。
     */
    @Test
    fun skipsPartialWhenHtmlHasNoImage() {
        assertFalse(DetailLoadPolicy.canPaintPartialFromHtml(detail(imageUrls = emptyList())))
        assertNull(DetailLoadPolicy.partialFromHtml(detail(imageUrls = emptyList())))
    }

    /**
     * 主图是占位图时不能提前画：R-18 作品（开关已开、走常态分支）的 HTML 主图就是
     * R-18 占位图，真实图在 append 里；先画出来会让占位图闪一下。
     */
    @Test
    fun skipsPartialWhenMainImageIsPlaceholder() {
        val placeholders = listOf(
            "https://cdn.poipiku.com/img/R-18.png_640.jpg",
            "https://cdn.poipiku.com/img/warning.png_640.jpg",
            "https://cdn.poipiku.com/img/publish_login.png_640.jpg",
            "https://cdn.poipiku.com/img/publish_follower.png_640.jpg",
            "https://cdn.poipiku.com/img/publish_pass.png_640.jpg",
        )
        placeholders.forEach { placeholder ->
            assertFalse(
                "$placeholder 是占位图，不该提前画",
                DetailLoadPolicy.canPaintPartialFromHtml(detail(imageUrls = listOf(placeholder))),
            )
        }
    }

    /**
     * 密码作品一律不提前画：用得是真实主图构造（服务端锁页 HTML 目前给的是 publish_pass
     * 占位图），保证墙后内容只能由 append 的答复决定，不会先出图再要密码。
     */
    @Test
    fun skipsPartialForPasswordProtectedWork() {
        assertFalse(
            DetailLoadPolicy.canPaintPartialFromHtml(detail(passwordProtected = true)),
        )
        assertNull(DetailLoadPolicy.partialFromHtml(detail(passwordProtected = true)))
    }

    /** 一张真实图 + 一张占位图混在一起（append 前的中间态）：仍然不提前画 */
    @Test
    fun skipsPartialWhenAnyImageIsPlaceholder() {
        assertFalse(
            DetailLoadPolicy.canPaintPartialFromHtml(
                detail(imageUrls = listOf(mainImage, "https://cdn.poipiku.com/img/warning.png_640.jpg")),
            ),
        )
    }

    /** 部分态不带正文：正文是 append 的产物，HTML 阶段还没有，画出来只会是空的 */
    @Test
    fun partialCarriesNoNovelText() {
        assertEquals("", DetailLoadPolicy.partialFromHtml(detail(novelText = "正文"))?.novelText)
    }
}
