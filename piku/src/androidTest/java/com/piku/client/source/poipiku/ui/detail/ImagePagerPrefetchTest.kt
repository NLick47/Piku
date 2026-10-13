package com.piku.client.source.poipiku.ui.detail
import com.piku.client.core.reader.rememberPagerWindowPrefetch
import com.piku.client.core.ui.detail.ImagePager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.size.Size as CoilSize
import com.piku.client.core.model.WorkDetail
import com.piku.client.core.ui.theme.PoipikuTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class ImagePagerPrefetchTest {

    @get:Rule
    val compose = createComposeRule()

    private val server = LocalImageServer()

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun nextPageIsFetchedBeforeTheSwipeAndReusedOnIt() {
        val urls = urls(6)
        val pageChanges = CopyOnWriteArraySet<Int>()
        val shown = CopyOnWriteArraySet<Int>()
        renderPager(urls, pageChanges, shown)

        // 三个请求是同一次窗口里连着发的，都得等它落地再断言
        compose.waitUntil(TIMEOUT_MS) { server.hits("/p1.jpg") > 0 }
        compose.waitUntil(TIMEOUT_MS) { server.hits("/p2.jpg") > 0 }
        compose.waitUntil(TIMEOUT_MS) { server.hits("/p3.jpg") > 0 }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = SingletonImageLoader.get(context)
        compose.waitUntil(TIMEOUT_MS) { loader.memoryCache?.get(MemoryCache.Key(urls[1])) != null }

        // 横滑落在图区里：图区最高只有屏高的 3/4，屏中央可能已经在它下面
        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(width * 0.9f, height * 0.2f),
                end = Offset(width * 0.1f, height * 0.2f),
                durationMillis = 300,
            )
        }
        compose.waitUntil(TIMEOUT_MS) { 1 in pageChanges }
        compose.waitUntil(TIMEOUT_MS) { 1 in shown }

        assertEquals("翻到第二张不该重新下载", 1, server.hits("/p1.jpg"))
        compose.waitForIdle()
    }

    /** 漫画几十页：静止时也只下窗口内那几张，不是整本 */
    @Test
    fun manyPageWorkPrefetchesOnlyTheWindow() {
        val urls = urls(12)
        val pageChanges = CopyOnWriteArraySet<Int>()
        val shown = CopyOnWriteArraySet<Int>()
        renderPager(urls, pageChanges, shown)

        compose.waitUntil(TIMEOUT_MS) { server.hits("/p1.jpg") > 0 }
        compose.waitUntil(TIMEOUT_MS) { server.hits("/p3.jpg") > 0 }
        compose.waitForIdle()

        assertEquals("窗口外不该被取", 0, server.hits("/p4.jpg"))
        assertEquals("最后一页更不该被取", 0, server.hits("/p11.jpg"))
    }

    /**
     * 窗口滑动时同一页会连着出现在相邻窗口里：前一次还在飞，就会再发一次（Coil 不合并 in-flight 请求）。
     * 这是现状的特征断言——哪天加了"在飞去重"，这里的期望要改成 1 次。
     */
    @Test
    fun aPageStillInFlightIsFetchedAgainByTheNextWindow() {
        server.responseDelayMs = 1_000
        val urls = urls(8)
        var current by mutableStateOf(0)
        compose.setContent {
            rememberPagerWindowPrefetch(urls, current, pageSizePx = CoilSize(600, 800))
        }

        // cur=0 的窗口把 p3 当"再往后两张"发出去，之后两个窗口里它还会以 +2、+1 的身份再出现
        compose.waitUntil(TIMEOUT_MS) { server.hits("/p3.jpg") > 0 }
        compose.runOnUiThread { current = 1 }
        compose.waitForIdle()
        compose.runOnUiThread { current = 2 }
        compose.waitForIdle()
        // p5 只在最后一个窗口里出现：等到它就说明三个窗口的请求都已经发出去并落地了
        compose.waitUntil(TIMEOUT_MS) { server.hits("/p5.jpg") > 0 }
        android.util.Log.d("PikuDiag", "testHits p3=${server.hits("/p3.jpg")} p2=${server.hits("/p2.jpg")} p4=${server.hits("/p4.jpg")}")

        assertTrue(
            "同一张在飞时被取了 ${server.hits("/p3.jpg")} 次（>=2 才算重复发请求）",
            server.hits("/p3.jpg") >= 2,
        )
    }

    /** 反过来：上一次已经下完的，再轮到它只走缓存，不会再打网络——问题只在"在飞"那一小段里 */
    @Test
    fun aCompletedPageIsNotFetchedAgain() {
        val urls = urls(8)
        val loader = SingletonImageLoader.get(InstrumentationRegistry.getInstrumentation().targetContext)
        var current by mutableStateOf(0)
        compose.setContent {
            rememberPagerWindowPrefetch(urls, current, pageSizePx = CoilSize(600, 800))
        }

        compose.waitUntil(TIMEOUT_MS) { server.hits("/p3.jpg") > 0 }
        compose.waitUntil(TIMEOUT_MS) { loader.diskHas(urls[3]) }
        compose.runOnUiThread { current = 1 }
        compose.waitForIdle()
        compose.runOnUiThread { current = 2 }
        compose.waitForIdle()
        compose.waitUntil(TIMEOUT_MS) { server.hits("/p2.jpg") > 0 }

        assertEquals("下完的那张不该再打网络", 1, server.hits("/p3.jpg"))
    }

    private fun ImageLoader.diskHas(url: String): Boolean =
        diskCache?.openSnapshot(url)?.use { true } == true

    private fun urls(count: Int): List<String> = (0 until count).map { "${server.baseUrl}/p$it.jpg" }

    private fun renderPager(
        urls: List<String>,
        pageChanges: MutableSet<Int>,
        shown: MutableSet<Int>,
    ) {
        compose.setContent {
            PoipikuTheme(darkTheme = false) {
                ImagePager(
                    detail = WorkDetail(
                        title = "test",
                        authorName = "author",
                        authorAvatarUrl = "",
                        categoryCd = -1,
                        categoryName = "",
                        imageUrls = urls,
                        tags = emptyList(),
                        r18 = false,
                    ),
                    dark = false,
                    fullBleed = true,
                    onImageClick = {},
                    onImageLongPress = {},
                    onWorkClick = { _, _, _ -> },
                    password = "",
                    onPasswordChange = {},
                    onPasswordSubmit = {},
                    passwordLoading = false,
                    onOpenNovelReader = {},
                    onPageChanged = { pageChanges += it },
                    onImageShown = { page, _ -> shown += page },
                )
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}

/** 只为这次测试供图：任意路径都回同一张 PNG，顺手记下每个路径被请求了几次 */
private class LocalImageServer : Closeable {

    private val png: ByteArray = run {
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.RED)
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    /** 端口交给系统挑；绑 IPv4 字面量，URL 那边用的就是 127.0.0.1（::1 连不上） */
    private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** 响应前故意拖一会儿：模拟"前一次还在飞"的那一小段（计数在请求到达时就加，不是在答完） */
    var responseDelayMs: Long = 0

    val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { respond(client) }
            }
        }
    }

    fun hits(path: String): Int = counts[path]?.get() ?: 0

    override fun close() {
        runCatching { socket.close() }
    }

    private fun respond(client: Socket) {
        runCatching {
            client.use { open ->
                val input = open.getInputStream().bufferedReader()
                val path = input.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                // 请求头读完再答，否则客户端还堵在写头上
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                }
                counts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
                if (responseDelayMs > 0) Thread.sleep(responseDelayMs)
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/png\r\n" +
                    "Content-Length: ${png.size}\r\n" +
                    "Connection: close\r\n\r\n"
                open.getOutputStream().apply {
                    write(header.toByteArray())
                    write(png)
                    flush()
                }
            }
        }
    }
}
