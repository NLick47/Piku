package com.piku.client.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReloadTest {

    @Test
    fun changeBeforeTheCollectorStartsStillTriggersReload() = runTest {
        val version = MutableStateFlow(0L)
        var reloads = 0

        val job = reloadOnSessionChange(version) { reloads++ }
        // 订阅协程还没被调度到，会话变化先发生了（冷启动补登完成 / 重登完成）
        version.value = 1

        advanceUntilIdle()

        assertEquals("调用时点之后的变化必须重载，不能因为协程还没开跑就丢掉", 1, reloads)
        job.cancel()
    }

    @Test
    fun versionAlreadyPresentAtCallTimeDoesNotTriggerReload() = runTest {
        val version = MutableStateFlow(0L)
        version.value = 3

        var reloads = 0
        val job = reloadOnSessionChange(version) { reloads++ }
        advanceUntilIdle()

        assertEquals("订阅时已有的版本不是变化，不能平白多拉一次", 0, reloads)
        job.cancel()
    }

    @Test
    fun changeAfterTheObserverIsAttachedTriggersReload() = runTest {
        val version = MutableStateFlow(0L)
        var reloads = 0
        val job = reloadOnSessionChange(version) { reloads++ }
        advanceUntilIdle()

        version.value = 1
        advanceUntilIdle()

        assertEquals(1, reloads)
        job.cancel()
    }

    /** 登出、再登录是两次身份变化，各自都要重载；只响应一次的实现不合格 */
    @Test
    fun everyChangeGetsItsOwnReload() = runTest {
        val version = MutableStateFlow(0L)
        var reloads = 0
        val job = reloadOnSessionChange(version) { reloads++ }
        advanceUntilIdle()

        version.value = 1
        advanceUntilIdle()
        version.value = 2
        advanceUntilIdle()

        assertEquals(2, reloads)
        job.cancel()
    }

    @Test
    fun rapidChangesCoalesceButNeverToZero() = runTest {
        val version = MutableStateFlow(0L)
        var reloads = 0
        val job = reloadOnSessionChange(version) { reloads++ }
        advanceUntilIdle()

        version.value = 1
        version.value = 2
        advanceUntilIdle()

        assertTrue("合并成一次可以，重载动作是幂等的；但一次都不能少", reloads >= 1)
        job.cancel()
    }

    @Test
    fun cancelledObserverStopsReloading() = runTest {
        val version = MutableStateFlow(0L)
        var reloads = 0
        val job = reloadOnSessionChange(version) { reloads++ }
        advanceUntilIdle()

        job.cancel()
        version.value = 1
        advanceUntilIdle()

        assertEquals(0, reloads)
    }
}
