package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WorkDetailParserTest {


    private fun readResource(name: String): String {
        val res = javaClass.classLoader?.getResource(name)?.readText(Charsets.UTF_8)
        assumeTrue("fixture $name 缺失，跳过（快照仅本地）", res != null)
        return res!!
    }

    private val html: String by lazy { readResource("workdetail.html") }

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
        val html = readResource("workdetail_link.html")
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
        val jaHtml = readResource("workdetail_ja.html")
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
        val brHtml = readResource("workdetail_br.html")
        val detail = WorkDetailParser.parse(brHtml)
        assertEquals(
            "cn：院长/江\n大杂食家\n会更一些其他圈子\n我的所有相关作品，除非我本人同意，任何人不能在任何平台进行二转",
            detail.authorProfile,
        )
        assertTrue("profile must not contain bare <br: ${detail.authorProfile}", !detail.authorProfile.contains("<br"))
    }

    @Test
    fun titleAndDescriptionWithoutBr() {
        val brHtml = readResource("workdetail_br.html")
        val detail = WorkDetailParser.parse(brHtml)
        assertEquals("摸鱼", detail.title)
        assertTrue(detail.description.isEmpty())
        assertTrue("title must not contain bare <br: ${detail.title}", !detail.title.contains("<br"))
    }

    @Test
    fun relatedWorksTitlesContainNoBareBr() {
        val brHtml = readResource("workdetail_br.html")
        val detail = WorkDetailParser.parse(brHtml)
        assertTrue("related works should be parsed, got ${detail.relatedWorks.size}", detail.relatedWorks.isNotEmpty())
        assertTrue(
            "related titles must not contain bare <br: ${detail.relatedWorks.map { it.title }}",
            detail.relatedWorks.all { !it.title.contains("<br") },
        )
    }

    @Test
    fun textWorkHasNoPlaceholderMainImage() {
        val textHtml = """
            <html><head>
            <link rel="canonical" href="https://poipiku.com/13616726/13367054.html" />
            </head><body>
            <div class="IllustItem  Text" id="IllustItem_13367054">
            <h1 id="IllustItemDesc_13367054" class="IllustItemDesc">説明文</h1>
            <h2 class="IllustItemUserName"><a href="/13616726/">kpress</a></h2>
            <a class="IllustItemThumb" href="javascript:void(0)"><img class="IllustItemThumbImg" src="https://cdn.poipiku.com/img/publish_pass.png_640.jpg"/></a>
            <div class="IllustItemThubExpand"><a class="IllustItemText" href="/IllustDetailPcV.jsp?ID=13616726&amp;TD=13367054"><span class="IllustItemThumbText "><div class="NovelSection"><span class="NovelTitle">交頸</span>正文内容<br />第二行</div></span></a></div>
            </div>
            </body></html>
        """.trimIndent()
        val detail = WorkDetailParser.parse(textHtml)
        // 文字作品主图是占位图，不能当作真实图片，否则 UI 显示占位图而非正文
        assertTrue("text work must not carry placeholder image, got ${detail.imageUrls}", detail.imageUrls.isEmpty())
        assertTrue(detail.title.isNotBlank())
        assertTrue(detail.authorName == "kpress")
    }

    @Test
    fun lockedTextWorkWithoutNovelSectionHasNoPlaceholderImage() {
        // 锁页/未解锁时首屏无 NovelSection，仅凭 IllustItem class 的 Text 标记识别文字作品
        val textHtml = """
            <html><head>
            <link rel="canonical" href="https://poipiku.com/13616726/13367054.html" />
            </head><body>
            <div class="IllustItem  Text" id="IllustItem_13367054">
            <h1 id="IllustItemDesc_13367054" class="IllustItemDesc">説明文</h1>
            <h2 class="IllustItemUserName"><a href="/13616726/">kpress</a></h2>
            <a class="IllustItemThumb" href="javascript:void(0)"><img class="IllustItemThumbImg" src="https://cdn.poipiku.com/img/publish_pass.png_640.jpg"/></a>
            <input type="password" class="IllustItemExpandPass" name="PAS">
            </div>
            </body></html>
        """.trimIndent()
        val detail = WorkDetailParser.parse(textHtml)
        assertTrue(
            "locked text work must have empty imageUrls, got ${detail.imageUrls}",
            detail.imageUrls.isEmpty(),
        )
        assertTrue(detail.passwordProtected)
    }

    @Test
    fun r18TextWorkHasNoPlaceholderImage() {
        // R18 文字作品：IllustItem class 为 "R18 Text"，主图可能是 poipiku logo 占位
        val textHtml = """
            <html><head>
            <link rel="canonical" href="https://poipiku.com/14035669/13368368.html" />
            </head><body>
            <div class="IllustItem  R18 Text" id="IllustItem_13368368">
            <h1 id="IllustItemDesc_13368368" class="IllustItemDesc">説明文</h1>
            <h2 class="IllustItemUserName"><a href="/14035669/">作者</a></h2>
            <a class="IllustItemThumb" href="javascript:void(0)"><img class="IllustItemThumbImg" src="https://cdn.poipiku.com/assets/img/poipiku_icon_512x512_2.png"/></a>
            </div>
            </body></html>
        """.trimIndent()
        val detail = WorkDetailParser.parse(textHtml)
        assertTrue(
            "r18 text work must have empty imageUrls, got ${detail.imageUrls}",
            detail.imageUrls.isEmpty(),
        )
    }

    @Test
    fun imageWorkStillKeepsMainImage() {
        // 图片作品（IllustItem class 无 Text）不受影响，仍保留主图
        val textHtml = """
            <html><head>
            <link rel="canonical" href="https://poipiku.com/13240156/13349246.html" />
            </head><body>
            <div class="IllustItem  R18" id="IllustItem_13349246">
            <h1 id="IllustItemDesc_13349246" class="IllustItemDesc">画像</h1>
            <h2 class="IllustItemUserName"><a href="/13240156/">作者名</a></h2>
            <a class="IllustItemThumb" href="javascript:void(0)"><img class="IllustItemThumbImg" src="https://cdn.poipiku.com/013240156/a.jpg_640.jpg"/></a>
            </div>
            </body></html>
        """.trimIndent()
        val detail = WorkDetailParser.parse(textHtml)
        assertEquals(listOf("https://cdn.poipiku.com/013240156/a.jpg_640.jpg"), detail.imageUrls)
    }

    @Test
    fun extractNovelTextFromAppendResponse() {
        val appendHtml = """
            <a class="IllustItemText" style="max-height:470px; overflow: scroll;" href="/IllustDetailPcV.jsp?ID=13616726&amp;TD=13367054"><span class="IllustItemThumbText "><div class="NovelSection"><span class="NovelTitle">交頸</span>这到底是怎么回事？<br />DAY1<br />END</div></span></a>
        """.trimIndent()
        val novel = WorkDetailParser.extractNovelText(appendHtml)
        assertTrue("novel text should be extracted, got '$novel'", novel.contains("这到底是怎么回事？"))
        assertTrue(novel.contains("DAY1"))
        assertTrue(novel.contains("END"))
        assertTrue("NovelTitle text should be kept: '$novel'", novel.contains("交頸"))
        assertTrue("novel must not contain any tag: '$novel'", !novel.contains("<"))
    }

    @Test
    fun extractNovelTextStripsAllResidualTags() {
        // NovelSection 内的 span/b/strong/嵌套标签全部剥离，正文与换行完好
        val appendHtml = """
            <a class="IllustItemText" href="/IllustDetailPcV.jsp?ID=1&amp;TD=2"><span class="IllustItemThumbText "><div class="NovelSection"><span class="NovelTitle">标题</span><b>加粗</b>正文<strong>重点</strong><br />第二行<span>内联</span>END</div></span></a>
        """.trimIndent()
        val novel = WorkDetailParser.extractNovelText(appendHtml)
        assertEquals("标题加粗正文重点\n第二行内联END", novel)
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