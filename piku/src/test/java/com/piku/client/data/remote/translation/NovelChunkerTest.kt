package com.piku.client.data.remote.translation

import com.piku.client.domain.model.TranslatedFields
import com.piku.client.domain.model.WorkDetail
import com.piku.client.domain.translation.ChunkContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelChunkerTest {

    private fun joined(chunks: List<NovelChunker.Chunk>): String =
        chunks.joinToString("") { it.text + it.separatorAfter }


    @Test
    fun `roundtrip preserves source exactly`() {
        val source = "第一段。\n第二段。\n\n＊＊＊\n\n第三段！\n第四段？"
        assertEquals(source, joined(NovelChunker.split(source)))
    }

    @Test
    fun `roundtrip preserves leading and trailing blank lines`() {
        val source = "\n\n开头前有空行\n正文。\n\n\n"
        assertEquals(source, joined(NovelChunker.split(source)))
    }

    @Test
    fun `blank only source returns single chunk`() {
        val source = "\n\n\n"
        val chunks = NovelChunker.split(source)
        assertEquals(1, chunks.size)
        assertEquals(source, joined(chunks))
    }


    @Test
    fun `paragraphs are never split across chunks`() {
        val paragraph = "あ".repeat(300)
        val source = (1..6).joinToString("\n") { "$it 段落 $paragraph" }
        val chunks = NovelChunker.split(source)
        assertTrue("应切成多块", chunks.size > 1)
        // 每个原始行必须完整地、按序出现在某一块内（段落原子性）
        val sourceLines = source.split('\n').filter { it.isNotBlank() }
        val chunkLines = chunks.flatMap { it.text.split('\n') }.filter { it.isNotBlank() }
        assertEquals(sourceLines, chunkLines)
    }

    @Test
    fun `packing respects target size roughly`() {
        val paragraph = "あ".repeat(250)
        val source = (1..12).joinToString("\n") { "$paragraph" }
        val chunks = NovelChunker.split(source)
        assertTrue(chunks.size >= 2)
        // 除最后一块外都应接近目标大小（允许一块的误差）
        chunks.dropLast(1).forEach { chunk ->
            assertTrue(
                "块过大: ${chunk.text.length}",
                chunk.text.length < NovelChunker.TARGET_CHUNK_CHARS * 2,
            )
        }
    }


    @Test
    fun `divider forces a chunk break and never enters text`() {
        val source = "场景一内容。\n" + "あ".repeat(200) + "\n***\n" + "い".repeat(200) + "\n场景二结尾。"
        val chunks = NovelChunker.split(source)
        assertTrue("分隔线后应断块", chunks.size >= 2)
        chunks.forEach { chunk ->
            assertFalse(chunk.text.contains("***"))
            assertFalse(chunk.text.lines().any { NovelChunker.isDividerLine(it) })
        }
        assertTrue(joined(chunks) == source)
    }

    @Test
    fun `divider detection covers common styles`() {
        listOf("***", "---", "＊＊＊", "※※※", "―――", "......", "======").forEach {
            assertTrue("应识别分隔线: $it", NovelChunker.isDividerLine(it))
        }
        assertFalse(NovelChunker.isDividerLine("—— 这是普通破折号开头的句子。"))
        assertFalse(NovelChunker.isDividerLine("**加粗**"))
        assertFalse(NovelChunker.isDividerLine("...三点不算"))
    }


    @Test
    fun `oversized line is split at sentence boundaries without losing chars`() {
        val sentence = "あいうえお。"
        val line = sentence.repeat(500) // 2500 字，超硬上限
        val chunks = NovelChunker.split(line)
        assertTrue(chunks.size >= 2)
        assertEquals(line, joined(chunks))
        // 每片都应以句末标点收尾（最后一片除外）
        chunks.dropLast(1).forEach { chunk ->
            assertTrue(chunk.text.last() in "。！？…")
        }
    }

    @Test
    fun `oversized line without punctuation falls back to hard cut`() {
        val line = "あ".repeat(2000)
        val chunks = NovelChunker.split(line)
        assertTrue(chunks.size >= 2)
        assertEquals(line, joined(chunks))
    }


    @Test
    fun `tail aligns to sentence boundary`() {
        val sentences = (1..20).joinToString("") { "第${it}句话。" }
        val tail = NovelChunker.tail(sentences, 50)
        assertFalse(tail.startsWith("半句"))
        assertTrue(tail.length <= 50)
        assertTrue(tail.startsWith("第"))
        // 必须从某句的开头开始（即前一字符是句号被截掉的边界）
        val idx = sentences.indexOf(tail)
        assertTrue(idx == 0 || sentences[idx - 1] == '。')
    }

    @Test
    fun `tail keeps whole text when under limit`() {
        val text = "短文本。"
        assertEquals(text, NovelChunker.tail(text, 100))
    }
}

class EchoStripTest {

    private val originalTail = "彼は静かに頷いた。それから窓の外を見た。"
    private val translatedTail = "他静静地点了点头。然后看向窗外。"

    private fun context() = ChunkContext(originalTail, translatedTail)

    @Test
    fun `clean output passes through unchanged`() {
        val output = "「そうだね」と彼女は言った。".trim()
        assertEquals(output, LlmTranslateEngine.stripEcho(output, context()))
    }

    @Test
    fun `null context is identity`() {
        val output = "任意译文。"
        assertEquals(output, LlmTranslateEngine.stripEcho(output, null))
    }

    @Test
    fun `full echo with all markers is stripped to body`() {
        val prefix = "⟦上文原句⟧\n$originalTail\n⟦上文译文⟧\n$translatedTail\n⟦待翻正文⟧\n"
        val body = "这是真正的译文。"
        assertEquals(body, LlmTranslateEngine.stripEcho(prefix + body, context()))
    }

    @Test
    fun `original tail echo without markers is stripped`() {
        val body = "后续的译文内容。"
        assertEquals(body, LlmTranslateEngine.stripEcho(originalTail + body, context()))
    }

    @Test
    fun `translated tail echo with marker is stripped`() {
        val output = "⟦上文译文⟧$translatedTail\n后续的译文内容。"
        assertEquals("后续的译文内容。", LlmTranslateEngine.stripEcho(output, context()))
    }

    @Test
    fun `legitimate translation identical to translated tail survives via validation input`() {
        // 极端巧合：输出恰好以译文尾开头且再无其他内容——剥离后为空串，
        // 交给 looksUntranslated 判空回退，不会凭空丢块（此处只验证剥离行为本身）
        val stripped = LlmTranslateEngine.stripEcho(translatedTail, context())
        assertEquals("", stripped)
    }
}

class TagGlossaryTest {

    private fun detail(
        tags: List<String>,
        translatedTags: List<String>?,
    ): WorkDetail = WorkDetail(
        title = "",
        authorName = "",
        authorAvatarUrl = "",
        categoryCd = 0,
        categoryName = "",
        imageUrls = emptyList(),
        tags = tags,
        r18 = false,
        translated = translatedTags?.let { TranslatedFields(tags = it) },
    )

    @Test
    fun `builds pairs from translated tags`() {
        val block = TranslationRepository.tagGlossaryBlock(
            detail(listOf("五条悟", "呪術廻戦"), listOf("五条悟", "咒术回战")),
        )
        // 恒等对（五条悟→五条悟）无约束信息，按设计丢弃
        assertEquals(
            "【本作标签对照 · 人名等专名严格照此翻译】\n呪術廻戦→咒术回战",
            block,
        )
    }

    @Test
    fun `identity pairs are dropped`() {
        val block = TranslationRepository.tagGlossaryBlock(
            detail(listOf("R-18"), listOf("R-18")),
        )
        assertNull(block)
    }

    @Test
    fun `no tags or untranslated tags returns null`() {
        assertNull(TranslationRepository.tagGlossaryBlock(detail(emptyList(), null)))
        assertNull(TranslationRepository.tagGlossaryBlock(detail(listOf("五条悟"), null)))
        // 有原文标签但译文缺失（未翻完）→ 整节省略
        assertNull(TranslationRepository.tagGlossaryBlock(detail(listOf("五条悟"), emptyList())))
    }
}
