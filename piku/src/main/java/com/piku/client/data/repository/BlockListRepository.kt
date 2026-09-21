package com.piku.client.data.repository

import android.util.Log
import com.piku.client.domain.model.FollowUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockListRepository @Inject constructor() {

    data class Entry(
        val userId: Long,
        val name: String,
        val avatarUrl: String? = null,
    )

    private val entriesById = LinkedHashMap<Long, Entry>()

    /**
     * 本会话内解除过屏蔽的用户 ID。服务端 BlockListF 有缓存延迟，解除后重新拉到的
     * 分页里可能还带着这个人，合并时必须把他们排除，否则"刚解除又被过滤"。
     * 只在本进程内有效，进程结束即失效（这正是纯内存名单的取舍）。
     */
    private val tombstones = mutableSetOf<Long>()

    private val _blockedIds = MutableStateFlow<Set<Long>>(emptySet())
    /** 当前登录会话已屏蔽的用户 ID 集合（热流，供各列表页过滤） */
    val blockedIds: StateFlow<Set<Long>> = _blockedIds.asStateFlow()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    /** 屏蔽名单（带昵称/头像），供屏蔽列表页展示 */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun add(userId: Long, name: String, avatarUrl: String?) {
        tombstones.remove(userId)
        entriesById.remove(userId)
        entriesById[userId] = Entry(userId, name, avatarUrl)
        publish()
    }

    @Synchronized
    fun remove(userId: Long) {
        tombstones.add(userId)
        entriesById.remove(userId)
        publish()
    }

    @Synchronized
    fun mergeFromServer(users: List<FollowUser>) {
        if (users.isEmpty()) return
        var changed = false
        for (user in users) {
            if (user.userId in tombstones) continue
            val existing = entriesById[user.userId]
            val merged = Entry(
                userId = user.userId,
                name = user.name.ifBlank { existing?.name.orEmpty() },
                avatarUrl = user.avatarUrl ?: existing?.avatarUrl,
            )
            if (merged != existing) {
                entriesById[user.userId] = merged
                changed = true
            }
        }
        if (changed) publish()
    }

    @Synchronized
    fun clear() {
        if (entriesById.isEmpty() && tombstones.isEmpty()) return
        entriesById.clear()
        tombstones.clear()
        publish()
    }

    private fun publish() {
        _entries.value = entriesById.values.toList()
        _blockedIds.value = entriesById.keys.toSet()
        Log.d(TAG, "blocklist update size=${entriesById.size}")
    }

    private companion object {
        const val TAG = "PikuBlock"
    }
}
