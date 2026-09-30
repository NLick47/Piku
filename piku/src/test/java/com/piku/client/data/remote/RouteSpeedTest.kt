package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteSpeedTest {

    private var now = 0L
    private val speed = RouteSpeed(now = { now })

    @Test
    fun rateComesFromTheBytesActuallyTransferred() {
        speed.record(relay = false, bytes = 1_048_576, elapsedMs = 4_000)

        assertEquals(262_144L, speed.rate(relay = false))
    }

    @Test
    fun estimateUsesTheRateOfThatRouteOnly() {
        assertNull(speed.estimatedMs(bytes = 1_048_576, relay = false))

        speed.record(relay = false, bytes = 1_048_576, elapsedMs = 4_000)

        assertEquals(4_000L, speed.estimatedMs(bytes = 1_048_576, relay = false))
        assertNull("另一条路没样本，不能拿这条的读数去估", speed.estimatedMs(bytes = 1_048_576, relay = true))
    }

    @Test
    fun smallImagesAreNotSamples() {
        // 缩略图几十毫秒就回来，耗时几乎全是往返延迟：拿它算带宽会把"慢"算成"快"
        speed.record(relay = false, bytes = 38_371, elapsedMs = 300)

        assertNull(speed.rate(relay = false))
    }

    @Test
    fun staleSamplesStopCounting() {
        speed.record(relay = false, bytes = 1_048_576, elapsedMs = 1_000)
        assertEquals(1_048_576L, speed.rate(relay = false))

        now += RouteSpeed.SAMPLE_WINDOW_MS + 1

        assertNull("换了网络/位置之后旧结论不该继续生效", speed.rate(relay = false))
    }

    @Test
    fun theTwoRoutesAreMeasuredSeparately() {
        speed.record(relay = false, bytes = 1_048_576, elapsedMs = 20_000)
        speed.record(relay = true, bytes = 1_048_576, elapsedMs = 4_000)

        assertEquals(true, speed.rate(relay = true)!! > speed.rate(relay = false)!!)
    }

    @Test
    fun onlyTheRecentSamplesAreKept() {
        repeat(RouteSpeed.MAX_SAMPLES + 3) { index ->
            speed.record(relay = false, bytes = 200_000, elapsedMs = 1_000)
            now += 1
        }

        assertEquals(RouteSpeed.MAX_SAMPLES, speed.recent().size)
    }

    /** record 在图片完成线程、rate 在取图决策线程：并发打摆不能炸（无同步时这里会抛 CME/数组越界） */
    @Test
    fun concurrentRecordAndRateDoNotCorruptState() {
        val clock = java.util.concurrent.atomic.AtomicLong(0)
        val shared = RouteSpeed(now = { clock.get() }, minBytes = 1, windowMs = Long.MAX_VALUE, capacity = 64)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(8)
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        repeat(4) {
            pool.submit {
                try {
                    start.await()
                    repeat(20_000) { i ->
                        clock.incrementAndGet()
                        shared.record(relay = i % 2 == 0, bytes = 200_000, elapsedMs = 100)
                    }
                } catch (t: Throwable) {
                    failures += t
                } finally {
                    done.countDown()
                }
            }
        }
        repeat(4) {
            pool.submit {
                try {
                    start.await()
                    repeat(20_000) {
                        clock.incrementAndGet()
                        shared.rate(relay = false)
                        shared.rate(relay = true)
                    }
                } catch (t: Throwable) {
                    failures += t
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        org.junit.Assert.assertTrue("测试本身超时", done.await(60, java.util.concurrent.TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals("并发读写不该抛异常：$failures", emptyList<Throwable>(), failures.toList())
    }
}
