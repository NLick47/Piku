package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class UserPageParserTest {


    private fun fixture(name: String): String {
        val res = javaClass.classLoader?.getResource(name)?.readText()
        assumeTrue("fixture $name 缺失，跳过（快照仅本地）", res != null)
        return res!!
    }

    @Test
    fun `parse with header banner extracts all fields`() {
        val info = UserPageParser.parse(fixture("userpage_with_header.html"))

        assertEquals("かを𝐏𝐨𝐢𝐩𝐢𝐤𝐮", info.userName)
        assertEquals(
            "https://cdn.poipiku.com/012108277/profile_20250904184853.jpeg",
            info.avatarUrl,
        )
        assertEquals(
            "https://cdn.poipiku.com/012108277/header_20250904185237.jpeg_640.jpg",
            info.headerUrl,
        )
        assertEquals(15, info.workCount)
        // 该 fixture 为匿名抓取（无登录态），关注按钮无 Selected
        assertEquals(false, info.followed)
        // 无 Poipass 背景规则时应为 null
        assertNull(info.bgColorHex)
        assertNull(info.bgImageUrl)
    }

    @Test
    fun `parse followed user page detects Selected class`() {
        val info = UserPageParser.parse(fixture("userpage_followed.html"))

        assertEquals("1403某", info.userName)
        assertEquals(true, info.followed)
        assertEquals(7, info.workCount)
    }

    @Test
    fun `parse without header banner leaves headerUrl null`() {
        val info = UserPageParser.parse(fixture("userpage_no_header.html"))

        assertNull(info.headerUrl)
        assertNull(info.avatarUrl) // 默认头像 default_user.jpg 应被过滤
        assertEquals("shikadarou", info.userName)
        assertEquals(40, info.workCount)
        assertEquals(false, info.followed)
    }

    @Test
    fun `parse extracts poipass page background rules from style block`() {
        val html = """
            <html><head>
            <meta name="twitter:image" content="https://cdn.poipiku.com/000000001/profile_20260101000000.jpeg" />
            <meta property="og:title" content="テストのポイピク | イラストとか箱「ポイピク」" />
            <meta property="og:description" content="テストはポイピクに3枚のイラストとかをポイポイしています。" />
            <style>
            .CardInfoDlgTitle{padding: 10px 0 0 0;}
            .UserInfo {background-image: url('https://cdn.poipiku.com/000000001/header_20260101000000.jpeg_640.jpg');}
            body {background: #FFE158;}
            </style>
            </head><body></body></html>
        """.trimIndent()

        val info = UserPageParser.parse(html)

        assertEquals(
            "https://cdn.poipiku.com/000000001/header_20260101000000.jpeg_640.jpg",
            info.headerUrl,
        )
        assertEquals("#FFE158", info.bgColorHex)
        assertNull(info.bgImageUrl)
        assertEquals(3, info.workCount)
    }

    @Test
    fun `parse extracts page background image and ignores header image`() {
        val html = """
            <html><head>
            <meta property="og:title" content="Xのポイピク | イラストとか箱「ポイピク」" />
            <meta property="og:description" content="Xはポイピクに1枚のイラストとかをポイポイしています。" />
            <style>
            .UserInfo {background-image: url('https://cdn.poipiku.com/000000002/header_20260101000000.png_640.jpg');}
            body {background-image: url('https://cdn.poipiku.com/000000002/bg_20260101000000.jpeg_640.jpg');}
            </style>
            </head><body></body></html>
        """.trimIndent()

        val info = UserPageParser.parse(html)

        assertEquals(
            "https://cdn.poipiku.com/000000002/header_20260101000000.png_640.jpg",
            info.headerUrl,
        )
        assertEquals(
            "https://cdn.poipiku.com/000000002/bg_20260101000000.jpeg_640.jpg",
            info.bgImageUrl,
        )
        assertNull(info.bgColorHex)
        assertEquals("X", info.userName)
        assertEquals(1, info.workCount)
    }

    @Test
    fun `parse handles page with no meta tags`() {
        val info = UserPageParser.parse("<html><body>plain</body></html>")

        assertNull(info.userName)
        assertNull(info.avatarUrl)
        assertNull(info.headerUrl)
        assertNull(info.workCount)
        assertNull(info.bgColorHex)
        assertNull(info.bgImageUrl)
    }

    // 以下用内联 HTML：resources 里的快照不在仓库，fixture 用例在本机之外会被 assumeTrue 跳过

    @Test
    fun `parseDisplayName prefers og title and strips the poipiku suffix`() {
        val html = """<meta property="og:title" content="サファイアのポイピク | イラストとか箱「ポイピク」">"""

        assertEquals("サファイア", UserPageParser.parseDisplayName(html))
    }

    @Test
    fun `parseDisplayName falls back to owner h2`() {
        val html = """<h2 class="IllustUserName">国家二级保护植被</h2>"""

        assertEquals("国家二级保护植被", UserPageParser.parseDisplayName(html))
    }

    @Test
    fun `parseDisplayName falls back to page title`() {
        val html = """<title>pipaのポイピク | イラストとか箱「ポイピク」</title>"""

        assertEquals("pipa", UserPageParser.parseDisplayName(html))
    }

    @Test
    fun `parseDisplayName falls back to avatar alt`() {
        val html = """<img class="IllustUserThumb" src="x.jpg" alt="サファイア">"""

        assertEquals("サファイア", UserPageParser.parseDisplayName(html))
    }

    @Test
    fun `parseDisplayName ignores not-found page title`() {
        val html = """<title>ご指定のページが見つかりません</title>"""

        assertNull(UserPageParser.parseDisplayName(html))
    }

    /**
     * 站点首页的 og:title 是「イラストとか箱「ポイピク」」，没有「のポイピク」后缀。
     * 没有这道守卫的话，非用户页的 og:title 会被当成昵称，而且 refreshUserProfile
     * 会把它持久化进抽屉，一直挂着直到下次刷新成功
     */
    @Test
    fun `parseDisplayName ignores og title without the poipiku suffix`() {
        val siteTop = """<meta property="og:title" content="イラストとか箱「ポイピク」">"""

        assertNull(UserPageParser.parseDisplayName(siteTop))
    }

    @Test
    fun `parseDisplayName decodes html entities`() {
        val html =
            """<meta property="og:title" content="A&amp;B&#39;sのポイピク | イラストとか箱「ポイピク」">"""

        assertEquals("A&B's", UserPageParser.parseDisplayName(html))
    }

    @Test
    fun `parseDisplayName returns null for blank html`() {
        assertNull(UserPageParser.parseDisplayName(""))
        assertNull(UserPageParser.parseDisplayName("""<h2 class="IllustUserName">  </h2>"""))
    }

    /** 页主页与用户主页共用同一份昵称实现，别让它们再分叉 */
    @Test
    fun `parse uses the same nickname source`() {
        val html = """
            <meta property="og:title" content="サファイアのポイピク | イラストとか箱「ポイピク」">
            <h2 class="IllustUserName">别人</h2>
        """.trimIndent()

        assertEquals(UserPageParser.parseDisplayName(html), UserPageParser.parse(html).userName)
        assertEquals("サファイア", UserPageParser.parse(html).userName)
    }
}
