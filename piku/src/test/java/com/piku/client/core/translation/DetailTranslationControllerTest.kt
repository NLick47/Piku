@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.piku.client.core.translation

import com.piku.client.core.model.TranslatedFields
import com.piku.client.core.model.WorkDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun detail(translated: TranslatedFields? = null) = WorkDetail(
    title = "タイトル",
    authorName = "作者",
    authorAvatarUrl = "",
    categoryCd = 0,
    categoryName = "",
    imageUrls = emptyList(),
    tags = emptyList(),
    r18 = false,
    translated = translated,
)

private typealias Request = DetailTranslationController.Request
private typealias Origin = DetailTranslationController.Origin
private typealias Failure = DetailTranslationController.Failure

private class Fixture(
    scope: CoroutineScope,
    val autoEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true),
    initial: WorkDetail = detail(),
) {
    val state = MutableStateFlow(DetailTranslationController.State(detail = initial))
    val failures = mutableListOf<Failure>()
    var calls = 0
    var hasKey = true
    var outcome: suspend () -> TranslationOutcome =
        { TranslationOutcome(TranslatedFields(title = "标题"), false) }

    val controller = DetailTranslationController(
        translate = { _, _ ->
            calls++
            outcome()
        },
        config = { DetailTranslationController.Config(autoEnabled.value, hasKey) },
        scope = scope,
        read = { state.value },
        write = { transform -> state.update(transform) },
        onFailed = { failures += it },
        autoEnabled = autoEnabled,
        modelAvailability = emptyFlow(),
    )
}

/** 短字段翻译编排：两个详情页共用，回归点集中在这里 */
class DetailTranslationControllerTest {

    @Test
    fun `failed request keeps the existing translation`() = runTest {
        val f = Fixture(backgroundScope, initial = detail(TranslatedFields(title = "已译标题")))
        f.outcome = { throw TranslationApiException(0, "endpoint down") }

        f.controller.request(Request(Origin.MANUAL))
        runCurrent()

        assertEquals("已译标题", f.state.value.detail?.translated?.title)
        assertFalse(f.state.value.translating)
        assertEquals(listOf(Failure.REQUEST), f.failures)
    }

    @Test
    fun `automatic failure stays silent`() = runTest {
        val f = Fixture(backgroundScope)
        f.outcome = { throw TranslationApiException(0, "endpoint down") }

        f.controller.request(Request(Origin.AUTO))
        runCurrent()

        assertTrue(f.failures.isEmpty())
        assertNull(f.state.value.detail?.translated)
    }

    @Test
    fun `automatic request runs only once for the same content`() = runTest {
        val f = Fixture(backgroundScope)

        f.controller.request(Request(Origin.AUTO))
        runCurrent()
        f.controller.request(Request(Origin.AUTO))
        runCurrent()

        assertEquals(1, f.calls)
    }

    @Test
    fun `manual request preempts an in-flight automatic one`() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.outcome = {
            gate.await()
            TranslationOutcome(TranslatedFields(title = "标题"), false)
        }

        f.controller.request(Request(Origin.AUTO))
        runCurrent()
        assertTrue(f.state.value.translating)

        f.controller.request(Request(Origin.MANUAL))
        runCurrent()
        // 手动意图不用排在自动任务后面，立刻发出自己的请求
        assertEquals(2, f.calls)
        assertTrue(f.state.value.translating)

        gate.complete(Unit)
        runCurrent()
        // 被抢占的旧任务不能回来清忙状态或覆盖结果
        assertEquals("标题", f.state.value.detail?.translated?.title)
        assertFalse(f.state.value.translating)
    }

    @Test
    fun `manual request blocked by config does not strand the running automatic one`() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.outcome = {
            gate.await()
            TranslationOutcome(TranslatedFields(title = "标题"), false)
        }

        f.controller.request(Request(Origin.AUTO))
        runCurrent()
        assertTrue(f.state.value.translating)

        f.hasKey = false
        f.controller.request(Request(Origin.MANUAL))
        runCurrent()
        assertEquals(listOf(Failure.NO_MODEL), f.failures)
        assertTrue(f.state.value.translating)

        gate.complete(Unit)
        runCurrent()
        assertFalse(f.state.value.translating)
        assertEquals("标题", f.state.value.detail?.translated?.title)
    }

    @Test
    fun `cancellation is not reported as a translation failure`() = runTest {
        val f = Fixture(backgroundScope)
        f.outcome = { throw CancellationException("left the page") }

        f.controller.request(Request(Origin.MANUAL))
        runCurrent()

        assertTrue(f.failures.isEmpty())
        assertFalse(f.state.value.translating)
    }

    @Test
    fun `turning auto translate on triggers a catch-up request`() = runTest {
        val auto = MutableStateFlow(false)
        val f = Fixture(backgroundScope, autoEnabled = auto)

        runCurrent()
        // 首发射只是开关当前值，不是用户操作
        assertEquals(0, f.calls)

        auto.value = true
        runCurrent()
        assertEquals(1, f.calls)
    }

    @Test
    fun `turning auto translate on retries after an earlier automatic attempt`() = runTest {
        val auto = MutableStateFlow(true)
        val f = Fixture(backgroundScope, autoEnabled = auto)
        f.outcome = { throw TranslationApiException(0, "endpoint down") }

        f.controller.request(Request(Origin.AUTO))
        runCurrent()
        assertEquals(1, f.calls)
        assertNull(f.state.value.detail?.translated)

        auto.value = false
        runCurrent()
        auto.value = true
        runCurrent()
        // 用户主动打开开关不受「同一内容只自动试一次」限制
        assertEquals(2, f.calls)
    }

    @Test
    fun `manual request without a usable model reports NO_MODEL`() = runTest {
        val f = Fixture(backgroundScope)
        f.hasKey = false

        f.controller.request(Request(Origin.MANUAL))
        runCurrent()

        assertEquals(0, f.calls)
        assertEquals(listOf(Failure.NO_MODEL), f.failures)
    }
}
