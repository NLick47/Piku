package com.piku.client.data.remote.translation

import com.piku.client.data.remote.translation.TranslationRepository.LinePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行分解（decomposeLines）契约：
 * - 不含链接的文本保持整条一个单元（缓存键与历史行为不变）；
 * - 含链接的文本按行拆分：链接行/空行 Keep 原样回填，文字行各自成单元；
 * - 行内混排链接在引擎边界摘除，stripped 里不得残留任何 http 字样，
 *   链接由调用方在译文行尾原样拼回。
 */
class TranslationRepositoryLinkSplitTest {

    @Test
    fun `link free text stays one whole unit`() {
        val parts = TranslationRepository.decomposeLines("これは面白い話でした", splitLines = true)
        assertEquals(1, parts.size)
        val part = parts[0] as LinePart.Translate
        assertEquals("これは面白い話でした", part.stripped)
        assertEquals(0, part.links.size)
    }

    @Test
    fun `pure link text is kept as is`() {
        val parts = TranslationRepository.decomposeLines("https://www.pixiv.net/users/123", splitLines = true)
        assertEquals(listOf<LinePart>(LinePart.Keep("https://www.pixiv.net/users/123")), parts)
    }

    @Test
    fun `profile with link lines splits into translate and keep`() {
        val text = "MMD静画置き場。永遠の初心者MMDer。\n" +
            "■ニコ動/スタレMMD置き場\n" +
            "https://www.nicovideo.jp/user/14156986/series/455835"
        val parts = TranslationRepository.decomposeLines(text, splitLines = true)
        assertEquals(3, parts.size)
        assertTrue(parts[0] is LinePart.Translate)
        assertEquals(parts[2], LinePart.Keep("https://www.nicovideo.jp/user/14156986/series/455835"))
        // 文字行不残留 URL
        (parts[0] as LinePart.Translate).let {
            assertFalse(it.stripped.contains("http"))
            assertEquals(text.split("\n")[0], it.original)
        }
    }

    @Test
    fun `inline link inside textual line is stripped`() {
        val text = "看我主页 https://x.com/abc 哦"
        val parts = TranslationRepository.decomposeLines(text, splitLines = true)
        assertEquals(1, parts.size)
        val part = parts[0] as LinePart.Translate
        assertFalse(part.stripped.contains("http"))
        assertEquals("看我主页 哦", part.stripped)
        assertEquals(listOf("https://x.com/abc"), part.links)
        assertEquals(text, part.original)
    }

    @Test
    fun `same stripped text with different links keeps distinct originals`() {
        // translateAll 按原文去重：摘除后文字相同的两行，链接必须各自独立，
        // 否则第二行会拼回第一行的链接
        val a = TranslationRepository.decomposeLines("主页 https://a.com", splitLines = true)
            .single() as LinePart.Translate
        val b = TranslationRepository.decomposeLines("主页 https://b.com", splitLines = true)
            .single() as LinePart.Translate
        assertEquals("主页", a.stripped)
        assertEquals("主页", b.stripped)
        assertEquals(listOf("https://a.com"), a.links)
        assertEquals(listOf("https://b.com"), b.links)
        assertFalse(a.original == b.original)
    }

    @Test
    fun `novel path keeps whole text as single unit`() {
        val text = "第一行有链接 https://x.com\n第二行是散文的延续。"
        val parts = TranslationRepository.decomposeLines(text, splitLines = false)
        assertEquals(1, parts.size)
        val part = parts[0] as LinePart.Translate
        assertEquals("第一行有链接\n第二行是散文的延续。", part.stripped)
        assertEquals(listOf("https://x.com"), part.links)
    }

    @Test
    fun `marker link line is kept whole`() {
        val text = "よろしく\n[https://x.com]My Twitter[/https://x.com]"
        val parts = TranslationRepository.decomposeLines(text, splitLines = true)
        assertEquals(2, parts.size)
        assertEquals(LinePart.Keep("[https://x.com]My Twitter[/https://x.com]"), parts[1])
    }
}
