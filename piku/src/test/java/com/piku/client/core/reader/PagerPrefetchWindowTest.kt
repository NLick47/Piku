package com.piku.client.core.reader
import com.piku.client.core.reader.PagerPrefetchTarget
import com.piku.client.core.reader.pagerPrefetchWindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerPrefetchWindowTest {

    private fun urls(count: Int): List<String> = (1..count).map { "https://cdn.example.com/w_$it.jpg" }

    private fun memoryPages(targets: List<PagerPrefetchTarget>) =
        targets.filter { it.memory }.map { it.page }

    private fun diskPages(targets: List<PagerPrefetchTarget>) =
        targets.filterNot { it.memory }.map { it.page }

    /** 下一页连内存一起备好（翻过去零等待），再往后两张只写磁盘 */
    @Test
    fun nextPageIsWarmedInMemoryAndTheFollowingOnesOnDisk() {
        val window = pagerPrefetchWindow(urls(6), currentPage = 0)
        assertEquals(listOf(1), memoryPages(window))
        assertEquals(listOf(2, 3), diskPages(window))
    }

    /** 漫画几十页：窗口大小与总页数无关，只多下固定几张 */
    @Test
    fun windowDoesNotGrowWithPageCount() {
        val window = pagerPrefetchWindow(urls(40), currentPage = 10)
        assertEquals(4, window.size)
        assertEquals(listOf(11), memoryPages(window))
        assertEquals(listOf(12, 13, 9), diskPages(window))
    }

    /** 首尾不越界：后面没页了就少下，不绕回别的页；单图作品整个窗口是空的 */
    @Test
    fun windowStopsAtTheListEdges() {
        val nearEnd = pagerPrefetchWindow(urls(6), currentPage = 4)
        assertEquals(listOf(5), memoryPages(nearEnd))
        assertEquals(listOf(3), diskPages(nearEnd))

        // 最后一页只剩回翻那一张，往前不越界也不回绕
        val lastPage = pagerPrefetchWindow(urls(6), currentPage = 5)
        assertTrue(memoryPages(lastPage).isEmpty())
        assertEquals(listOf(4), diskPages(lastPage))

        assertTrue(pagerPrefetchWindow(urls(1), currentPage = 0).isEmpty())
    }

    /** 回翻那一张只写磁盘：省钱，翻回去也不用等网络 */
    @Test
    fun previousPageIsWarmedOnDiskOnly() {
        val window = pagerPrefetchWindow(urls(6), currentPage = 3)
        assertEquals(listOf(4), memoryPages(window))
        assertEquals(listOf(5, 2), diskPages(window))
    }

    /** 同一个 URL 只预热一次（看图器页表用末页兜底时会出现重复页） */
    @Test
    fun duplicateImagesArePrefetchedOnce() {
        val same = "https://cdn.example.com/same.jpg"
        val window = pagerPrefetchWindow(List(4) { same }, currentPage = 0)
        assertEquals(listOf(1), memoryPages(window))
        assertTrue(diskPages(window).isEmpty())
    }

    /** 动图不预热：页面用的是带 #anim 的独立内存键，预热进去也对不上 */
    @Test
    fun animatedImagesAreSkipped() {
        val list = urls(6).toMutableList()
        list[1] = "https://cdn.example.com/w_2.gif_640.jpg"
        val window = pagerPrefetchWindow(list, currentPage = 0)
        assertTrue(memoryPages(window).isEmpty())
        assertEquals(listOf(2, 3), diskPages(window))
    }
}
