package com.piku.client.data.remote.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmTranslateEngineTest {

    // ---------------- chatUrl ----------------

    @Test
    fun `chat url appends path without double slash`() {
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            LlmTranslateEngine.chatUrl("https://open.bigmodel.cn/api/paas/v4"),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            LlmTranslateEngine.chatUrl("https://open.bigmodel.cn/api/paas/v4/"),
        )
    }

    // ---------------- parseBatch ----------------

    @Test
    fun `parse batch returns segments in marker order`() {
        val output = """
            前言废话
            [[2]]
            第二条译文
            [[1]]
            第一条译文
        """.trimIndent()
        assertEquals(listOf("第一条译文", "第二条译文"), LlmTranslateEngine.parseBatch(output, 2))
    }

    @Test
    fun `parse batch keeps internal line breaks of each segment`() {
        val output = "[[1]]\n第一行\n第二行\n[[2]]\n尾行"
        assertEquals(listOf("第一行\n第二行", "尾行"), LlmTranslateEngine.parseBatch(output, 2))
    }

    @Test
    fun `parse batch fails when a marker is missing`() {
        assertNull(LlmTranslateEngine.parseBatch("[[1]]\n只有一条", 2))
    }

    @Test
    fun `parse batch fails when a segment is blank`() {
        assertNull(LlmTranslateEngine.parseBatch("[[1]]\n有内容\n[[2]]\n   ", 2))
    }

    @Test
    fun `parse batch fails on out of range index`() {
        assertNull(LlmTranslateEngine.parseBatch("[[1]]\na\n[[3]]\nb", 2))
    }

    // ---------------- looksUntranslated ----------------

    @Test
    fun `identical text counts as untranslated`() {
        val source = "こんにちは、世界"
        assertTrue(LlmTranslateEngine.looksUntranslated(source, source))
    }

    @Test
    fun `blank output counts as untranslated`() {
        assertTrue(LlmTranslateEngine.looksUntranslated("テスト", "  "))
    }

    @Test
    fun `kana remaining in output means untranslated`() {
        val source = "これは面白い小説です"
        // 模型把原文原样吐回来（或只翻了一半）
        assertTrue(LlmTranslateEngine.looksUntranslated(source, "これは面白い"))
        // 正常中文译文不应残留假名
        assertFalse(LlmTranslateEngine.looksUntranslated(source, "这是有趣的小说"))
    }

    @Test
    fun `chinese output without kana passes validation`() {
        assertFalse(LlmTranslateEngine.looksUntranslated("おはよう", "早上好"))
    }

    @Test
    fun `non japanese source skips kana check`() {
        // 英文原文没有假名可依据，只要不是空且不同就算翻译过
        assertFalse(LlmTranslateEngine.looksUntranslated("hello world", "你好，世界"))
    }

    @Test
    fun `translating into japanese adds kana and must not be flagged`() {
        // 方向性保护：中文为主的混排译入日语，正确译文的假名远多于原文——
        // 旧逻辑按"残留假名过半"误杀，导致混排块整段回退原文
        val source = "这个角色实在太可爱了，完全是我的本命。かわいい！"
        val output = "このキャラは本当に可愛すぎて、完全に私の推しだ。かわいい！"
        assertFalse(LlmTranslateEngine.looksUntranslated(source, output))
    }

    @Test
    fun `echoed mixed source still counts as untranslated`() {
        // 原样复读（假名数相等）仍判未翻：方向性保护不得放过回声
        val source = "这个角色太可爱了。かわいい！"
        assertTrue(LlmTranslateEngine.looksUntranslated(source, source))
    }

    @Test
    fun `ja to zh partial kana retention boundary unchanged`() {
        val source = "これは面白い小説です本当に面白いのだ" // 假名 12 个
        // 残留 4 个（< 一半）：允许保留的拟声词/专名，算已翻译
        assertFalse(LlmTranslateEngine.looksUntranslated(source, "这是有趣的小说ゴゴゴ"))
        // 残留 7 个（> 一半）：基本没译
        assertTrue(
            LlmTranslateEngine.looksUntranslated(
                source,
                "これは面白い小说、本当に面白いのだよ",
            ),
        )
    }

    // ---------------- isAlreadyInTarget（目标语言预检矩阵） ----------------

    private val zh = "Simplified Chinese"

    @Test
    fun `zh target skips kana-free chinese text`() {
        assertTrue(LlmTranslateEngine.isAlreadyInTarget("今天天气不错，我们一起去散步吧。", zh))
    }

    @Test
    fun `zh target translates japanese containing kana`() {
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("今日も推しが尊かった…！", zh))
    }

    @Test
    fun `zh target translates latin sentences`() {
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("My Dearest Friend", zh))
    }

    @Test
    fun `zh target skips short ascii fragments like r18 tags`() {
        assertTrue(LlmTranslateEngine.isAlreadyInTarget("R-18 ★ 2024", zh))
        // 纯汉字串（无假名）中日互读无碍，跳过最省额度
        assertTrue(LlmTranslateEngine.isAlreadyInTarget("異世界転生 ★ 完結済", zh))
        // 含假名则必须送翻
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("全年齢向け★その2", zh))
    }

    @Test
    fun `ja target skips kana-bearing japanese`() {
        assertTrue(
            LlmTranslateEngine.isAlreadyInTarget("これは面白い話でした", LlmTranslateEngine.TARGET_JA),
        )
    }

    @Test
    fun `ja target sends chinese prose so jp users can read it`() {
        assertFalse(
            LlmTranslateEngine.isAlreadyInTarget("这是一个关于异世界转生的故事。", LlmTranslateEngine.TARGET_JA),
        )
    }

    @Test
    fun `ja target skips kanji heavy japanese titles`() {
        // 汉字:假名 ≈ 1:1 的正常日文（転生したらスライムだった件 ≈ 13:12）不受 3 倍阈值影响
        assertTrue(
            LlmTranslateEngine.isAlreadyInTarget("転生したらスライムだった件", LlmTranslateEngine.TARGET_JA),
        )
    }

    @Test
    fun `ja target sends chinese dominant mixed text`() {
        // 中文段落里夹一句日语感叹：汉字远超假名 3 倍 → 送翻，
        // 日本用户不再看到整段未翻的中文主体
        assertFalse(
            LlmTranslateEngine.isAlreadyInTarget(
                "这个角色实在太可爱了，完全是我的本命。かわいい！",
                LlmTranslateEngine.TARGET_JA,
            ),
        )
    }

    @Test
    fun `zh target sends halfwidth katakana instead of passing it through`() {
        // 半角片假名（U+FF66-FF9F）不在旧平/片假名区段内，修复前会被当"已中文"透传
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("ｶﾞﾝﾊﾞﾚ", zh))
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("ﾃﾞｰﾀ保存用", zh))
    }

    @Test
    fun `zh target sends mixed zh ja sentence containing kana`() {
        // 中日混排只要出现假名就送翻：中文部分需要翻译，日文部分模型原样保留
        assertFalse(
            LlmTranslateEngine.isAlreadyInTarget("今天天气不错、とても良い天気ですね", zh),
        )
    }

    @Test
    fun `en target skips plain english without cjk`() {
        assertTrue(
            LlmTranslateEngine.isAlreadyInTarget("Hello world, this is fine.", LlmTranslateEngine.TARGET_EN),
        )
    }

    @Test
    fun `en target sends any cjk content`() {
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("雨の日の放課後", LlmTranslateEngine.TARGET_EN))
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("今天天气不错", LlmTranslateEngine.TARGET_EN))
    }

    @Test
    fun `zh target sends korean instead of passing it through`() {
        // 韩文无假名、无拉丁词，修复前会被误判为"已中文"而静默透传
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("안녕하세요 오늘도 좋은 하루", zh))
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("한국어 태그", zh))
    }

    @Test
    fun `zh target sends cyrillic arabic and greek text`() {
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("Привет мир", zh))
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("مرحبا بالعالم", zh))
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("Καλημέρα κόσμε", zh))
    }

    @Test
    fun `en target sends non latin foreign scripts`() {
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("안녕하세요", LlmTranslateEngine.TARGET_EN))
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("Привет мир", LlmTranslateEngine.TARGET_EN))
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("สวัสดีครับ", LlmTranslateEngine.TARGET_EN))
    }

    @Test
    fun `ja target still sends korean and russian`() {
        // 无假名的外语在日语目标下本来就放行，回归确认不受本次改动影响
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("안녕하세요", LlmTranslateEngine.TARGET_JA))
        assertFalse(LlmTranslateEngine.isAlreadyInTarget("Привет мир", LlmTranslateEngine.TARGET_JA))
    }

    @Test
    fun `mixed chinese with latin word still passes through in zh target`() {
        // 中英混排、不含外语字系的文本行为不变
        assertTrue(LlmTranslateEngine.isAlreadyInTarget("今天天气不错 nice day", zh))
    }

    // ---------------- isPureLink（纯链接预检） ----------------

    @Test
    fun `bare urls are pure links`() {
        assertTrue(LlmTranslateEngine.isPureLink("https://www.pixiv.net/users/123"))
        assertTrue(LlmTranslateEngine.isPureLink("http://example.com/a?b=1&c=2"))
        assertTrue(LlmTranslateEngine.isPureLink("www.pixiv.net/artworks/99"))
        assertTrue(LlmTranslateEngine.isPureLink("  HTTPS://X.COM/ABC  "))
    }

    @Test
    fun `bracket wrapped urls are pure links`() {
        assertTrue(LlmTranslateEngine.isPureLink("[https://x.com/abc]"))
        assertTrue(LlmTranslateEngine.isPureLink("(https://x.com/abc)"))
    }

    @Test
    fun `text with surrounding prose is not a pure link`() {
        // 链接只是内容的一部分：仍有可译文本，必须正常送翻
        assertFalse(LlmTranslateEngine.isPureLink("Twitter：https://x.com/abc"))
        assertFalse(LlmTranslateEngine.isPureLink("https://x.com/abc です"))
        assertFalse(LlmTranslateEngine.isPureLink("https://x.com/abc と https://pixiv.net"))
        assertFalse(LlmTranslateEngine.isPureLink("普通标签文本"))
    }

    @Test
    fun `marker link with spaced display text is pure link`() {
        // LinkText.convert 产物：显示文本含空格也不能送模型
        assertTrue(LlmTranslateEngine.isPureLink("[https://x.com]My Twitter[/https://x.com]"))
        assertTrue(LlmTranslateEngine.isPureLink("[https://x.com/abc]主页[/https://x.com/abc]"))
    }

    @Test
    fun `marker link with surrounding prose is not pure link`() {
        assertFalse(
            LlmTranslateEngine.isPureLink("フォローは[https://x.com]Twitter[/https://x.com]まで"),
        )
    }

    // ---------------- extractLinks（行内混排链接的摘除） ----------------

    @Test
    fun `extract links strips all urls from mixed line`() {
        val (stripped, links) = LlmTranslateEngine.extractLinks("主页 https://x.com/abc と https://pixiv.net/a")
        assertFalse(stripped.contains("http"))
        assertEquals(2, links.size)
        assertEquals("主页 と", stripped)
    }

    @Test
    fun `extract links removes marker block with display text`() {
        val (stripped, links) = LlmTranslateEngine.extractLinks("前文[https://x.com]My Twitter[/https://x.com]后文")
        assertFalse(stripped.contains("http"))
        assertFalse(stripped.contains("Twitter"))
        assertEquals(listOf("[https://x.com]My Twitter[/https://x.com]"), links)
        assertEquals("前文后文", stripped)
    }

    @Test
    fun `extract links keeps text untouched when no link`() {
        val (stripped, links) = LlmTranslateEngine.extractLinks("普通日文文本です")
        assertEquals("普通日文文本です", stripped)
        assertTrue(links.isEmpty())
    }

    @Test
    fun `extract links collapses leftover whitespace`() {
        val (stripped, _) = LlmTranslateEngine.extractLinks("前 https://x.com 后")
        assertEquals("前 后", stripped)
    }
}
