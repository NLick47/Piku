package com.piku.client.core.translation

import com.piku.client.core.model.WorkDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NovelTranslationControllerTest {

    private fun detail() = WorkDetail(
        title = "标题",
        authorName = "",
        authorAvatarUrl = "",
        categoryCd = 0,
        categoryName = "",
        imageUrls = emptyList(),
        tags = emptyList(),
        r18 = false,
    )

    /** 每个源文本一条可手动投喂的事件流 */
    private class Streams {
        private val bySource = mutableMapOf<String, MutableSharedFlow<NovelStreamEvent>>()

        fun of(source: String): MutableSharedFlow<NovelStreamEvent> =
            bySource.getOrPut(source) { MutableSharedFlow(extraBufferCapacity = 8) }

        fun open(source: String): Flow<NovelStreamEvent> = of(source)
    }

    private fun controller(streams: Streams, scope: kotlinx.coroutines.CoroutineScope) =
        NovelTranslationController(
            hasModel = { true },
            stream = { _, source -> streams.open(source) },
            scope = scope,
        )

    /** 复现：译文模式下流未跑完就换章，旧章只剩前缀，不能当成已完成 */
    @Test
    fun `switching chapter mid-stream keeps the partial translation but not as complete`() =
        runTest {
            val streams = Streams()
            val controller = controller(streams, backgroundScope)

            controller.start(detail(), key = 0, source = "ABCDE")
            runCurrent()
            streams.of("ABCDE").tryEmit(
                NovelStreamEvent.Progress(
                    translatedSoFar = "译文",
                    consumedOffset = 2,
                    doneChunks = 1,
                    totalChunks = 10,
                ),
            )
            runCurrent()

            // 切到下一章：controller 取消旧流
            controller.start(detail(), key = 1, source = "FGHIJ")
            runCurrent()

            val part = controller.state.value.parts.getValue(0)
            assertEquals("译文", part.translated)
            assertEquals("CDE", part.remainder)
            // 有译文可展示，但没翻完：宿主据此续翻而不是直接切显示
            assertTrue(controller.hasText(0, "ABCDE"))
            assertFalse(controller.isComplete(0, "ABCDE"))
        }

    @Test
    fun `completed stream marks the chapter complete`() = runTest {
        val streams = Streams()
        val controller = controller(streams, backgroundScope)

        controller.start(detail(), key = 0, source = "ABCDE")
        runCurrent()
        streams.of("ABCDE").tryEmit(
            NovelStreamEvent.Completed(translatedSoFar = "整段译文", failedCount = 0, totalChunks = 3),
        )
        runCurrent()

        assertTrue(controller.isComplete(0, "ABCDE"))
        assertEquals("整段译文", controller.displayText(0, "ABCDE"))
    }

    @Test
    fun `seeded translation is ignored once the body changes`() = runTest {
        val streams = Streams()
        val controller = controller(streams, backgroundScope)

        controller.seed(0, "旧译文", "ABC")
        assertEquals("旧译文", controller.displayText(0, "ABC"))
        assertTrue(controller.isComplete(0, "ABC"))

        // 正文换了（解锁 / 重载），旧译文不能再冒出来
        assertNull(controller.displayText(0, "XYZ"))
        assertFalse(controller.isComplete(0, "XYZ"))
    }

    @Test
    fun `all chunks failing does not count as a finished translation`() = runTest {
        val streams = Streams()
        val controller = controller(streams, backgroundScope)

        controller.start(detail(), key = 0, source = "ABCDE")
        runCurrent()
        // 每块都失败 → 仓库层回退原文，累计译文与原文全等
        streams.of("ABCDE").tryEmit(
            NovelStreamEvent.Completed(translatedSoFar = "ABCDE", failedCount = 3, totalChunks = 3),
        )
        runCurrent()

        assertFalse(controller.isComplete(0, "ABCDE"))
        assertNull(controller.displayText(0, "ABCDE"))
    }

    @Test
    fun `endpoint down abort does not keep the partial translation`() = runTest {
        val streams = Streams()
        val controller = controller(streams, backgroundScope)

        controller.start(detail(), key = 0, source = "ABCDE")
        runCurrent()
        // 接口不可用提前中止：手里只有半段，不能当成整段译文
        streams.of("ABCDE").tryEmit(
            NovelStreamEvent.Completed(
                translatedSoFar = "AB",
                failedCount = 1,
                totalChunks = 1,
                aborted = true,
            ),
        )
        runCurrent()

        assertFalse(controller.isComplete(0, "ABCDE"))
        assertNull(controller.displayText(0, "ABCDE"))
    }
}
