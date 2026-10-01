package com.piku.client.data.local

import android.content.SharedPreferences
import com.piku.client.domain.model.WorkSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomTagRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {

    private val tagsBySource: Map<WorkSource, MutableStateFlow<List<String>>> =
        loadAll().mapValues { MutableStateFlow(it.value) }

    fun tags(source: WorkSource): StateFlow<List<String>> = tagsBySource.getValue(source)

    /** 添加标签（自动去首尾空白、去 # 前缀（含叠加的 #）、去重）。返回是否真正新增了标签。 */
    fun addCustomTag(source: WorkSource, tag: String): Boolean {
        val normalized = normalize(tag) ?: return false
        val current = tags(source).value
        if (normalized in current) return false
        write(source, listOf(normalized) + current)
        return true
    }

    fun removeCustomTag(source: WorkSource, tag: String) {
        val normalized = normalize(tag) ?: return
        val current = tags(source).value
        val next = current.filterNot { it == normalized }
        if (next.size == current.size) return
        write(source, next)
    }

    private fun write(source: WorkSource, tags: List<String>) {
        tagsBySource.getValue(source).value = tags
        prefs.edit().putString(key(source), encode(tags)).apply()
    }

    private fun loadAll(): Map<WorkSource, List<String>> {
        val loaded = WorkSource.entries.associateWith { decode(prefs.getString(key(it), null)) }
        if (loaded.values.any { it.isNotEmpty() }) return loaded
        // 加源维度之前只有一份全局列表，整体归给 POIPIKU（当时标签页与投稿页都只查它）
        val legacy = decode(prefs.getString(KEY_LEGACY, null))
        if (legacy.isEmpty()) return loaded
        prefs.edit().putString(key(WorkSource.POIPIKU), encode(legacy)).remove(KEY_LEGACY).apply()
        return loaded + (WorkSource.POIPIKU to legacy)
    }

    private fun decode(raw: String?): List<String> = raw?.let {
        runCatching {
            Json.decodeFromString(ListSerializer(String.serializer()), it)
        }.getOrNull()
    } ?: emptyList()

    private fun encode(tags: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), tags)

    /** 规范化标签名：去首尾空白、去全部前导 #（站点分类标签写作 "##東方"）；为空返回 null。 */
    private fun normalize(tag: String): String? {
        val t = tag.trim().trimStart('#').trim()
        return t.takeIf { it.isNotEmpty() }
    }

    private companion object {
        fun key(source: WorkSource) = "custom_tags_${source.name}"
        const val KEY_LEGACY = "custom_tags"
    }
}
