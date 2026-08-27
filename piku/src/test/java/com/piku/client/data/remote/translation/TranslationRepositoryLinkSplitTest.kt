package com.piku.client.data.remote.translation

import com.piku.client.data.remote.translation.TranslationRepository.LinePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行分解（decomposeLines）契约：
 * - 每条文本恒为单一处理单元（段数不随行数/链接膨胀，小模型友好）；
 * - 整条是链接、或摘链后不剩文字 → Keep 原样回填，永不送模型；
 * - stripped 里不得残留任何 http 字样，链接由调用方在译文末尾原样拼回。
 */
class TranslationRepositoryLinkSplitTest {

    @Test
    fun `link free text stays one whole unit`() {
        val parts = TranslationRepository.decomposeLines("これは面白い話でした")
        assertEquals(1, parts.size)
        val part = parts[0] as LinePart.Translate
        assertEquals("これは面白い話でした", part.stripped)
        assertEquals(0, part.links.size)
    }

    @Test
    fun `pure link text is kept as is`() {
        val parts = TranslationRepository.decomposeLines("https://www.pixiv.net/users/123")
        assertEquals(listOf<LinePart>(LinePart.Keep("https://www.pixiv.net/users/123")), parts)
    }

    @Test
    fun `multiline text with link line stays a single unit`() {
        // 性能契约：含链接不再触发按行拆分——段数恒为 1，
        // 否则小模型按 [[n]] 逐段输出会随行数线性变慢
        val text = "MMD静画置き場。永遠の初心者MMDer。\n" +
            "■ニコ動/スタレMMD置き場\n" +
            "https://www.nicovideo.jp/user/14156986/series/455835"
        val parts = TranslationRepository.decomposeLines(text)
        assertEquals(1, parts.size)
        val part = parts[0] as LinePart.Translate
        assertFalse(part.stripped.contains("http"))
        // 链接行摘除后剩余文字行保留换行结构
        assertEquals(
            "MMD静画置き場。永遠の初心者MMDer。\n■ニコ動/スタレMMD置き場",
            part.stripped,
        )
        assertEquals(listOf("https://www.nicovideo.jp/user/14156986/series/455835"), part.links)
        assertEquals(text, part.original)
    }

    @Test
    fun `inline link inside textual line is stripped`() {
        val text = "看我主页 https://x.com/abc 哦"
        val parts = TranslationRepository.decomposeLines(text)
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
        val a = TranslationRepository.decomposeLines("主页 https://a.com")
            .single() as LinePart.Translate
        val b = TranslationRepository.decomposeLines("主页 https://b.com")
            .single() as LinePart.Translate
        assertEquals("主页", a.stripped)
        assertEquals("主页", b.stripped)
        assertEquals(listOf("https://a.com"), a.links)
        assertEquals(listOf("https://b.com"), b.links)
        assertFalse(a.original == b.original)
    }

    @Test
    fun `same stripped text with different links gets distinct cache keys`() {
        val a = TranslationRepository.decomposeLines("主页 https://a.com")
            .single() as LinePart.Translate
        val b = TranslationRepository.decomposeLines("主页 https://b.com")
            .single() as LinePart.Translate
        val keyA = TranslationRepository.cacheKey(a.stripped, a.links)
        val keyB = TranslationRepository.cacheKey(b.stripped, b.links)
        assertEquals(a.stripped, b.stripped)
        assertFalse(keyA == keyB)
    }

    @Test
    fun `same stripped text with identical links shares cache key`() {
        val a = TranslationRepository.decomposeLines("主页 https://a.com")
            .single() as LinePart.Translate
        val b = TranslationRepository.decomposeLines("主页 https://a.com")
            .single() as LinePart.Translate
        assertEquals(
            TranslationRepository.cacheKey(a.stripped, a.links),
            TranslationRepository.cacheKey(b.stripped, b.links),
        )
    }

    @Test
    fun `novel path keeps whole text as single unit`() {
        val text = "第一行有链接 https://x.com\n第二行是散文的延续。"
        val parts = TranslationRepository.decomposeLines(text)
        assertEquals(1, parts.size)
        val part = parts[0] as LinePart.Translate
        assertEquals("第一行有链接\n第二行是散文的延续。", part.stripped)
        assertEquals(listOf("https://x.com"), part.links)
    }

    @Test
    fun `marker link block is extracted with display text`() {
        val text = "よろしく\n[https://x.com]My Twitter[/https://x.com]"
        val parts = TranslationRepository.decomposeLines(text)
        assertEquals(1, parts.size)
        val part = parts[0] as LinePart.Translate
        assertFalse(part.stripped.contains("Twitter"))
        assertEquals(listOf("[https://x.com]My Twitter[/https://x.com]"), part.links)
    }

    @Test
    fun `text that becomes blank after extraction is kept whole`() {
        // 整条只有链接和空白：没有可翻内容，原样保留不为空内容浪费请求
        val text = "  https://x.com/abc \n"
        val parts = TranslationRepository.decomposeLines(text)
        assertEquals(listOf<LinePart>(LinePart.Keep(text)), parts)
    }
}
