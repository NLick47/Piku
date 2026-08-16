package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularTagParserTest {

    private fun readResource(name: String): String =
        javaClass.classLoader.getResource(name)!!.readText()

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
}