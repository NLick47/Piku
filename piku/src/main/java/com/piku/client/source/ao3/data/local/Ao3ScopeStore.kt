package com.piku.client.source.ao3.data.local

import android.content.SharedPreferences
import com.piku.client.core.source.SourceScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * 「我订阅的圈层」落盘。AO3 自己的文件，不碰全局设置——
 * 圈层是 AO3 独有的浏览方式（同人/配对/标签），别的源没有这个概念。
 */
@Singleton
class Ao3ScopeStore(
    private val prefs: SharedPreferences,
) {

    @Serializable
    private data class Stored(val kind: String, val name: String)

    private val _scopes = MutableStateFlow(loadScopes())
    val scopes: StateFlow<List<SourceScope>> = _scopes.asStateFlow()

    private val _active = MutableStateFlow(loadActive())
    val active: StateFlow<SourceScope?> = _active.asStateFlow()

    fun setScopes(scopes: List<SourceScope>) {
        val deduped = scopes.distinctBy { it.id }
        _scopes.value = deduped
        prefs.edit()
            .putString(KEY_SCOPES, json.encodeToString(deduped.map { Stored(it.kind, it.name) }))
            .apply()
        // 激活项被删掉了就落到第一个（没有就清空激活）
        if (_active.value?.id !in deduped.map { it.id }) {
            deduped.firstOrNull()?.let { setActive(it) } ?: clearActive()
        }
    }

    fun setActive(scope: SourceScope) {
        if (_active.value?.id == scope.id) return
        _active.value = scope
        prefs.edit().putString(KEY_ACTIVE, scope.id).apply()
    }

    private fun clearActive() {
        _active.value = null
        prefs.edit().remove(KEY_ACTIVE).apply()
    }

    private fun loadScopes(): List<SourceScope> = runCatching {
        val raw = prefs.getString(KEY_SCOPES, null) ?: return emptyList()
        json.decodeFromString<List<Stored>>(raw).map { SourceScope(it.kind, it.name) }
    }.getOrDefault(emptyList())

    private fun loadActive(): SourceScope? {
        val id = prefs.getString(KEY_ACTIVE, null) ?: return null
        return loadScopes().firstOrNull { it.id == id }
    }

    companion object {
        const val PREFS_NAME = "ao3_local"
        private const val KEY_SCOPES = "scopes"
        private const val KEY_ACTIVE = "active_scope"
        val json = Json { ignoreUnknownKeys = true }
    }
}
