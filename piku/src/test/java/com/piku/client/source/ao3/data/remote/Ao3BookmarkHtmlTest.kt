package com.piku.client.source.ao3.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ao3BookmarkHtmlTest {

    @Test
    fun `csrf 取 meta 里的令牌`() {
        val html = """<head><meta name="csrf-token" content="a1b2c3+d4" /></head>"""
        assertEquals("a1b2c3+d4", Ao3BookmarkHtml.csrfToken(html))
    }

    @Test
    fun `单 pseud 取隐藏域的 id`() {
        val html = """
            <form action="/works/123/bookmarks" method="post">
              <input type="hidden" name="bookmark[pseud_id]" id="bookmark_pseud_id" value="421337" />
            </form>
        """.trimIndent()
        assertEquals(421337L, Ao3BookmarkHtml.pseudId(html))
    }

    @Test
    fun `多 pseud 取被选中的那一项`() {
        val html = """
            <select name="bookmark[pseud_id]" title="choose pseud">
              <option value="11">main</option>
              <option selected="selected" value="22">alt</option>
            </select>
        """.trimIndent()
        assertEquals(22L, Ao3BookmarkHtml.pseudId(html))
    }

    @Test
    fun `本人书签 id 取 Edit 入口的 href`() {
        val html = """
            <li>
              <a class="bookmark_form_trigger_for_123" data-path="/bookmarks/987654/edit"
                 href="/bookmarks/987654/edit" data-remote="true">Edit</a>
            </li>
        """.trimIndent()
        assertEquals(987654L, Ao3BookmarkHtml.ownBookmarkId(html))
    }

    @Test
    fun `没收藏过时页面上只有新建入口`() {
        val html = """
            <li>
              <a class="bookmark_form_trigger_for_123" href="/works/123/bookmarks/new"
                 data-remote="true">Bookmark</a>
            </li>
        """.trimIndent()
        assertNull(Ao3BookmarkHtml.ownBookmarkId(html))
    }

    @Test
    fun `会话失效跳到登录页时拿不到 pseud`() {
        val html = """
            <meta name="csrf-token" content="tok" />
            <form action="/users/login" method="post">
              <input name="user[login]" id="user_login" />
            </form>
        """.trimIndent()
        assertNull(Ao3BookmarkHtml.pseudId(html))
    }
}
