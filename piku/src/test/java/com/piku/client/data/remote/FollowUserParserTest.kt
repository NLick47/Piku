package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `detects login page`() {
        val loginPage = """
            <html><body>
            <form method="post" action="/f/LoginUserF.jsp">
            <input name="EM" type="text">
            <input name="PW" type="password">
            </form>
            </body></html>
        """.trimIndent()
        assertTrue(FollowUserParser.isLoginPage(loginPage))
        assertFalse(FollowUserParser.isLoginPage(readResource("followusers.html")))
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
}