package com.piku.client.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelParagraphLayoutTest {

    @Test
    fun `plain novel separated by single newlines gets indent`() {
        val text = "秦丘学園の生徒たちには、ある一つの法則が存在していた。\n" +
            "それは――「一芸に秀でた者は、平均以上の才能を伸ばせない」というものだ。\n" +
            "例えば、圧倒的な調合スキルを持ち、学園のトップに君臨する寒川凪。"
        assertTrue(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `translated chinese prose gets indent`() {
        val text = "他推开门，屋里没有人。\n桌上摊着一本翻到一半的书。\n窗外的雨还在下。"
        assertTrue(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `author already indented with ideographic space is left alone`() {
        val text = "\u3000放課後の夢ノ咲学院には、授業中とは違う静けさがある。\n" +
            "\u3000廊下を行き交う生徒たちの声は、授業の終わりを告げるチャイムから遠ざかっていく。\n" +
            "\u3000人気のなくなった教室には、まだ西日の名残が差し込んでいた。"
        assertFalse(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `author already indented with half width space is left alone`() {
        val text = " The first line is indented by the author.\n" +
            " The second line too, with a leading space.\n" +
            " And a third one, same style."
        assertFalse(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `blank line separated paragraphs are left alone`() {
        val text = "第一章正文第一段，写得比较长一些，构成完整的一句。\n\n" +
            "第二章正文第二段，同样是一个完整的段落内容。\n\n" +
            "第三段收尾，段落之间都靠空行分隔开来。"
        assertFalse(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `mixed blank and bare newlines still gets indent`() {
        val text = "「……は？？」\n" +
            "なんで……ここに……佐野大樹が。\n" +
            "テレビで見たまんま。\n" +
            "くすんだ淡いピンク色の髪は、少し長めのマッシュヘア。\n" +
            "眉にかかるほどの前髪に、耳が少し隠れるくらいの長さ。\n\n" +
            "優しげな垂れ目に、目の下には特徴的なハート型のほくろ。\n" +
            "整った顔立ちなのに、どこか幼さが残っていて、子犬みたいな雰囲気がある。\n" +
            "そして何より、笑った時のあの可愛らしさ。\n" +
            "……テレビで見るより、ずっと可愛い。"
        assertTrue(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `verse with short unpunctuated lines is left alone`() {
        val text = "僕は百点\n取ったことない\n小学校から\n大学まで\nただ一回も\n\n" +
            "最高得点\n九十五点\n五点\u3000足りない"
        assertFalse(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `short english prose lines ending with period still get indent`() {
        val text = "He ran.\nShe hid.\nIt fell.\nWe left."
        assertTrue(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `short dialogue lines ending with brackets still get indent`() {
        val text = "「ごめん別れよう」\nたった七文字の言葉で頭がカッと熱くなった。\n" +
            "「……えっ」\n胃のあたりが気持ち悪くなって、うまく息ができない。\n" +
            "「採用。」\nゴールデンウィーク最終日、たくさん考えた自己PRを練習した。"
        assertTrue(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `zero width space is not treated as author indentation`() {
        val text = "\u200b初夏の緑が深く色づく頃、葵の静かな日常は引き裂かれた。\n" +
            "\u200b彼らは徳川家の重臣たちで、静まり返った広間に通された。\n" +
            "\u200bその控えめでありながらも凛とした物腰に、重臣たちは目を細めた。"
        assertTrue(NovelParagraphLayout.shouldIndent(text))
    }

    @Test
    fun `single line text has no paragraph boundary`() {
        assertFalse(NovelParagraphLayout.shouldIndent("たった一行の短い作品。"))
    }

    @Test
    fun `blank text is not indented`() {
        assertFalse(NovelParagraphLayout.shouldIndent(""))
        assertFalse(NovelParagraphLayout.shouldIndent("\n\n\n"))
    }

    @Test
    fun `paragraph gap inserts blank line only at bare newlines`() {
        val text = "第一段。\n第二段。\n\n第三段。\n"
        assertEquals("第一段。\n\n第二段。\n\n第三段。\n", NovelParagraphLayout.withParagraphGap(text))
    }

    @Test
    fun `paragraph gap on already-gapped text is idempotent`() {
        val once = NovelParagraphLayout.withParagraphGap("甲。\n乙。\n丙。")
        assertEquals(once, NovelParagraphLayout.withParagraphGap(once))
    }
}
