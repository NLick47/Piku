package com.piku.client.source.ao3.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class Ao3HtmlTest {

    @Test
    fun paragraphGapsCollapseToSingleBlankLine() {
        val html = "<p>第一段</p>\n    <p>第二段</p>\n\n    <p>第三段</p>"
        assertEquals("第一段\n\n第二段\n\n第三段", Ao3Html.cleanText(html))
    }

    @Test
    fun doubleBreakKeepsOneBlankLine() {
        assertEquals("a\n\nb", Ao3Html.cleanText("a<br><br>b"))
    }

    @Test
    fun singleBreakStaysSingle() {
        assertEquals("a\nb", Ao3Html.cleanText("a<br>b"))
    }

    @Test
    fun entityNewlinesCollapseToo() {
        assertEquals("a\n\nb", Ao3Html.cleanText("a&#10;&#10;&#10;b"))
    }

    @Test
    fun htmlIndentStrippedButNbspIndentKept() {
        assertEquals(
            "a\n\n\u00A0\u00A0b",
            Ao3Html.cleanText("<p>a</p>\n    <p>&nbsp;&nbsp;b</p>"),
        )
    }
}
