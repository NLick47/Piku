package com.piku.client.data.repository

import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavBackupPathTest {

    private fun poipiku(workId: String) = WorkKey(WorkSource.POIPIKU, workId)

    private fun pixiv(workId: String) = WorkKey(WorkSource.PIXIV, workId)

    @Test
    fun imageAndTextPathsCarrySourceAndIndex() {
        assertEquals(
            "piku/默认收藏夹/image/POIPIKU_123_0.jpg",
            WebDavBackupPath.image("默认收藏夹", poipiku("123"), index = 0, ext = "jpg"),
        )
        assertEquals(
            "piku/默认收藏夹/text/POIPIKU_123.txt",
            WebDavBackupPath.text("默认收藏夹", poipiku("123")),
        )
    }

    @Test
    fun sameWorkIdOnDifferentSourcesNeverCollides() {
        assertNotEquals(
            WebDavBackupPath.image("A", poipiku("123"), index = 0, ext = "jpg"),
            WebDavBackupPath.image("A", pixiv("123"), index = 0, ext = "jpg"),
        )
        assertNotEquals(
            WebDavBackupPath.text("A", poipiku("123")),
            WebDavBackupPath.text("A", pixiv("123")),
        )
    }

    @Test
    fun pagesOfOneWorkStayDistinct() {
        assertNotEquals(
            WebDavBackupPath.image("A", poipiku("123"), index = 0, ext = "jpg"),
            WebDavBackupPath.image("A", poipiku("123"), index = 1, ext = "png"),
        )
    }

    @Test
    fun folderNameStaysOnePathSegment() {
        // 夹名叫 A/B：直接拼进去会多出一层目录，备份散到别处
        val path = WebDavBackupPath.image("A/B", poipiku("123"), index = 0, ext = "jpg")

        assertEquals("piku/A_B/image/POIPIKU_123_0.jpg", path)
        assertEquals("piku + 夹 + image + 文件 四段", 4, path.split('/').size)
    }

    @Test
    fun dotSegmentsCannotEscapeThePikuRoot() {
        assertEquals("piku/_/image/POIPIKU_123_0.jpg", WebDavBackupPath.image("..", poipiku("123"), 0, "jpg"))
        assertEquals("piku/_/text/POIPIKU_123.txt", WebDavBackupPath.text(".", poipiku("123")))
        assertTrue(
            "任何一段都不能是 ..",
            WebDavBackupPath.image("../x", poipiku("123"), 0, "jpg").split('/').none { it == ".." },
        )
    }

    @Test
    fun urlSpecialCharsCannotBreakThePath() {
        // ? 会截断成 query、# 会被当 fragment、% 会让 URL 解析失败、控制字符非法
        val path = WebDavBackupPath.image("a?b#c%d\u0007", poipiku("123"), 0, "jpg")

        assertEquals("piku/a_b_c_d_/image/POIPIKU_123_0.jpg", path)
    }

    @Test
    fun ordinaryNamesAreKeptAsIs() {
        // 中文/空格/标点照旧：不能因为这次净化让既有备份的目录全体改名
        assertEquals(
            "piku/默认收藏夹 2026/image/POIPIKU_123_0.jpg",
            WebDavBackupPath.image("默认收藏夹 2026", poipiku("123"), 0, "jpg"),
        )
        assertEquals(
            "piku/R-18 (新)/text/POIPIKU_123.txt",
            WebDavBackupPath.text("R-18 (新)", poipiku("123")),
        )
    }
}
