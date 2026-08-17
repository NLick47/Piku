package com.piku.client.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户自定义标签存储（用于标签筛选的快捷入口）。
 * 以 JSON 数组形式保存在 SharedPreferences 中，保证插入顺序（最新添加的排在最前）。
 * 使用 SharedPreferences（而非 DataStore）是为了去掉 DataStore 依赖、缩小 APK；
 * 与 [LanguageStore]/[SettingsRepository] 同一套模式：内存 StateFlow 为准。
 * 增删方法无挂起点，调用线程（主线程）上读改写原子完成，不会并发丢失更新。
 */
@Singleton
class CustomTagRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {

    private val _customTags = MutableStateFlow(load())
    val customTags: StateFlow<List<String>> = _customTags.asStateFlow()

    /** 添加标签（自动去首尾空白、去 # 前缀、去重）。返回是否真正新增了标签。 */
    fun addCustomTag(tag: String): Boolean {
        val normalized = normalize(tag) ?: return false
        val current = _customTags.value
        if (normalized in current) return false
        val next = listOf(normalized) + current
        _customTags.value = next
        prefs.edit().putString(KEY_CUSTOM_TAGS, encode(next)).apply()
        return true
    }

    fun removeCustomTag(tag: String) {
        val normalized = normalize(tag) ?: return
        val current = _customTags.value
        val next = current.filterNot { it == normalized }
        if (next.size == current.size) return
        _customTags.value = next
        prefs.edit().putString(KEY_CUSTOM_TAGS, encode(next)).apply()
    }

    private fun load(): List<String> = prefs.getString(KEY_CUSTOM_TAGS, null)
        ?.let { raw ->
            runCatching {
                Json.decodeFromString(ListSerializer(String.serializer()), raw)
            }.getOrNull()
        }
        ?: emptyList()

    private fun encode(tags: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), tags)

    /** 规范化标签名：去首尾空白、去 # 前缀；为空返回 null。 */
    private fun normalize(tag: String): String? {
        val t = tag.trim().removePrefix("#").trim()
        return t.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val KEY_CUSTOM_TAGS = "custom_tags"
    }
}
