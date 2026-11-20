package com.piku.client.source.ao3.data.local

import android.content.SharedPreferences
import javax.inject.Singleton

@Singleton
class Ao3BookmarkSyncStore(
    private val prefs: SharedPreferences,
) {

    fun isMirrored(account: String, workId: Long): Boolean =
        prefs.getStringSet(key(account), emptySet())?.contains(workId.toString()) == true

    fun setMirrored(account: String, workId: Long, mirrored: Boolean) {
        val key = key(account)
        val ids = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (mirrored) ids.add(workId.toString()) else ids.remove(workId.toString())
        prefs.edit().putStringSet(key, ids).apply()
    }

    private fun key(account: String): String = "mirrored_${account.lowercase()}"
}
