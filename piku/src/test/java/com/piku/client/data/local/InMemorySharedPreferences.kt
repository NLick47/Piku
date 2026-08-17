package com.piku.client.data.local

import android.content.SharedPreferences

class InMemorySharedPreferences : SharedPreferences {

    private val map = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(key: String, defValue: String?): String? =
        map[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        map[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        map[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removals = HashSet<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            pending[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            removals.addAll(map.keys)
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            removals.forEach { map.remove(it) }
            pending.forEach { (key, value) ->
                if (value == null) map.remove(key) else map[key] = value
            }
        }
    }
}
