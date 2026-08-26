package com.piku.client.data.local

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 凭据加解密接口：实现可注入，便于 JVM 单测 */
interface CredentialCipher {
    fun encrypt(plain: String): String
    fun decrypt(cipherText: String): String
}

/** 最小键值存储：隔离 SharedPreferences，便于 JVM 单测 */
interface CredentialStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** SharedPreferences 实现 */
class SharedPreferencesCredentialStorage(private val prefs: SharedPreferences) : CredentialStorage {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** 登录凭据（仅存在于内存/加密存储中） */
data class Credentials(
    val email: String,
    val password: String,
)

/**
 * 凭据加密存储：密钥由 Android Keystore 保管（绑定设备+应用，卸载即失），
 * 密文持久化到本地，供 session 失效后自动重新登录使用。
 */
class CredentialStore(
    private val storage: CredentialStorage,
    private val cipher: CredentialCipher,
) {

    fun save(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return
        val encryptedEmail = encryptOrNull(email) ?: return
        val encryptedPassword = encryptOrNull(password) ?: return
        storage.put(KEY_EMAIL, encryptedEmail)
        storage.put(KEY_PASSWORD, encryptedPassword)
    }

    private fun encryptOrNull(plain: String): String? =
        runCatching { cipher.encrypt(plain) }
            .onFailure { Log.w(TAG, "credential encrypt failed: ${it.message}") }
            .getOrNull()

    fun load(): Credentials? {
        val email = storage.get(KEY_EMAIL) ?: return null
        val password = storage.get(KEY_PASSWORD) ?: return null
        // 密文损坏/密钥失效时静默失败，交由调用方走登出流程
        return runCatching {
            Credentials(cipher.decrypt(email), cipher.decrypt(password))
        }.getOrNull()
    }

    /** 持久化 uid：冷启动时恢复登录态用（uid 非敏感，明文存储即可） */
    fun saveUid(uid: Long) {
        storage.put(KEY_UID, uid.toString())
    }

    fun loadUid(): Long? = storage.get(KEY_UID)?.toLongOrNull()

    fun clear() {
        storage.remove(KEY_EMAIL)
        storage.remove(KEY_PASSWORD)
        storage.remove(KEY_UID)
    }

    private companion object {
        const val TAG = "PikuDiag"
        const val KEY_EMAIL = "email_enc"
        const val KEY_PASSWORD = "password_enc"
        const val KEY_UID = "uid"
    }
}

/** Android Keystore 实现：AES-256-GCM，IV 随密文一起保存 */
class KeystoreCredentialCipher(private val keyAlias: String) : CredentialCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + bytes
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String {
        val raw = Base64.decode(cipherText, Base64.NO_WRAP)
        require(raw.size > GCM_IV_SIZE) { "cipher text too short" }
        val iv = raw.copyOfRange(0, GCM_IV_SIZE)
        val data = raw.copyOfRange(GCM_IV_SIZE, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_SIZE = 12
        const val TAG_LENGTH_BITS = 128
    }
}