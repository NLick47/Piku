package com.piku.client.ui.common

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackChannelTest {

    @Test
    fun `在场收集者能收到事件`() = runTest {
        val channel = FeedbackChannel()
        val received = mutableListOf<FeedbackMessage>()
        val job = launch { channel.messages.collect { received += it } }
        advanceUntilIdle()

        channel.show(101, "arg")
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(101, received.first().text.res)
        assertEquals(listOf<Any>("arg"), received.first().text.args)
        job.cancel()
    }

    @Test
    fun `没有收集者时事件被丢弃而不是排队`() = runTest {
        val channel = FeedbackChannel()

        // 页面不在场（ViewModel 已创建但页面未打开）
        channel.show(202)

        // 页面之后才订阅：不应收到此前那条
        val received = mutableListOf<FeedbackMessage>()
        val job = launch { channel.messages.collect { received += it } }
        advanceUntilIdle()

        assertTrue("离开页面期间的事件不应被补发", received.isEmpty())
        job.cancel()
    }

    @Test
    fun `重新订阅不会重放离开前的事件`() = runTest {
        val channel = FeedbackChannel()

        // 第一次进入页面：收到一条提示后立刻离开（收集协程被取消，事件已消费）
        val firstPage = mutableListOf<FeedbackMessage>()
        val firstJob = launch { channel.messages.collect { firstPage += it } }
        advanceUntilIdle()
        channel.show(303)
        advanceUntilIdle()
        assertEquals(1, firstPage.size)
        firstJob.cancel()
        advanceUntilIdle()

        // 再次进入页面：不应重放上一条
        val secondPage = mutableListOf<FeedbackMessage>()
        val secondJob = launch { channel.messages.collect { secondPage += it } }
        advanceUntilIdle()

        assertTrue("重进页面不应重放旧提示", secondPage.isEmpty())
        secondJob.cancel()
    }

    @Test
    fun `带动作的事件携带按钮文案与回调`() = runTest {
        val channel = FeedbackChannel()
        val received = mutableListOf<FeedbackMessage>()
        val job = launch { channel.messages.collect { received += it } }
        advanceUntilIdle()

        var clicked = 0
        channel.showAction(404, actionLabelRes = 405) { clicked++ }
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(404, received.first().text.res)
        assertEquals(405, received.first().actionLabelRes)
        received.first().onAction?.invoke()
        assertEquals(1, clicked)
        job.cancel()
    }
}
