package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkDetailParserTest {

    private val html = javaClass.classLoader
        ?.getResource("workdetail.html")
        ?.readText(Charsets.UTF_8)
        ?: error("workdetail.html not found")

    @Test
    fun parsesDetail() {
        val detail = WorkDetailParser.parse(html)
        assertEquals("玫瑰仙子来信", detail.title)
        assertEquals("1403某", detail.authorName)
        assertEquals(4, detail.categoryCd)
        assertEquals("DOODLE", detail.categoryName)
        assertTrue(detail.description.contains("节能体"))
        assertEquals(listOf("罗吒x你", "罗吒乙女"), detail.tags)
        assertTrue(detail.r18)
        assertTrue(detail.imageUrls.isNotEmpty())
    }

    @Test
    fun parsesAuthorProfile() {
        val detail = WorkDetailParser.parse(html)
        assertEquals("哪吒x你", detail.authorProfile)
    }

    @Test
    fun parsesReactions() {
        val detail = WorkDetailParser.parse(html)
        assertTrue(detail.reactions.isNotEmpty())
        assertEquals("💖", detail.reactions.first())
        assertTrue(detail.reactions.contains("🍆"))
        assertEquals(detail.reactions.size, detail.reactions.distinct().size)
        assertTrue(detail.reactionCount >= detail.reactions.size)
    }

    @Test
    fun parsesRelatedWorks() {
        val detail = WorkDetailParser.parse(html)
        assertTrue("related works should be parsed, got ${detail.relatedWorks.size}", detail.relatedWorks.size >= 5)
        val first = detail.relatedWorks.first()
        assertTrue(first.id > 0)
        assertTrue(first.authorId > 0)
        assertTrue(first.authorName.isNotBlank())
        assertTrue(first.thumbnailUrl.isNotBlank())
        assertTrue("first title is blank: '${first.title}'", first.title.isNotBlank())
        assertTrue(detail.relatedWorks.all { it.id > 0 && it.authorName.isNotBlank() })
    }

    @Test
    fun convertsHtmlLinksInTitleAndDescription() {
        val html = """
            <html><head>
            <link rel="canonical" href="https://poipiku.com/13240156/13349246.html" />
            </head><body>
            <h3 class="UserInfoProfile">プロフィール</h3>
            <div class="IllustItem  Upload" id="IllustItem_13349246">
            <h1 id="IllustItemDesc_13349246" class="IllustItemDesc">参考 <a class="AutoLink" href="https://twitter.com/example/status/1">ツイート元</a> と <a class="AutoLink" href="https://poipiku.com/12555920/13349091.html">pixiv作品</a></h1>
            <h2 class="IllustItemUserName"><a href="/13240156/">作者名</a></h2>
            <a class="IllustItemUserThumb" href="/13240156/" style="background-image:url('https://cdn.poipiku.com/013240156/a.jpg')"></a>
            <div class="IllustItemCommand"><h2 class="IllustItemCategory"><a class="Category C1" href="/NewArrivalPcV.jsp?CD=1">ILLUST</a></h2></div>
            <a class="IllustItemThumb" href="javascript:void(0)"><img class="IllustItemThumbImg" src="https://cdn.poipiku.com/img/a.jpg_640.jpg"/></a>
            </div>
            </body></html>
        """.trimIndent()
        val detail = WorkDetailParser.parse(html)
        assertEquals(
            "参考 [https://twitter.com/example/status/1]ツイート元[/https://twitter.com/example/status/1] と [https://poipiku.com/12555920/13349091.html]pixiv作品[/https://poipiku.com/12555920/13349091.html]",
            detail.title,
        )
        assertTrue(detail.description.isEmpty())
    }

    @Test
    fun parsesRealPageWithSingleQuotedProfileLink() {
        val html = javaClass.classLoader
            ?.getResource("workdetail_link.html")
            ?.readText(Charsets.UTF_8)
            ?: error("workdetail_link.html not found")
        val detail = WorkDetailParser.parse(html)
        assertEquals(
            "[https://twitter.com/radreamra]@radreamra[/https://twitter.com/radreamra]",
            detail.authorProfile,
        )
        assertTrue("title must not contain raw anchors: ${detail.title}", !detail.title.contains("<a"))
        assertTrue("description must not contain raw anchors: ${detail.description}", !detail.description.contains("<a"))
        assertEquals("この間夢(寝て見る方)に出てきたイマジナリー彼氏の話", detail.title)
        assertTrue(detail.relatedWorks.all { it.id != 13349244L })
    }

    @Test
    fun parsesJapanesePageRelatedWorks() {
        val jaHtml = javaClass.classLoader
            ?.getResource("workdetail_ja.html")
            ?.readText(Charsets.UTF_8)
            ?: error("workdetail_ja.html not found")
        val detail = WorkDetailParser.parse(jaHtml)
        assertTrue(
            "japanese page related works should be parsed, got ${detail.relatedWorks.size}",
            detail.relatedWorks.size >= 10,
        )
        assertTrue(detail.relatedWorks.none { it.id == 13349246L })
        assertTrue(detail.relatedWorks.all { it.id > 0 && it.authorName.isNotBlank() })
        assertEquals("ユムコチャン・ボナパルト", detail.authorName)
        assertEquals(0, detail.reactionCount)
    }

    @Test
    fun authorProfileMultiLineBreaksToNewlines() {
        val brHtml = javaClass.classLoader
            ?.getResource("workdetail_br.html")
            ?.readText(Charsets.UTF_8)
            ?: error("workdetail_br.html not found")
        val detail = WorkDetailParser.parse(brHtml)
        assertEquals(
            "cn：院长/江\n大杂食家\n会更一些其他圈子\n我的所有相关作品，除非我本人同意，任何人不能在任何平台进行二转",
            detail.authorProfile,
        )
        assertTrue("profile must not contain bare <br: ${detail.authorProfile}", !detail.authorProfile.contains("<br"))
    }

    @Test
    fun titleAndDescriptionWithoutBr() {
        val brHtml = javaClass.classLoader
            ?.getResource("workdetail_br.html")
            ?.readText(Charsets.UTF_8)
            ?: error("workdetail_br.html not found")
        val detail = WorkDetailParser.parse(brHtml)
        assertEquals("摸鱼", detail.title)
        assertTrue(detail.description.isEmpty())
        assertTrue("title must not contain bare <br: ${detail.title}", !detail.title.contains("<br"))
    }

    @Test
    fun relatedWorksTitlesContainNoBareBr() {
        val brHtml = javaClass.classLoader
            ?.getResource("workdetail_br.html")
            ?.readText(Charsets.UTF_8)
            ?: error("workdetail_br.html not found")
        val detail = WorkDetailParser.parse(brHtml)
        assertTrue("related works should be parsed, got ${detail.relatedWorks.size}", detail.relatedWorks.isNotEmpty())
        assertTrue(
            "related titles must not contain bare <br: ${detail.relatedWorks.map { it.title }}",
            detail.relatedWorks.all { !it.title.contains("<br") },
        )
    }

    @Test
    fun extractUnlockBlockedMessageStripsHtmlAndEntities() {
        val msg = WorkDetailParser.extractUnlockBlockedMessage(
            "右上のログインボタンをクリックして、<b>ポイピク</b>とTwitterアカウントを連携してください。&amp;詳細",
        )
        assertEquals("右上のログインボタンをクリックして、ポイピクとTwitterアカウントを連携してください。&詳細", msg)
    }

    @Test
    fun extractUnlockBlockedMessageReturnsEmptyForBlank() {
        assertTrue(WorkDetailParser.extractUnlockBlockedMessage("   ").isEmpty())
        assertTrue(WorkDetailParser.extractUnlockBlockedMessage("").isEmpty())
    }
}