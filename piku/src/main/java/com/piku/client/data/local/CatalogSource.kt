package com.piku.client.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class CatalogSource(
    val id: String,
    val name: String,
    val url: String,
    val encKey: String = "",
)


object CatalogSourceCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String): List<CatalogSource> =
        runCatching { json.decodeFromString<List<CatalogSource>>(raw) }.getOrDefault(emptyList())

    fun encode(sources: List<CatalogSource>): String = json.encodeToString(sources)

    fun autoName(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val host = withoutScheme.substringBefore('/')
        val path = withoutScheme.substringAfter('/', "")
        if (path.isBlank()) return host
        val segments = path.split('/').filter { it.isNotBlank() }.toMutableList()
        // 末段含点视为文件名，不参与命名
        if (segments.lastOrNull()?.contains('.') == true) segments.removeAt(segments.lastIndex)
        return segments.takeLast(2).joinToString("/").ifBlank { host }.take(48)
    }
}
