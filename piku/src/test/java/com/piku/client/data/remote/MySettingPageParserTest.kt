package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MySettingPageParserTest {

    @Test
    fun `parse keeps avatar url that already carries a small size suffix`() {
        val html = """<section class="PreviewImg" src="https://cdn.poipiku.com/014189264/profile_20260920230319.png_120.jpg">"""

        assertEquals(
            "https://cdn.poipiku.com/014189264/profile_20260920230319.png_120.jpg",
            MySettingPageParser.parseAvatarUrl(html),
        )
    }

    @Test
    fun `parse appends small suffix when avatar url has no trailing digits and extension`() {
        val html = """<section class="PreviewImg" src="https://cdn.poipiku.com/014189264/profile.png">"""

        assertEquals(
            "https://cdn.poipiku.com/014189264/profile.png_120.jpg",
            MySettingPageParser.parseAvatarUrl(html),
        )
    }

    /**
     * 设置页实测给的就是这种带时间戳的 URL（profile_20260816063418.jpeg）。
     * 时间戳不算尺寸后缀——算的话就会去下 289935 字节的原图，
     * 而补成 `_120.jpg` 只有 6525 字节，且列表页 22 个头像全是这个形态
     */
    @Test
    fun `parse appends small suffix to a timestamped file name`() {
        val html = """<section class="PreviewImg" src="https://cdn.poipiku.com/014189264/profile_20260816063418.jpeg">"""

        assertEquals(
            "https://cdn.poipiku.com/014189264/profile_20260816063418.jpeg_120.jpg",
            MySettingPageParser.parseAvatarUrl(html),
        )
    }

    /** 别的已知尺寸后缀也要认，不能重复追加 */
    @Test
    fun `parse keeps avatar url that already carries another known size suffix`() {
        val html = """<section class="PreviewImg" src="https://cdn.poipiku.com/014189264/profile_20260816063418.png_360.jpg">"""

        assertEquals(
            "https://cdn.poipiku.com/014189264/profile_20260816063418.png_360.jpg",
            MySettingPageParser.parseAvatarUrl(html),
        )
    }

    @Test
    fun `parse returns null avatar when page has no preview image`() {
        assertNull(MySettingPageParser.parseAvatarUrl("<html><body>plain</body></html>"))
    }

    @Test
    fun `parse returns null for blank html`() {
        assertNull(MySettingPageParser.parseAvatarUrl(""))
    }
}
