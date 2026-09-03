package com.piku.client.data.local

import android.content.SharedPreferences
import com.piku.client.domain.model.Work
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 屏蔽的作者：id 用于精确匹配，name 仅用于展示 */
@Serializable
data class BlockedUser(
    val id: Long,
    val name: String,
)

@Singleton
class BlockedContentRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {

    private val _blockedTags = MutableStateFlow(loadStrings(KEY_BLOCKED_TAGS))
    val blockedTags: StateFlow<List<String>> = _blockedTags.asStateFlow()

    private val _blockedUsers = MutableStateFlow(loadAuthors())
    val blockedUsers: StateFlow<List<BlockedUser>> = _blockedUsers.asStateFlow()

    fun addBlockedTag(tag: String): Boolean {
        val normalized = normalize(tag) ?: return false
        val current = _blockedTags.value
        if (normalized in current) return false
        val next = listOf(normalized) + current
        _blockedTags.value = next
        prefs.edit().putString(KEY_BLOCKED_TAGS, encodeStrings(next)).apply()
        return true
    }

    fun removeBlockedTag(tag: String) {
        val normalized = normalize(tag) ?: return
        val current = _blockedTags.value
        val next = current.filterNot { it == normalized }
        if (next.size == current.size) return
        _blockedTags.value = next
        prefs.edit().putString(KEY_BLOCKED_TAGS, encodeStrings(next)).apply()
    }

    fun blockUser(authorId: Long, authorName: String) {
        if (authorId <= 0) return
        val current = _blockedUsers.value
        if (current.any { it.id == authorId }) return
        val next = listOf(BlockedUser(authorId, authorName)) + current
        _blockedUsers.value = next
        prefs.edit().putString(KEY_BLOCKED_AUTHORS, encodeAuthors(next)).apply()
    }

    fun unblockUser(authorId: Long) {
        val current = _blockedUsers.value
        val next = current.filterNot { it.id == authorId }
        if (next.size == current.size) return
        _blockedUsers.value = next
        prefs.edit().putString(KEY_BLOCKED_AUTHORS, encodeAuthors(next)).apply()
    }

    fun filterWorks(works: List<Work>): List<Work> {
        val tags = _blockedTags.value
        val authorIds = _blockedUsers.value.map { it.id }.toHashSet()
        if (tags.isEmpty() && authorIds.isEmpty()) return works
        val loweredTags = tags.map { it.lowercase(Locale.ROOT) }
        return works.filterNot { work ->
            work.authorId in authorIds || loweredTags.any { work.title.lowercase(Locale.ROOT).contains(it) }
        }
    }

    private fun normalize(tag: String): String? {
        val t = tag.trim().removePrefix("#").trim()
        return t.takeIf { it.isNotEmpty() }
    }

    private fun loadStrings(key: String): List<String> = prefs.getString(key, null)
        ?.let { raw ->
            runCatching {
                Json.decodeFromString(ListSerializer(String.serializer()), raw)
            }.getOrNull()
        }
        ?: emptyList()

    private fun loadAuthors(): List<BlockedUser> = prefs.getString(KEY_BLOCKED_AUTHORS, null)
        ?.let { raw ->
            runCatching {
                Json.decodeFromString(ListSerializer(BlockedUser.serializer()), raw)
            }.getOrNull()
        }
        ?: emptyList()

    private fun encodeStrings(tags: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), tags)

    private fun encodeAuthors(authors: List<BlockedUser>): String =
        Json.encodeToString(ListSerializer(BlockedUser.serializer()), authors)

    private companion object {
        const val KEY_BLOCKED_TAGS = "blocked_tags"
        const val KEY_BLOCKED_AUTHORS = "blocked_authors"
    }
}
