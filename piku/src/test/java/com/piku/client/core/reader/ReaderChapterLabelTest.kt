package com.piku.client.core.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChapterLabelTest {

    @Test
    fun stripsChapterPrefixWhenNameFollows() {
        // AO3 的章标题形如「Chapter 1: 名字」，行首已有「第 N 章」
        assertEquals("Quebrado", readerChapterLabel("Chapter 1: Quebrado"))
        assertEquals("Todo por sobrevivir", readerChapterLabel("Chapter 2: Todo por sobrevivir"))
        // 大小写与多余空白都容忍
        assertEquals("名字", readerChapterLabel("chapter 3: 名字"))
        assertEquals("名字", readerChapterLabel("  Chapter 4 :  名字  "))
    }

    @Test
    fun keepsTitleWithoutNameSuffix() {
        // 只有「Chapter N」没有名字：原样保留（它本身就是信息）
        assertEquals("Chapter 5", readerChapterLabel("Chapter 5"))
        // 不像章号前缀的标题不许误切
        assertEquals("Chapters of Us: A Story", readerChapterLabel("Chapters of Us: A Story"))
        assertEquals("第一章：开始", readerChapterLabel("第一章：开始"))
    }

    @Test
    fun blankFallsBack() {
        assertEquals("第 7 章", readerChapterLabel("", fallback = "第 7 章"))
        assertEquals("第 7 章", readerChapterLabel("   ", fallback = "第 7 章"))
        // 冒号后为空（"Chapter 3:"）也回落
        assertEquals("第 7 章", readerChapterLabel("Chapter 3:", fallback = "第 7 章"))
    }

    @Test
    fun noFallbackYieldsEmptyForBlank() {
        assertEquals("", readerChapterLabel(""))
    }
}
