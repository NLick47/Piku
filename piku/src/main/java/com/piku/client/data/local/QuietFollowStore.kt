package com.piku.client.data.local

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuietFollowStore @Inject constructor(
    private val prefs: SharedPreferences,
) {
    fun contains(userId: Long): Boolean = userId in read()

    fun mark(userId: Long) = update { it + userId }

    fun unmark(userId: Long) = update { it - userId }

    private fun read(): Set<Long> =
        prefs.getString(KEY, null)?.split(',')?.mapNotNull(String::toLongOrNull)?.toSet() ?: emptySet()

    private fun update(transform: (Set<Long>) -> Set<Long>) {
        prefs.edit().putString(KEY, transform(read()).joinToString(",")).apply()
    }

    private companion object {
        const val KEY = "pixiv_quiet_follow_ids"
    }
}
