package com.piku.client.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSourceCodecTest {

    @Test
    fun `round trip preserves all fields`() {
        val sources = listOf(
            CatalogSource(id = "a", name = "公益池", url = "https://a.example.com/m.enc.json", encKey = "ab".repeat(32)),
            CatalogSource(id = "b", name = "plain", url = "https://b.example.com/models.json"),
        )
        assertEquals(sources, CatalogSourceCodec.decode(CatalogSourceCodec.encode(sources)))
    }

    @Test
    fun `unknown fields and broken json degrade to empty or skip`() {
        // 未知字段容忍（向前兼容新字段）
        val withExtra = """[{"id":"a","name":"n","url":"u","encKey":"","future":123}]"""
        assertEquals(1, CatalogSourceCodec.decode(withExtra).size)
        // 坏数据返回空表，不让设置页崩
        assertTrue(CatalogSourceCodec.decode("not json").isEmpty())
        assertTrue(CatalogSourceCodec.decode("").isEmpty())
    }

    @Test
    fun `autoName uses last two path directories`() {
        assertEquals(
            "foo/piku-models@catalog",
            CatalogSourceCodec.autoName("https://cdn.jsdelivr.net/gh/foo/piku-models@catalog/models.enc.json"),
        )
        assertEquals(
            "piku-models/catalog",
            CatalogSourceCodec.autoName("https://raw.githubusercontent.com/foo/piku-models/catalog/models.enc.json"),
        )
    }

    @Test
    fun `autoName falls back to host when path is short`() {
        assertEquals("example.com", CatalogSourceCodec.autoName("https://example.com/list.json"))
        assertEquals("example.com", CatalogSourceCodec.autoName("https://example.com"))
        assertEquals("example.com", CatalogSourceCodec.autoName("example.com"))
    }
}
