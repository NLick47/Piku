package com.piku.client.data.remote.translation

import com.piku.client.domain.model.TranslatedFields
import com.piku.client.domain.model.mergeTranslatedFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bug 复现：预检透传（isAlreadyInTarget 命中）会把原文填进批量结果，
 * 若 buildFields 不丢弃"与原文相同"的值，纯中文作品在简中目标下会得到
 * 全字段假译文，hasAny=true 导致顶栏与 chip 全出现、点击无变化。
 *
 * buildFields 现拆分为 [shortOut]（标题/简介/作者简介 + 标签）与 [novelOut]（正文），
 * 对应短文本与小说正文分别由不同模型翻译。
 */
class TranslationRepositoryBuildFieldsTest {

    // 纯中文作品 + 简中目标：out 与 sources 完全一致（全部被透传）
    // out 顺序：标题 / 简介 / 作者简介 / 正文 / 标签1 / 标签2
    private val cnSources = listOf(
        "雨中故事",       // title
        "这是一个中文简介。",  // description
        "大家好，感谢支持。",  // profile
        "这是中文正文……",    // novel
        "原创",           // tag1
        "短篇",           // tag2
    )

    // 把 6 元素 out 拆成 shortOut(标题/简介/作者简介/标签) 与 novelOut(正文)
    private fun split(out: List<String>) =
        listOf(out[0], out[1], out[2], out[4], out[5]) to out[3]

    @Test
    fun `full passthrough yields no fields at all`() {
        val (shortOut, novelOut) = split(cnSources)
        val fields = TranslationRepository.buildFields(
            titleSrc = cnSources[0],
            descriptionSrc = cnSources[1],
            profileSrc = cnSources[2],
            tagsSrc = cnSources.subList(4, 6),
            shortOut = shortOut,
            novelSrc = cnSources[3],
            novelOut = novelOut,
        )
        assertNull(fields)
    }

    @Test
    fun `genuinely translated values are kept`() {
        val out = listOf("Rain Story", "A Chinese intro.", "Hi all", "Body text", "Original", "Short")
        val (shortOut, novelOut) = split(out)
        val fields = TranslationRepository.buildFields(
            titleSrc = "雨中故事",
            descriptionSrc = "这是一个中文简介。",
            profileSrc = "大家好，感谢支持。",
            tagsSrc = listOf("原创", "短篇"),
            shortOut = shortOut,
            novelSrc = "正文",
            novelOut = novelOut,
        )
        assertEquals("Rain Story", fields?.title)
        assertEquals("A Chinese intro.", fields?.description)
        assertEquals(listOf("Original", "Short"), fields?.tags)
    }

    @Test
    fun `passthrough tags alone do not create tags field`() {
        // 标题等真翻了，但两个标签都是原文透传 → tags 应为 null 而不是原标签组
        val out = listOf("Rain Story", "A Chinese intro.", "Hi all", "Body", "原创", "短篇")
        val (shortOut, novelOut) = split(out)
        val fields = TranslationRepository.buildFields(
            titleSrc = "雨中故事",
            descriptionSrc = "简介",
            profileSrc = "主页",
            tagsSrc = listOf("原创", "短篇"),
            shortOut = shortOut,
            novelSrc = "正文",
            novelOut = novelOut,
        )
        assertNull(fields?.tags)
        assertEquals("Rain Story", fields?.title)
    }

    @Test
    fun `partially translated tags keep whole aligned list`() {
        val out = listOf("T", "D", "P", "N", "Original", "短篇")
        val (shortOut, novelOut) = split(out)
        val fields = TranslationRepository.buildFields(
            titleSrc = "标题", descriptionSrc = "简", profileSrc = "主", novelSrc = "文",
            tagsSrc = listOf("原创", "短篇"),
            shortOut = shortOut,
            novelOut = novelOut,
        )
        // 一个不同即整组保留（展示层按下标对齐，原文兜底）
        assertEquals(listOf("Original", "短篇"), fields?.tags)
    }

    @Test
    fun `gated long novel produces no novel field`() {
        // 自动路径长文不入 sources：novelSrc 为空串、novelOut 也为空
        val out = listOf("标题译", "简介译", "主页译", "")
        val fields = TranslationRepository.buildFields(
            titleSrc = "タイトル", descriptionSrc = "説", profileSrc = "プ", novelSrc = "",
            tagsSrc = emptyList(),
            shortOut = out.subList(0, 3),
            novelOut = out[3],
        )
        assertNull(fields?.novelText)
        assertEquals("标题译", fields?.title)
    }

    @Test
    fun `model echoing source verbatim is dropped like passthrough`() {
        // 模型把原文原样吐回（looksUntranslated 漏网情形）：同样不得生成假译文
        val out = listOf("感謝", "简介译文", "", "")
        val fields = TranslationRepository.buildFields(
            titleSrc = "感謝", descriptionSrc = "簡介", profileSrc = "", novelSrc = "",
            tagsSrc = emptyList(),
            shortOut = out.subList(0, 3),
            novelOut = out[3],
        )
        assertNull(fields?.title)
        assertEquals("简介译文", fields?.description)
    }

    /**
     * Bug 复现：自动重跑不带长文，若整体覆盖 translated，
     * 阅读器刚拉到的正文译文会从状态中消失。
     */
    @Test
    fun `auto rerun without novel preserves reader fetched novel`() {
        val old = TranslatedFields(title = "旧标题译", novelText = "长篇正文译文")
        val new = TranslatedFields(title = "新标题译") // 本次 includeLongNovel=false
        val merged = mergeTranslatedFields(old, new)
        assertEquals("长篇正文译文", merged?.novelText) // 修复前：为 null（被覆盖丢失）
        assertEquals("新标题译", merged?.title)
    }

    @Test
    fun `fresh novel result wins over stale one`() {
        val old = TranslatedFields(novelText = "旧正文")
        val new = TranslatedFields(novelText = "新正文")
        assertEquals("新正文", mergeTranslatedFields(old, new)?.novelText)
    }

    @Test
    fun `null new batch keeps current state untouched`() {
        val old = TranslatedFields(novelText = "x")
        assertNull(mergeTranslatedFields(old, null))
    }

    @Test
    fun `no old state is plain replace`() {
        val new = TranslatedFields(title = "t")
        assertEquals(new, mergeTranslatedFields(null, new))
    }
}
