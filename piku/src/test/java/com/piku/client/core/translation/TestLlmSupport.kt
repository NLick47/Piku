package com.piku.client.core.translation

import android.content.SharedPreferences
import com.piku.client.core.storage.prefs.CredentialCipher
import com.piku.client.core.storage.prefs.SharedPreferencesCredentialStorage
import kotlinx.serialization.json.Json

/** 测试用可逆“加密”：只验证存取路径，不碰 Android Keystore。 */
internal object PlainCredentialCipher : CredentialCipher {
    override fun encrypt(plain: String): String = "enc:$plain"
    override fun decrypt(cipherText: String): String = cipherText.removePrefix("enc:")
}

internal fun testManualProviderRepository(
    prefs: SharedPreferences,
    json: Json,
): ManualProviderRepository = ManualProviderRepository(
    prefs = prefs,
    json = json,
    secretStore = LlmSecretStore(
        storage = SharedPreferencesCredentialStorage(prefs),
        cipher = PlainCredentialCipher,
    ),
)
