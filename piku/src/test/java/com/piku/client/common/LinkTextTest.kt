package com.piku.client.common

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkTextTest {

    @Test
    fun convertHandlesSingleQuotedAnchor() {
        val html = "<a class='AutoLink' href='https://twitter.com/radreamra' target='_blank'>@radreamra</a>"
        assertEquals(
            "[https://twitter.com/radreamra]@radreamra[/https://twitter.com/radreamra]",
            LinkText.convert(html),
        )
    }

    @Test
    fun convertHandlesDoubleQuotedAnchorWithAttributesBeforeHref() {
        val html = "<a class=\"fab fa-twitter\" target=\"_blank\" href=\"https://twitter.com/2domenojinse1\">@2domenojinse1</a>"
        assertEquals(
            "[https://twitter.com/2domenojinse1]@2domenojinse1[/https://twitter.com/2domenojinse1]",
            LinkText.convert(html),
        )
    }

    @Test
    fun convertKeepsTextOnlyForNonHttpLinks() {
        val html = "<a href=\"javascript:void(0)\">ボタン</a>と<a href=\"/377568/\">プロフィール</a>"
        assertEquals("ボタンとプロフィール", LinkText.convert(html))
    }

    @Test
    fun convertStripsStrayTags() {
        val html = "<a class='AutoLink' href='https://x.com/a' target='_blank'>リンク</a>と<a class='AutoLink'>壊れ</a>と<img src='x.png'/>"
        assertEquals(
            "[https://x.com/a]リンク[/https://x.com/a]と壊れと",
            LinkText.convert(html),
        )
    }

    @Test
    fun convertKeepsBrAndEntitiesIntact() {
        val html = "説明<br />詳細&amp;情報"
        assertEquals("説明<br />詳細&amp;情報", LinkText.convert(html))
    }

    @Test
    fun parseHandlesCompleteMarkersAndBareUrls() {
        val segments = LinkText.parse(
            "参考 [https://twitter.com/a]ツイート元[/https://twitter.com/a] と https://poipiku.com/1/2.html。",
        )
        assertEquals(
            listOf(
                LinkSegment.Plain("参考 "),
                LinkSegment.Link("https://twitter.com/a", "ツイート元"),
                LinkSegment.Plain(" と "),
                LinkSegment.Link("https://poipiku.com/1/2.html", "https://poipiku.com/1/2.html"),
                LinkSegment.Plain("。"),
            ),
            segments,
        )
    }

    @Test
    fun parseHandlesUnclosedMarker() {
        val segments = LinkText.parse("[https://twitter.com/a]残ったテキスト")
        assertEquals(
            listOf(LinkSegment.Link("https://twitter.com/a", "残ったテキスト")),
            segments,
        )
    }

    @Test
    fun parseDropsStrayCloseMarker() {
        val segments = LinkText.parse("続き[/https://twitter.com/a]")
        assertEquals(listOf(LinkSegment.Plain("続き")), segments)
    }

    @Test
    fun parseTrimsTrailingPunctuationFromBareUrl() {
        val segments = LinkText.parse("見て→ https://example.com/page。終わり")
        assertEquals(
            listOf(
                LinkSegment.Plain("見て→ "),
                LinkSegment.Link("https://example.com/page", "https://example.com/page"),
                LinkSegment.Plain("。終わり"),
            ),
            segments,
        )
    }
}