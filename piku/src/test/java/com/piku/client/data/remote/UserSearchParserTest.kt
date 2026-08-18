package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSearchParserTest {

    private val sampleHtml = """
        <html><head><title>絵師検索</title></head><body>
        <a class="UserInfo Thumb" href="/10000001/">
        <span class="UserInfoUserThumb" style="background-image:url('https://cdn.poipiku.com/010000001/profile_1.jpeg_120.jpg')"></span>
        <span class="UserInfoUserName">テスト絵師1</span></a>
        <span class="UserInfoCmdFollow" >フォローする</span>
        <a class="UserInfo Thumb" href="/10000002/">
        <span class="UserInfoUserThumb" style="background-image:url('https://cdn.poipiku.com/010000002/profile_2.jpeg_120.jpg')"></span>
        <span class="UserInfoUserName">テスト絵師2</span></a>
        <span class="UserInfoCmdFollow Selected" >フォロー解除</span>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses user cards from keyword search page`() {
        val users = UserSearchParser.parse(sampleHtml)
        assertEquals(2, users.size)

        val first = users[0]
        assertEquals(10000001L, first.userId)
        assertEquals("テスト絵師1", first.name)
        assertEquals("https://cdn.poipiku.com/010000001/profile_1.jpeg_120.jpg", first.avatarUrl)
        assertFalse(first.followed)
    }

    @Test
    fun `parses followed state from Selected marker`() {
        val users = UserSearchParser.parse(sampleHtml)
        assertTrue(users[1].followed)
    }

    @Test
    fun `returns empty when no user cards present`() {
        val html = """
            <html><body><div>该关键词未找到相关作者</div></body></html>
        """.trimIndent()
        assertTrue(UserSearchParser.parse(html).isEmpty())
    }

    @Test
    fun `returns empty for blank response`() {
        assertTrue(UserSearchParser.parse("\n".repeat(21)).isEmpty())
    }

    @Test
    fun `skips decoy user links outside user cards`() {
        val html = """
            <a class="IllustUser" href="/99999999/">decoy</a>
            $sampleHtml
        """.trimIndent()
        val users = UserSearchParser.parse(html)
        assertEquals(2, users.size)
        assertTrue(users.none { it.userId == 99999999L })
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
        assertTrue(UserSearchParser.isLoginPage(loginPage))
        assertFalse(UserSearchParser.isLoginPage(sampleHtml))
    }

    @Test
    fun `keeps null avatar for card without image`() {
        val html = """
            <a class="UserInfo Thumb" href="/10000003/">
            <span class="UserInfoUserName">无头像用户</span></a>
        """.trimIndent()
        val users = UserSearchParser.parse(html)
        assertEquals(1, users.size)
        assertEquals(10000003L, users[0].userId)
        assertNull(users[0].avatarUrl)
    }
}