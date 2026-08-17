package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FollowFeedParserTest {

    private fun readResource(name: String): String {
        val res = javaClass.classLoader?.getResource(name)?.readText()
        assumeTrue("fixture $name 缺失，跳过（快照仅本地）", res != null)
        return res!!
    }

    @Test
    fun `parses timeline items from follow page`() {
        val works = FollowFeedParser.parse(readResource("followfeed.html"))
        assertEquals(3, works.size)

        // 条目1：R18、单图、中文描述
        val first = works[0]
        assertEquals(13349091L, first.id)
        assertEquals(12555920L, first.authorId)
        assertEquals("1403某", first.authorName)
        assertEquals("https://cdn.poipiku.com/012555920/profile_20251026224012.jpeg_120.jpg", first.authorAvatarUrl)
        assertEquals(4, first.categoryCd)
        assertEquals("涂鸦", first.categoryName)
        assertEquals("玫瑰仙子来信\n【节能体】\n罗吒x你 罗吒乙女 罗小黑哪吒x你", first.title)
        assertTrue(first.thumbnailUrl.contains("013349091"))
        assertEquals(1, first.imageCount)
        assertTrue(first.r18)

        // 条目2：追加图数量 = +4 + 主图 = 5
        val second = works[1]
        assertEquals(13335122L, second.id)
        assertEquals(5, second.imageCount)
        assertTrue(second.r18)

        // 条目3：非 R18、日文页面（语言无关的追加图提示）
        val third = works[2]
        assertEquals(13284829L, third.id)
        assertEquals(90210L, third.authorId)
        assertEquals("田中太郎", third.authorName)
        assertEquals(1, third.categoryCd)
        assertEquals("らくがき", third.categoryName)
        assertEquals(4, third.imageCount)
        assertFalse(third.r18)
        assertFalse(third.warning)
        assertFalse(third.loginRequired)
    }

    @Test
    fun `footer decoy detail call is ignored`() {
        // 尾部噪音里的 showIllustDetail(999, 888, -1) 缺少作者/描述/配图，不应产生条目
        val works = FollowFeedParser.parse(readResource("followfeed.html"))
        assertTrue(works.none { it.id == 888L })
    }

    @Test
    fun `returns empty for no-follow welcome page`() {
        val html = """
            <div id="InfoMsg" style="display:block;">
                欢迎来到 POIPIKU。<br>这里会显示最新信息。<br>
            </div>
        """.trimIndent()
        assertTrue(FollowFeedParser.parse(html).isEmpty())
    }

    @Test
    fun `detects login page`() {
        val loginPage = """
            <title>ログイン | イラストとか箱「ポイピク」</title>
            <form action="/f/LoginUserF.jsp" method="post">...</form>
        """.trimIndent()
        assertTrue(FollowFeedParser.isLoginPage(loginPage))
        assertFalse(FollowFeedParser.isLoginPage("<html><body>no form here</body></html>"))
    }

    @Test
    fun `warning thumbnails are flagged`() {
        val html = """
            <div class="IllustItem Upload" id="IllustItem_1"><div class="IllustItemUser"><a class="IllustItemUserThumb" href="/7/" style="background-image:url('https://cdn.poipiku.com/0007/profile.jpg')"></a><h2 class="IllustItemUserName"><a href="/7/">a</a></h2></div><h1 id="IllustItemDesc_1" class="IllustItemDesc">t</h1>
            <a class="IllustItemThumb" href="javascript:void(0)" onclick="showIllustDetail(7, 1, -1)"><img class="IllustItemThumbImg" src="https://cdn.poipiku.com/img/warning_640.jpg"></a></div>
        """.trimIndent()
        val work = FollowFeedParser.parse(html).single()
        assertTrue(work.warning)
        assertFalse(work.r18)
        assertEquals("https://cdn.poipiku.com/0007/profile.jpg", work.authorAvatarUrl)
    }
}
