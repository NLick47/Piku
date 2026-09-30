package com.piku.client.data.auth

import android.util.Log
import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivAuthStore @Inject constructor(
    private val storage: CredentialStorage,
    private val cipher: CredentialCipher,
    private val json: Json,
) {

    @Volatile
    private var cached: PixivToken? = restore()

    /** 传输层同步读访问令牌；未登录给 null */
    fun accessToken(): String? = cached?.accessToken

    fun current(): PixivToken? = cached

    fun save(token: PixivToken) {
        cached = token
        val plain = json.encodeToString(PixivToken.serializer(), token)
        val encrypted = runCatching { cipher.encrypt(plain) }
            .onFailure { Log.w(TAG, "pixiv token encrypt failed: ${it.message}") }
            .getOrNull()
        if (encrypted == null) {
            // 加密失败仍让本次会话可用（内存里已有），但不落盘，冷启动后自然要求重新登录
            storage.remove(KEY_TOKEN)
            return
        }
        storage.put(KEY_TOKEN, encrypted)
    }

    fun clear() {
        cached = null
        storage.remove(KEY_TOKEN)
    }

    private fun restore(): PixivToken? {
        val encrypted = storage.get(KEY_TOKEN) ?: return null
        // 密文损坏 / 密钥失效（重装、清密钥）时静默失败，交给调用方当成未登录
        return runCatching {
            json.decodeFromString(PixivToken.serializer(), cipher.decrypt(encrypted))
        }
            .onFailure { Log.w(TAG, "pixiv token restore failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() }
    }

    private companion object {
        const val TAG = "PikuDiag"
        const val KEY_TOKEN = "pixiv_token_enc"
    }
}
