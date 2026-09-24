package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PopularTagParserTest {


    private fun readResource(name: String): String {
        val res = javaClass.classLoader?.getResource(name)?.readText()
        assumeTrue("fixture $name 缺失，跳过（快照仅本地）", res != null)
        return res!!
    }

    @Test
    fun `parses popular tags from real page`() {
        val tags = PopularTagParser.parse(readResource("poptag.html"))
        assertEquals(15, tags.size)
        val names = tags.map { it.name }
        assertTrue(names.contains("類司R18"))
        assertTrue(names.contains("类司R18"))
        assertTrue(names.contains("類司"))
        assertTrue(names.contains("亮懿"))
        assertTrue(names.contains("oc"))
        assertTrue(names.contains("女の子"))
        assertTrue(names.contains("オリジナル"))
        assertTrue(names.contains("オリキャラ"))
        assertNotNull(tags.find { it.name == "oc" }?.genreId)
        assertNotNull(tags.find { it.name == "oc" }?.iconUrl)
    }

    @Test
    fun `parses works from tag search page with same parser as feed`() {
        val works = NewArrivalParser.parse(readResource("tagsearch.html"))
        assertEquals(48, works.size)
        val first = works.first()
        assertTrue(first.authorName.isNotBlank())
        assertTrue(first.thumbnailUrl.contains("cdn.poipiku.com"))
        assertTrue(first.title.isNotBlank())
    }

    // 下面用内联 HTML：上面两条依赖快照，而快照不在仓库，本机之外一律被 assumeTrue 跳过

    @Test
    fun `parses a tag card with icon and genre id`() {
        val tags = PopularTagParser.parse(
            """
            <section class="CategoryListItem">
              <h2 class="GenreNameOrg">#オリジナル</h2>
              <div class="GenreImage" style="background-image: url('https://cdn.poipiku.com/genre_12_icon.png')"></div>
            </section>
            """.trimIndent(),
        )

        assertEquals(1, tags.size)
        assertEquals("オリジナル", tags.single().name)
        assertEquals(12L, tags.single().genreId)
        assertEquals("https://cdn.poipiku.com/genre_12_icon.png", tags.single().iconUrl)
    }

    @Test
    fun `keeps card order and skips cards without a name`() {
        val tags = PopularTagParser.parse(
            """
            <section class="CategoryListItem"><h2 class="GenreNameOrg">A</h2></section>
            <section class="CategoryListItem"><div class="GenreImage"></div></section>
            <section class="CategoryListItem"><h2 class="GenreNameOrg">B</h2></section>
            """.trimIndent(),
        )

        assertEquals(listOf("A", "B"), tags.map { it.name })
        assertNull("没有图标的卡片不该编出 genreId", tags.first().genreId)
    }
}