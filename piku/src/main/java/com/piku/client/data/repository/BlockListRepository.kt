package com.piku.client.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.piku.client.domain.model.FollowUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockListRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Entry(
        val userId: Long,
        val name: String,
        val avatarUrl: String? = null,
    )

    private val _blockedIds = MutableStateFlow<Set<Long>>(emptySet())
    /** 当前登录会话已屏蔽的用户 ID 集合（热流，供各列表页过滤） */
    val blockedIds: StateFlow<Set<Long>> = _blockedIds.asStateFlow()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    /** 屏蔽名单（带昵称/头像），供屏蔽列表页展示 */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    init {
        load()
    }

    @Synchronized
    fun add(userId: Long, name: String, avatarUrl: String?) {
        update(
            blockedIds = _blockedIds.value + userId,
            entries = _entries.value.filterNot { it.userId == userId } +
                Entry(userId, name, avatarUrl),
        )
    }

    @Synchronized
    fun remove(userId: Long) {
        update(
            blockedIds = _blockedIds.value - userId,
            entries = _entries.value.filterNot { it.userId == userId },
        )
    }

    @Synchronized
    fun mergeFromServer(users: List<FollowUser>) {
        if (users.isEmpty()) return
        val merged = LinkedHashMap<Long, Entry>()
        for (entry in _entries.value) merged[entry.userId] = entry
        for (user in users) {
            val existing = merged[user.userId]
            merged[user.userId] = Entry(
                userId = user.userId,
                name = user.name.ifBlank { existing?.name.orEmpty() },
                avatarUrl = user.avatarUrl ?: existing?.avatarUrl,
            )
        }
        update(blockedIds = merged.keys.toSet(), entries = merged.values.toList())
    }


    @Synchronized
    fun clear() {
        if (_entries.value.isEmpty() && _blockedIds.value.isEmpty()) return
        update(blockedIds = emptySet(), entries = emptyList())
    }

    private fun update(blockedIds: Set<Long>, entries: List<Entry>) {
        _blockedIds.value = blockedIds
        _entries.value = entries
        prefs.edit().putString(KEY_BLOCKED_USERS, json.encodeToString(entries)).apply()
        Log.d(TAG, "blocklist update size=${entries.size}")
    }

    private fun load() {
        val raw = prefs.getString(KEY_BLOCKED_USERS, null) ?: return
        runCatching {
            val list = json.decodeFromString<List<Entry>>(raw)
            _entries.value = list
            _blockedIds.value = list.map { it.userId }.toSet()
        }.onFailure { Log.w(TAG, "blocklist load failed", it) }
    }

    private companion object {
        const val TAG = "PikuBlock"
        const val KEY_BLOCKED_USERS = "blocked_users"
    }
}
