package com.piku.client.core.translation

import android.util.Log
import com.piku.client.core.storage.prefs.CredentialCipher
import com.piku.client.core.storage.prefs.CredentialStorage

class LlmSecretStore(
    private val storage: CredentialStorage,
    private val cipher: CredentialCipher,
) {

    fun put(providerId: String, key: String) {
        if (key.isBlank()) {
            remove(providerId)
            return
        }
        val encrypted = runCatching { cipher.encrypt(key) }
            .onFailure { Log.w(TAG, "llm secret encrypt failed: ${it.message}") }
            .getOrNull()
        if (encrypted == null) {
            storage.remove(keyOf(providerId))
            return
        }
        storage.put(keyOf(providerId), encrypted)
    }

    fun get(providerId: String): String? =
        storage.get(keyOf(providerId))?.let { encrypted ->
            runCatching { cipher.decrypt(encrypted) }.getOrNull()
        }

    fun remove(providerId: String) {
        storage.remove(keyOf(providerId))
    }

    private fun keyOf(providerId: String): String = PREFIX + providerId

    private companion object {
        const val TAG = "PikuDiag"
        const val PREFIX = "llm_provider_key_"
    }
}
