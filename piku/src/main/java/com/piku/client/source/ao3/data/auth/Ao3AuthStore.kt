package com.piku.client.source.ao3.data.auth

import com.piku.client.core.storage.prefs.CredentialStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AO3 的账号信息只存用户名：会话本身在 AO3 自己的 cookie 罐里，
 * 密码不落盘——站点靠 Remember Me 的 cookie 维持登录，没必要留一份可重放的凭据。
 */
@Singleton
class Ao3AuthStore @Inject constructor(
    private val storage: CredentialStorage,
) {

    fun username(): String? = storage.get(KEY_USERNAME)?.takeIf { it.isNotBlank() }

    fun save(username: String) {
        storage.put(KEY_USERNAME, username)
    }

    fun clear() {
        storage.remove(KEY_USERNAME)
    }

    private companion object {
        const val KEY_USERNAME = "username"
    }
}
