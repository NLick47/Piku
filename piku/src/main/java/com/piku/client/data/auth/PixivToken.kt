package com.piku.client.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class PixivToken(
    val accessToken: String,
    val refreshToken: String,
    /** 过期时刻（epoch millis）。pixiv 只给 expires_in 秒数，换算后存，读时才不用再算 */
    val expiresAt: Long,
    val userId: String = "",
    val name: String = "",
    val account: String = "",
    val avatarUrl: String? = null,
) {
    /** 留 60 秒余量：请求发出到服务端校验之间还会花掉一点时间 */
    fun isExpiring(now: Long): Boolean = now >= expiresAt - EXPIRY_MARGIN_MS

    companion object {
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
