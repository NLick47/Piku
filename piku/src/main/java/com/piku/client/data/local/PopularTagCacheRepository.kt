package com.piku.client.data.local

import android.content.SharedPreferences
import com.piku.client.domain.model.PopularTag
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopularTagCacheRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {

    fun save(tags: List<PopularTag>) {
        if (tags.isEmpty()) return
        prefs.edit().putString(KEY_POPULAR_TAGS, encode(tags)).apply()
    }

    fun load(): List<PopularTag>? = prefs.getString(KEY_POPULAR_TAGS, null)
        ?.let { raw ->
            runCatching {
                Json.decodeFromString(ListSerializer(PopularTag.serializer()), raw)
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }

    private fun encode(tags: List<PopularTag>): String =
        Json.encodeToString(ListSerializer(PopularTag.serializer()), tags)

    private companion object {
        const val KEY_POPULAR_TAGS = "popular_tags"
    }
}