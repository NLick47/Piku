package com.piku.client.core.network.image

import com.piku.client.core.network.dns.DoHDns
import com.piku.client.core.network.http.FakeCall
import com.piku.client.core.network.http.FakeChain
import com.piku.client.core.network.http.NetworkRuntime
import com.piku.client.core.network.http.okResponse
import com.piku.client.core.storage.prefs.InMemorySharedPreferences
import com.piku.client.core.storage.prefs.SettingsRepository
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NativeImageTransportTest {

    private var now = 0L
    private val runtime = NetworkRuntime(now = { now }, monotonicNow = { now }, sleeper = {})

    private val request = Request.Builder()
        .url("https://${ImageUpstream.POIPIKU.host}/000377568/013349244.png_360.jpg")
        .build()

    private fun transport(fetch: (NativeImageSpec) -> NativeImageResponse) =
        NativeImageTransport("Piku/test", runtime, fetch)

    private fun NativeImageTransport.fetch(request: Request): NativeOutcome = fetch(request, budgetMs = 4_000)

    private fun image(spec: NativeImageSpec) = NativeImageResponse(
        status = 200,
        headers = listOf("content-type" to "image/jpeg"),
        body = ByteArray(16),
    )

    private fun controller(): ImageRouteController =
        ImageRouteController(
            SettingsRepository(InMemorySharedPreferences()),
            InMemorySharedPreferences(),
            runtime,
        )

    @Test
    fun firstAddressFailureFallsThroughToTheNextOne() {
        val tried = mutableListOf<NativeImageSpec>()
        val fallback = transport { spec ->
            tried += spec
            if (tried.size == 1) throw IOException("blackhole") else image(spec)
        }

        val outcome = fallback.fetch(request)

        assertTrue(outcome is NativeOutcome.Ok)
        assertEquals(2, tried.size)
        assertTrue("必须换到下一个地址", tried[0].ip != tried[1].ip)
        // 带点 SNI 是整条通道的前提，退化回裸域名会让它悄悄失效
        assertTrue(tried[0].url, tried[0].url.startsWith("https://cdn.poipiku.com./"))
        // 与常规线路的 RefererInterceptor 同口径：poipiku CDN 认本站地址
        assertTrue(
            "原生请求必须带本站 Referer",
            tried[0].headers.any {
                it.first.equals("referer", ignoreCase = true) &&
                    it.second == "https://cdn.poipiku.com/000377568/013349244.png_360.jpg"
            },
        )
    }

    @Test
    fun zeroBudgetIsSkippedNotFailed() {
        var calls = 0
        val transport = transport { spec ->
            calls++
            image(spec)
        }

        val outcome = transport.fetch(request, budgetMs = 0)

        assertEquals(NativeOutcome.Skipped, outcome)
        assertEquals("预算不够就不该开连接", 0, calls)
    }

    @Test
    fun rescueWithoutBudgetDoesNotMarkTheRouteBroken() {
        now = 100_000L
        val controller = controller()
        var nativeCalls = 0
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { spec ->
                nativeCalls++
                image(spec)
            },
        )
        // 直连把 29s 都吃掉了：留给原生的时间不足一次最小尝试
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("connect timeout")),
            onProceed = { now += 29_000L },
        )

        runCatching { interceptor.intercept(chain) }

        assertEquals("没时间就别开原生连接", 0, nativeCalls)
        assertTrue("没试过就不能算它失败", controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun budgetBelowOneAttemptIsSkippedNotFailed() {
        var calls = 0
        val transport = transport { spec ->
            calls++
            image(spec)
        }

        // 预算大于 0 但不够一次最小尝试：连循环都进不去，更不能算线路故障
        val outcome = transport.fetch(request, budgetMs = 500)

        assertEquals(NativeOutcome.Skipped, outcome)
        assertEquals(0, calls)
    }

    @Test
    fun allAddressesFailingReportsFailed() {
        var calls = 0
        val fallback = transport { _ ->
            calls++
            throw IOException("down")
        }

        assertEquals(NativeOutcome.Failed, fallback.fetch(request))
        assertTrue("应把候选地址都试一遍", calls > 1)
    }

    @Test
    fun nonPoipikuHostIsSkipped() {
        val fallback = transport { spec -> image(spec) }
        val pixiv = Request.Builder()
            .url("https://${ImageUpstream.PIXIV.host}/img-master/a.jpg")
            .build()

        assertEquals(NativeOutcome.Skipped, fallback.fetch(pixiv))
    }

    @Test
    fun nonUpstreamHostsPassThroughWithoutTouchingNative() {
        // AO3 等非受管域名与这条原生通道无关：拦截器第一行就放行
        var nativeCalls = 0
        val interceptor = ImageRelayInterceptor(
            controller(),
            runtime = runtime,
            nativeTransport = transport { spec ->
                nativeCalls++
                image(spec)
            },
        )
        val ao3 = Request.Builder().url("https://archiveofourown.org/works/1").build()
        val chain = FakeChain(ao3, FakeCall(ao3), listOf(okResponse(ao3)))

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(0, nativeCalls)
        assertEquals(listOf("archiveofourown.org"), chain.hosts)
    }

    @Test
    fun nativeRouteTakesPrecedenceOverTheBaseRoute() {
        // 翻转有 20s 冷却，起点挪开一点免得被拦住（那是另一条测试管的）
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        assertEquals(ImageRoute.RELAY, controller.route(ImageUpstream.POIPIKU))

        controller.markNativeSucceeded(ImageUpstream.POIPIKU)

        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
        assertFalse(controller.useRelay(ImageUpstream.POIPIKU))
        // 原生只是 poipiku 这条上游的结论，不该带偏 pixiv
        assertEquals(ImageRoute.DIRECT, controller.route(ImageUpstream.PIXIV))
    }

    @Test
    fun nativeFailureReturnsToTheBaseRouteAndBlocksImmediateRetry() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)

        controller.markNativeFailed(ImageUpstream.POIPIKU)

        assertEquals(ImageRoute.RELAY, controller.route(ImageUpstream.POIPIKU))
        assertFalse("冷却期内不该再拿原生兜底", controller.canTryNative(ImageUpstream.POIPIKU))

        now += 5_001L
        assertTrue(controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun bothRoutesFailingArmsNativeEvenBeforeItIsProven() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)

        controller.markRelayFailed(ImageUpstream.POIPIKU)

        assertEquals(
            "直连与中转都不通时应该先试原生，而不是每张图再白等两轮",
            ImageRoute.NATIVE,
            controller.route(ImageUpstream.POIPIKU),
        )
        assertTrue(controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun nativeFailureSuppressesTheTrialUntilItsBlockExpires() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        controller.markNativeFailed(ImageUpstream.POIPIKU)

        controller.markRelayFailed(ImageUpstream.POIPIKU)
        assertEquals("静默期内不该再试原生", ImageRoute.RELAY, controller.route(ImageUpstream.POIPIKU))

        now += 5_001L
        controller.markRelayFailed(ImageUpstream.POIPIKU)
        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun nativeRouteDoesNotDowngradeDetailImages() {
        now = 100_000L
        val controller = controller()
        assertFalse("没有测速样本时停在打底档", controller.worthFullImageInline(ImageUpstream.POIPIKU))

        controller.markNativeSucceeded(ImageUpstream.POIPIKU)

        assertTrue("原生是唯一通的线路，不该把详情降成糊图", controller.worthFullImageInline(ImageUpstream.POIPIKU))
    }

    @Test
    fun bypassProbeFailsWithItsOwnErrorAndNeverTouchesNative() {
        now = 100_000L
        val controller = controller()
        var nativeCalls = 0
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { spec ->
                nativeCalls++
                image(spec)
            },
        )
        val probe = Request.Builder()
            .url("https://${ImageUpstream.POIPIKU.host}/assets/img/poipiku_icon_512x512_2.png")
            .header(ImageRelayInterceptor.BYPASS_HEADER, "1")
            .build()
        val chain = FakeChain(
            probe,
            FakeCall(probe),
            listOf(IOException("connect timeout")),
            onProceed = { now += 200L },
        )

        val thrown = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        // 探测失败就是"直连不可用"的结论：原生兜底会把它篡改成"直连可用"
        assertEquals("connect timeout", thrown?.message)
        assertEquals(0, nativeCalls)
        assertEquals(ImageRoute.DIRECT, controller.route(ImageUpstream.POIPIKU))
        assertTrue(controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun nativeSkippedFallsBackToTheBaseRouteNotDirect() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        // 原生库缺失 = 原生通道被跳过，不是线路失败
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { throw UnsatisfiedLinkError("libpiku_ech not found") },
        )
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(okResponse(request, code = 404), IOException("direct timeout")),
            onProceed = { now += 100L },
        )

        val response = interceptor.intercept(chain)

        // 基底是中转：原生跳过后先试中转，不能一头撞上已判死的直连
        assertEquals(404, response.code)
        assertEquals(
            listOf("pic-relay.cyou", ImageUpstream.POIPIKU.host),
            chain.hosts,
        )
    }

    @Test
    fun pixivKeepsDirectFallbackWithoutThePhantomNativeReserve() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.PIXIV, directOk = false)
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { image(it) },
        )
        val pixiv = Request.Builder()
            .url("https://${ImageUpstream.PIXIV.host}/img-master/a.jpg")
            .build()
        var attempt = 0
        val chain = FakeChain(
            pixiv,
            FakeCall(pixiv),
            listOf(IOException("relay timeout"), okResponse(pixiv)),
            onProceed = { now += if (attempt++ == 0) 3_000L else 10_000L },
        )

        val response = interceptor.intercept(chain)

        // pixiv 没有原生通道：4.5s 的原生预留不该吃掉直连重试的预算
        assertEquals(200, response.code)
        assertEquals(
            listOf("pic-relay.cyou", ImageUpstream.PIXIV.host),
            chain.hosts,
        )
    }

    @Test
    fun relayNotFoundIsReturnedWhenThereIsNoBudgetForDirect() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { image(it) },
        )
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(okResponse(request, code = 404)),
            onProceed = { now += 600L },
        )

        val response = interceptor.intercept(chain)

        // 中继已经回了 404；直连预算不够时不能把这个应答丢掉换成异常
        assertEquals(404, response.code)
        assertEquals(listOf("pic-relay.cyou"), chain.hosts)
    }

    @Test
    fun relayNotFoundStillTriesDirectWhenBudgetAllows() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { image(it) },
        )
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(okResponse(request, code = 404), okResponse(request)),
            onProceed = { now += 100L },
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(
            listOf("pic-relay.cyou", ImageUpstream.POIPIKU.host),
            chain.hosts,
        )
    }

    @Test
    fun cancelledRequestsDoNotFlipTheRoute() {
        now = 100_000L
        val controller = controller()
        val interceptor = ImageRelayInterceptor(controller, runtime = runtime)
        // 用户快速划走：Coil 取消请求，异常是 IOException("Canceled")
        val chain = FakeChain(
            request,
            FakeCall(request, canceled = true),
            listOf(IOException("Canceled")),
        )

        runCatching { interceptor.intercept(chain) }

        assertEquals("取消不是线路故障，不该把直连判死", ImageRoute.DIRECT, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun directTimeoutLeavesTheRestOfTheBudgetToNative() {
        now = 100_000L
        var nativeCalls = 0
        val interceptor = ImageRelayInterceptor(
            controller(),
            runtime = runtime,
            nativeTransport = transport { spec ->
                nativeCalls++
                image(spec)
            },
        )
        // 直连把两条路由的超时都吃满（2 × 5s），这时 callTimeout 只剩 5s
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("connect timed out")),
            onProceed = { now += 10_000L },
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals("直连吃满 10s 后不该再试中继", listOf(ImageUpstream.POIPIKU.host), chain.hosts)
        assertEquals(1, nativeCalls)
    }

    @Test
    fun relayTimeoutLeavesTheRestOfTheBudgetToNative() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        var nativeCalls = 0
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { spec ->
                nativeCalls++
                image(spec)
            },
        )
        // 中继先失败（5s），此时再试直连（10s）正好把总超时吃光
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("relay timeout")),
            onProceed = { now += 5_000L },
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(listOf("pic-relay.cyou"), chain.hosts)
        assertEquals(1, nativeCalls)
    }

    @Test
    fun serverErrorPassesThroughWhenNativeCannotRescue() {
        now = 100_000L
        val controller = controller()
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { NativeImageResponse(503, emptyList(), ByteArray(0)) },
        )
        val chain = FakeChain(request, FakeCall(request), listOf(okResponse(request, code = 503)))

        val response = interceptor.intercept(chain)

        // 原生也救不了：把服务端这个 503 原样交给调用方，而不是变成异常
        assertEquals(503, response.code)
        assertFalse("原生同样失败，应进入静默期", controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun originalFailurePropagatesWhenNativeCannotRescue() {
        now = 100_000L
        val controller = controller()
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { throw IOException("native down") },
        )
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("connect timeout"), IOException("relay down")),
            onProceed = { now += 200L },
        )

        val thrown = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        // 两条常规线路都失败、原生也失败：透出**原始的直连错误**，不能被预算异常替换
        assertTrue(thrown is IOException)
        assertEquals("connect timeout", thrown?.message)
    }

    @Test
    fun nativeObjectRejectionIsReturnedWithoutTouchingTheRoute() {
        now = 100_000L
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        var calls = 0
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { spec ->
                calls++
                NativeImageResponse(403, listOf("content-type" to "application/xml"), ByteArray(8))
            },
        )
        val chain = FakeChain(request, FakeCall(request), listOf(okResponse(request)))

        val response = interceptor.intercept(chain)

        // 图没了（CloudFront AccessDenied）：直接回 403，不要再白跑一轮常规线路
        assertEquals(403, response.code)
        assertTrue(chain.hosts.isEmpty())
        assertEquals(1, calls)
        assertTrue("对象级拒绝不该把原生线路判死", controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun nativeServerErrorStillCountsAsRouteTrouble() {
        var calls = 0
        val transport = transport { spec ->
            calls++
            NativeImageResponse(503, emptyList(), ByteArray(0))
        }

        val outcome = transport.fetch(request)

        assertEquals(NativeOutcome.Failed, outcome)
        assertTrue("5xx 要换完候选地址再判线路故障", calls > 1)
    }

    @Test
    fun nativeRouteExpiresBackToTheBaseRoute() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))

        now += 121_000L

        assertEquals("粘性到期后要回到基底线路，不能一直占着原生", ImageRoute.RELAY, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun missingNativeLibraryFallsBackToRegularRoutesWithoutCrashing() {
        now = 100_000L
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { throw UnsatisfiedLinkError("libpiku_ech not found") },
        )
        val chain = FakeChain(request, FakeCall(request), listOf(okResponse(request)))

        val response = interceptor.intercept(chain)

        // 库缺失只是这条通道不可用，不是线路故障：常规线路照常兜住，也不能崩
        assertEquals(200, response.code)
        assertEquals(listOf(ImageUpstream.POIPIKU.host), chain.hosts)
        assertTrue(controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun transportMalfunctionDoesNotEscapeOrTouchTheRoute() {
        now = 100_000L
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        // 帧损坏在 Kotlin 侧表现为 RuntimeException（BufferUnderflow 等）
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { throw IllegalStateException("BufferUnderflowException") },
        )
        val chain = FakeChain(request, FakeCall(request), listOf(okResponse(request)))

        val response = interceptor.intercept(chain)

        // 通道故障按 Skipped 处理：落回常规线路取图，不把线路判死
        assertEquals(200, response.code)
        assertEquals(listOf(ImageUpstream.POIPIKU.host), chain.hosts)
        assertTrue(controller.canTryNative(ImageUpstream.POIPIKU))
        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun managedStillRunsAfterNativeFails() {
        now = 100_000L
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { throw IOException("native down") },
        )
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(okResponse(request)),
            onProceed = { now += 300L },
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals("原生失败后仍要回到常规线路", listOf(ImageUpstream.POIPIKU.host), chain.hosts)
    }

    @Test
    fun fastFailureStillTriesBothRegularRoutes() {
        now = 100_000L
        var nativeCalls = 0
        val interceptor = ImageRelayInterceptor(
            controller(),
            runtime = runtime,
            nativeTransport = transport { spec ->
                nativeCalls++
                image(spec)
            },
        )
        // 直连快速被拒（200ms）：预算够，仍应完整试一遍中继
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("connection reset"), okResponse(request)),
            onProceed = { now += 200L },
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(
            "预算充足时不能因为要留余量就跳过中继",
            listOf(ImageUpstream.POIPIKU.host, "pic-relay.cyou"),
            chain.hosts,
        )
        assertEquals(0, nativeCalls)
    }

    @Test
    fun nativeRouteSkipsDirectAndRelay() {
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        val interceptor = ImageRelayInterceptor(
            controller,
            nativeTransport = transport { image(it) },
        )
        val chain = FakeChain(request, FakeCall(request), listOf(okResponse(request)))

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertTrue("原生线上的图不该再发起 HTTP 请求", chain.hosts.isEmpty())
    }

    @Test
    fun gatewayFailureIsRescuedByNativeAndPromotesIt() {
        val controller = controller()
        val interceptor = ImageRelayInterceptor(
            controller,
            nativeTransport = transport { image(it) },
        )
        val chain = FakeChain(request, FakeCall(request), listOf(okResponse(request, code = 403)))

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun missingImageIsNotWorthANativeAttempt() {
        val controller = controller()
        var calls = 0
        val interceptor = ImageRelayInterceptor(
            controller,
            nativeTransport = transport { spec ->
                calls++
                image(spec)
            },
        )
        val chain = FakeChain(request, FakeCall(request), listOf(okResponse(request, code = 404)))

        val response = interceptor.intercept(chain)

        assertEquals(404, response.code)
        assertEquals("图真的没了，换条路也一样", 0, calls)
    }

    @Test
    fun silentPeriodDoesNotStartABudgetBustingAttemptAndRecovers() {
        now = 100_000L
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        var nativeOk = false
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { spec ->
                if (!nativeOk) {
                    now += 6_000L
                    throw IOException("native down")
                }
                image(spec)
            },
        )
        val first = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("direct timeout")),
            onProceed = { now += 10_000L },
        )

        runCatching { interceptor.intercept(first) }

        assertTrue("原生失败后不该再发起必然超时的常规线路", first.hosts.isEmpty())
        assertFalse(controller.canTryNative(ImageUpstream.POIPIKU))

        nativeOk = true
        now += 5_001L
        val second = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("direct timeout"), IOException("relay timeout")),
            onProceed = { now += 200L },
        )

        val response = interceptor.intercept(second)

        assertEquals(200, response.code)
        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun cancelledCallDoesNotStartNativeFetch() {
        now = 100_000L
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        var calls = 0
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { spec ->
                calls++
                image(spec)
            },
        )
        val chain = FakeChain(
            request,
            FakeCall(request, canceled = true),
            listOf(IOException("Canceled")),
        )

        runCatching { interceptor.intercept(chain) }

        assertEquals(0, calls)
        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun staleNativeFailureDoesNotWipeANewerSuccess() {
        now = 100_000L
        val controller = controller()
        val staleEpoch = controller.nativeEpoch(ImageUpstream.POIPIKU)
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)

        assertFalse(controller.markNativeFailed(ImageUpstream.POIPIKU, staleEpoch))

        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
        assertTrue(controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun currentEpochFailureStillBlocksNative() {
        now = 100_000L
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        val epoch = controller.nativeEpoch(ImageUpstream.POIPIKU)

        assertTrue(controller.markNativeFailed(ImageUpstream.POIPIKU, epoch))

        assertEquals(ImageRoute.DIRECT, controller.route(ImageUpstream.POIPIKU))
        assertFalse(controller.canTryNative(ImageUpstream.POIPIKU))
    }

    @Test
    fun relayFailureDoesNotShortenNativeStickiness() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        now += 5_000L
        controller.markRelayFailed(ImageUpstream.POIPIKU)

        now += 35_000L

        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun budgetExhaustionSurfacesTheOriginalFailure() {
        now = 100_000L
        val interceptor = ImageRelayInterceptor(
            controller(),
            runtime = runtime,
            nativeTransport = transport { _ -> throw IOException("native down") },
        )
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("connect timeout")),
            onProceed = { now += 10_000L },
        )

        val thrown = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        assertEquals("connect timeout", thrown?.message)
    }

    @Test
    fun relayRejectionSurvivesDirectFailure() {
        now = 100_000L
        val controller = controller()
        controller.applyProbe(ImageUpstream.POIPIKU, directOk = false)
        val interceptor = ImageRelayInterceptor(controller, runtime = runtime)
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(okResponse(request, code = 404), IOException("direct timeout")),
            onProceed = { now += 100L },
        )

        val response = interceptor.intercept(chain)

        assertEquals(404, response.code)
        assertEquals(
            listOf("pic-relay.cyou", ImageUpstream.POIPIKU.host),
            chain.hosts,
        )
    }

    @Test
    fun nativeObjectRejectionWinsOverTheOriginalFailure() {
        now = 100_000L
        val interceptor = ImageRelayInterceptor(
            controller(),
            runtime = runtime,
            nativeTransport = transport { _ ->
                NativeImageResponse(403, listOf("content-type" to "application/xml"), ByteArray(8))
            },
        )
        val chain = FakeChain(
            request,
            FakeCall(request),
            listOf(IOException("connection reset"), IOException("relay down")),
            onProceed = { now += 100L },
        )

        val response = interceptor.intercept(chain)

        assertEquals(403, response.code)
    }

    @Test
    fun redirectIsNotARouteFailure() {
        now = 100_000L
        val controller = controller()
        controller.markNativeSucceeded(ImageUpstream.POIPIKU)
        var calls = 0
        val interceptor = ImageRelayInterceptor(
            controller,
            runtime = runtime,
            nativeTransport = transport { _ ->
                calls++
                NativeImageResponse(302, emptyList(), ByteArray(4))
            },
        )
        val chain = FakeChain(request, FakeCall(request), listOf(okResponse(request)))

        val response = interceptor.intercept(chain)

        assertEquals(302, response.code)
        assertEquals(1, calls)
        assertTrue(chain.hosts.isEmpty())
        assertTrue(controller.canTryNative(ImageUpstream.POIPIKU))
        assertEquals(ImageRoute.NATIVE, controller.route(ImageUpstream.POIPIKU))
    }

    @Test
    fun rateLimitTriesEveryAddressBeforeGivingUp() {
        var calls = 0
        val outcome = transport { _ ->
            calls++
            NativeImageResponse(429, emptyList(), ByteArray(4))
        }.fetch(request, budgetMs = 4_000)

        assertEquals(NativeOutcome.Failed, outcome)
        assertEquals(DoHDns.STATIC_ADDRESSES.getValue(ImageUpstream.POIPIKU.host).size, calls)
    }

    @Test
    fun everyAddressGetsItsOwnFullSlice() {
        now = 100_000L
        val slices = mutableListOf<Int>()
        transport { spec ->
            slices += spec.timeoutMs
            now += 300L
            throw IOException("blackhole")
        }.fetch(request, budgetMs = 29_000)

        assertTrue(slices.size > 1)
        assertTrue("每个地址都该独立给足 3s，不能为后面的地址预留后只剩 1s", slices.all { it == 3_000 })
    }

    @Test
    fun anAddressLateInTheTableIsStillReachable() {
        now = 100_000L
        val total = DoHDns.STATIC_ADDRESSES.getValue(ImageUpstream.POIPIKU.host).size
        var calls = 0
        val outcome = transport { spec ->
            calls++
            if (calls < total) throw IOException("blackhole") else image(spec)
        }.fetch(request, budgetMs = 4_000)

        assertTrue("表尾地址也必须能轮到", outcome is NativeOutcome.Ok)
        assertEquals(total, calls)
    }

}
