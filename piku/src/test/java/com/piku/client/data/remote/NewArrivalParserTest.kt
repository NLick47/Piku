package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewArrivalParserTest {

    @Test
    fun `parses work block with author category title and thumbnail`() {
        val works = NewArrivalParser.parse(workBlock())

        assertEquals(1, works.size)
        val work = works.single()
        assertEquals(999L, work.id)
        assertEquals(14189264L, work.authorId)
        assertEquals("サファイア", work.authorName)
        assertEquals("https://cdn.poipiku.com/014189264/profile_x.png_120.jpg", work.authorAvatarUrl)
        assertEquals(3, work.categoryCd)
        assertEquals("イラスト", work.categoryName)
        assertEquals("标题\n第二行", work.title)
        assertEquals("https://cdn.poipiku.com/014189264/999_Abc.png_640.jpg", work.thumbnailUrl)
        assertEquals(3, work.imageCount)
        assertFalse(work.r18)
        assertFalse(work.isPrivate)
    }

    /** 缩略图路径决定这几个标记位（R-18 / 注意 / 需登录） */
    @Test
    fun `flags come from the thumbnail path`() {
        val r18 = NewArrivalParser.parse(workBlock(thumb = "/img/R-18_x.jpg")).single()
        val warning = NewArrivalParser.parse(workBlock(thumb = "/img/warning_x.jpg")).single()
        val login = NewArrivalParser.parse(workBlock(thumb = "/img/publish_login_x.jpg")).single()

        assertTrue(r18.r18)
        assertTrue(warning.warning)
        assertTrue(login.loginRequired)
    }

    @Test
    fun `non-public marker is detected`() {
        val html = workBlock(privateMarker = """<span class="Publish Private"></span>""")

        assertTrue(NewArrivalParser.parse(html).single().isPrivate)
    }

    /** マイボックス整页只有页主一个作者、块内不带作者区：用调用方给的资料回填 */
    @Test
    fun `falls back to the page owner when the block has no author`() {
        val html = workBlock(withAuthor = false)

        val withFallback = NewArrivalParser.parse(
            html = html,
            authorFallbackId = 14189264L,
            authorFallbackName = "页主",
            authorFallbackAvatarUrl = "https://cdn.poipiku.com/fallback.jpg",
        ).single()

        assertEquals(14189264L, withFallback.authorId)
        assertEquals("页主", withFallback.authorName)
        assertEquals("https://cdn.poipiku.com/fallback.jpg", withFallback.authorAvatarUrl)
        assertTrue("没给回填资料时该块按原行为丢弃", NewArrivalParser.parse(html).isEmpty())
    }

    /** 块内自带作者时以块内为准，不用回填 */
    @Test
    fun `block author wins over the fallback`() {
        val work = NewArrivalParser.parse(
            html = workBlock(),
            authorFallbackId = 1L,
            authorFallbackName = "页主",
        ).single()

        assertEquals(14189264L, work.authorId)
        assertEquals("サファイア", work.authorName)
    }

    /** 缺关键字段的块要被丢掉，不能产出半成品 */
    @Test
    fun `drops blocks missing category or work link`() {
        assertTrue(NewArrivalParser.parse(workBlock(category = "")).isEmpty())
        assertTrue(NewArrivalParser.parse(workBlock(workLink = "")).isEmpty())
        assertTrue(NewArrivalParser.parse(workBlock(thumb = "")).isEmpty())
    }

    @Test
    fun `cleanText turns br into newline and decodes entities`() {
        assertEquals("a\nb & c", NewArrivalParser.cleanText("a<br>b &amp; c"))
    }

    // ---- 工具 ----

    private fun workBlock(
        thumb: String = "https://cdn.poipiku.com/014189264/999_Abc.png_640.jpg",
        category: String = """<a class="CategoryInfo" href="/NewArrivalPcV.jsp?CD=3"><span class="Category C3">イラスト</span></a>""",
        workLink: String = """<a class="IllustInfo" href="/14189264/999.html"><span class="IllustInfoDesc">标题<br>第二行</span></a>""",
        withAuthor: Boolean = true,
        privateMarker: String = "",
    ): String {
        val author = if (withAuthor) {
            """
            <a class="IllustUser" href="/14189264/">
              <img class="IllustUserThumb" src="https://cdn.poipiku.com/014189264/profile_x.png_120.jpg" alt="サファイア">
            </a>
            <h2 class="IllustUserName">サファイア</h2>
            """.trimIndent()
        } else {
            ""
        }
        val thumbTag = if (thumb.isEmpty()) {
            ""
        } else {
            """<img class="IllustThumbImgPic" src="$thumb">"""
        }
        return """
            <div class="IllustThumb">
              $author
              $category
              <i class="far fa-images"></i> 3</span>
              $workLink
              $thumbTag
              $privateMarker
            </div>
        """.trimIndent()
    }
}
