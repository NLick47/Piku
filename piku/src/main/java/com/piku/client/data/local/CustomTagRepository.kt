package com.piku.client.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 用户自定义标签存储（用于标签筛选的快捷入口）。
 * 以 JSON 数组形式保存在 DataStore 中，保证插入顺序（最新添加的排在最前）。
 */
class CustomTagRepository(private val dataStore: DataStore<Preferences>) {

    val customTags: Flow<List<String>> = dataStore.data.map { prefs ->
        decode(prefs[KEY_CUSTOM_TAGS])
    }

    /** 添加标签（自动去首尾空白、去 # 前缀、去重）。返回是否真正新增了标签。 */
    suspend fun addCustomTag(tag: String): Boolean {
        val normalized = normalize(tag) ?: return false
        var added = false
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_CUSTOM_TAGS])
            if (normalized !in current) {
                prefs[KEY_CUSTOM_TAGS] = encode(listOf(normalized) + current)
                added = true
            }
        }
        return added
    }

    suspend fun removeCustomTag(tag: String) {
        val normalized = normalize(tag) ?: return
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_CUSTOM_TAGS])
            val next = current.filterNot { it == normalized }
            if (next.size != current.size) {
                prefs[KEY_CUSTOM_TAGS] = encode(next)
            }
        }
    }

    private fun decode(raw: String?): List<String> = raw
        ?.let { runCatching { Json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }
        ?: emptyList()

    private fun encode(tags: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), tags)

    /** 规范化标签名：去首尾空白、去 # 前缀；为空返回 null。 */
    private fun normalize(tag: String): String? {
        val t = tag.trim().removePrefix("#").trim()
        return t.takeIf { it.isNotEmpty() }
    }

    private companion object {
        val KEY_CUSTOM_TAGS = stringPreferencesKey("custom_tags")
    }
}
