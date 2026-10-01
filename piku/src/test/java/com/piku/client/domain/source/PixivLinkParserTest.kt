package com.piku.client.domain.source

import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixivLinkParserTest {

    @Test
    fun parsesCanonicalArtworkShareUrl() {
        assertEquals(
            SourceLink.Work(WorkSource.PIXIV, workId = 123456),
            parsePixivLink("https://www.pixiv.net/artworks/123456"),
        )
    }

    @Test
    fun parsesArtworkUrlWithLocalePrefixAnchorAndTrailingSlash() {
        // 官方分享多页作品时带 #页码 锚点
        assertEquals(
            SourceLink.Work(WorkSource.PIXIV, workId = 9),
            parsePixivLink("https://www.pixiv.net/en/artworks/9/#2"),
        )
    }

    @Test
    fun parsesArtworkUrlWithQueryParams() {
        assertEquals(
            SourceLink.Work(WorkSource.PIXIV, workId = 3),
            parsePixivLink("https://pixiv.net/artworks/3?ref=twitter"),
        )
    }

    @Test
    fun parsesSchemeLessUrlPastedIntoSearchBox() {
        assertEquals(
            SourceLink.Work(WorkSource.PIXIV, workId = 3),
            parsePixivLink("pixiv.net/artworks/3"),
        )
    }

    @Test
    fun parsesNovelUrlAndTagsItAsNovel() {
        assertEquals(
            SourceLink.Work(WorkSource.PIXIV, workId = 77, kind = WorkKind.NOVEL),
            parsePixivLink("https://www.pixiv.net/novel/show.php?id=77"),
        )
    }

    @Test
    fun parsesNovelUrlWithLocalePrefixAndExtraParams() {
        // id 不保证排在 query 首位
        assertEquals(
            SourceLink.Work(WorkSource.PIXIV, workId = 8, kind = WorkKind.NOVEL),
            parsePixivLink("https://pixiv.net/en/novel/show.php?x=1&id=8&y=2"),
        )
    }

    @Test
    fun parsesAuthorUrl() {
        assertEquals(
            SourceLink.User(WorkSource.PIXIV, userId = 42),
            parsePixivLink("https://www.pixiv.net/users/42/"),
        )
    }

    @Test
    fun parsesAuthorIllustrationsTabUrlAsAuthor() {
        // 画师插画 tab 的规范地址，浏览器地址栏最常见的复制形态
        assertEquals(
            SourceLink.User(WorkSource.PIXIV, userId = 42),
            parsePixivLink("https://www.pixiv.net/users/42/artworks"),
        )
    }

    @Test
    fun isCaseInsensitiveAcrossHostAndPath() {
        assertEquals(
            SourceLink.Work(WorkSource.PIXIV, workId = 5),
            parsePixivLink("HTTPS://WWW.PIXIV.NET/ARTWORKS/5"),
        )
    }

    @Test
    fun rejectsNovelUrlWhenIdMissingOrNonNumeric() {
        assertNull(parsePixivLink("https://www.pixiv.net/novel/show.php"))
        assertNull(parsePixivLink("https://www.pixiv.net/novel/show.php?id=abc"))
        assertNull(parsePixivLink("https://www.pixiv.net/novel/show.php?page=1"))
    }

    @Test
    fun rejectsIdsBeyondLongRangeInsteadOfCrashing() {
        // 数字段无上限，超出 Long 必须按「不是链接」返回 null 而不是抛 NumberFormatException：
        // 该解析在搜索框每次键入都会执行，抛异常即崩 App
        assertNull(parsePixivLink("https://www.pixiv.net/artworks/99999999999999999999"))
        assertNull(parsePixivLink("https://www.pixiv.net/novel/show.php?id=99999999999999999999"))
    }

    @Test
    fun rejectsPathsBeyondTheWorkId() {
        assertNull(parsePixivLink("https://www.pixiv.net/artworks/1/extra"))
    }

    @Test
    fun rejectsForeignDomainsAndHostLookalikes() {
        assertNull(parsePixivLink("https://example.com/artworks/1"))
        // pixiv.net.evil.com 的真实 host 是 evil.com，不能被 pixiv 前缀放过
        assertNull(parsePixivLink("https://www.pixiv.net.evil.com/artworks/1"))
    }

    @Test
    fun rejectsSitePagesThatAreNotWorksOrAuthors() {
        assertNull(parsePixivLink("https://www.pixiv.net/tags/東方Project"))
        assertNull(parsePixivLink("https://www.pixiv.net/"))
    }

    @Test
    fun rejectsLegacyUrlForms() {
        // 刻意不支持的历史形态（现代分享链接已覆盖）：钉住这个非目标，防止歧义形态被「顺手修好」放进来
        assertNull(parsePixivLink("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=1"))
        assertNull(parsePixivLink("https://www.pixiv.net/u/1"))
    }

    @Test
    fun rejectsPlainKeywords() {
        assertNull(parsePixivLink("東方プロジェクト"))
        assertNull(parsePixivLink(""))
        assertNull(parsePixivLink("#tag"))
    }
}
