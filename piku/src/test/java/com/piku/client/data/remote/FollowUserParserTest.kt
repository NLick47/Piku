package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FollowUserParserTest {


    private fun readResource(name: String): String {
        val res = javaClass.classLoader?.getResource(name)?.readText()
        assumeTrue("fixture $name 缺失，跳过（快照仅本地）", res != null)
        return res!!
    }

    @Test
    fun `parses follow users from setting page`() {
        val users = FollowUserParser.parse(readResource("followusers.html"))
        assertEquals(2, users.size)

        val first = users[0]
        assertEquals(12108277L, first.userId)
        assertEquals("かを𝐏𝐨𝐢𝐩𝐢𝐤𝐮", first.name)
        assertEquals("https://cdn.poipiku.com/012108277/profile_20250904184853.jpeg_120.jpg", first.avatarUrl)

        val second = users[1]
        assertEquals(12555920L, second.userId)
        assertEquals("1403某", second.name)
        assertEquals("https://cdn.poipiku.com/012555920/profile_20251026224012.jpeg_120.jpg", second.avatarUrl)
    }

    @Test
    fun `extracts total from inline js`() {
        val html = readResource("followusers.html")
        assertEquals(2, FollowUserParser.parseTotal(html))
    }

    @Test
    fun `returns null total when no follow script present`() {
        assertNull(FollowUserParser.parseTotal("<html><body>nothing here</body></html>"))
    }

    @Test
    fun `returns empty for blank response`() {
        // f/FollowListF.jsp 匿名调用返回 21 个空行（无 JSON/HTML）
        val blank = "\n".repeat(21)
        assertTrue(FollowUserParser.parse(blank).isEmpty())
    }

    @Test
    fun `skips decoy user links outside follow list`() {
        // 页面其他区域的普通链接（非 UserInfo Thumb 块）不应被误解析
        val html = """
            <a class="IllustUser" href="/99999999/">someone</a>
            ${readResource("followusers.html")}
        """.trimIndent()
        val users = FollowUserParser.parse(html)
        assertEquals(2, users.size)
        assertTrue(users.none { it.userId == 99999999L })
    }

    // 下面用内联 HTML：上面依赖快照的用例在本机之外一律被 assumeTrue 跳过

    @Test
    fun `parses follow users from inline html`() {
        val users = FollowUserParser.parse(
            """
            <a class="UserInfo Thumb" href="/14189264/" style="">
              <span class="UserInfoUserThumb" style="background-image:url('https://cdn.poipiku.com/014189264/profile_x.png_120.jpg')"></span>
              <span class="UserInfoUserName">サファイア</span>
            </a>
            <a class="UserInfo Thumb" href="/13955571/" style="">
              <span class="UserInfoUserThumb" style="background-image:url('')"></span>
              <span class="UserInfoUserName">植被</span>
            </a>
            """.trimIndent(),
        )

        assertEquals(listOf(14189264L, 13955571L), users.map { it.userId })
        assertEquals("サファイア", users[0].name)
        assertEquals("https://cdn.poipiku.com/014189264/profile_x.png_120.jpg", users[0].avatarUrl)
        assertNull("头像 url 为空视为没有头像", users[1].avatarUrl)
    }

    @Test
    fun `extracts total from an inline script`() {
        assertEquals(2, FollowUserParser.parseTotal("<script>var TOTAL=2;</script>"))
    }

    @Test
    fun `skips user blocks without a name`() {
        val html = """
            <a class="UserInfo Thumb" href="/14189264/" style="">
              <span class="UserInfoUserName"></span>
            </a>
        """.trimIndent()

        assertTrue("没有昵称的块要丢掉，不能产出半成品", FollowUserParser.parse(html).isEmpty())
    }
}