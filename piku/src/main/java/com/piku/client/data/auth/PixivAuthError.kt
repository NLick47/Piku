package com.piku.client.data.auth

sealed class PixivAuthError(message: String) : Exception(message) {

    data object Network : PixivAuthError("pixiv auth network")

    data object CredentialRejected : PixivAuthError("pixiv auth credential rejected")

    /** 出口被 pixiv 挡了（403）：换网络再试，不是账号的问题 */
    data object Blocked : PixivAuthError("pixiv auth blocked")

    /** 请求太频繁（429） */
    data object RateLimited : PixivAuthError("pixiv auth rate limited")

    data object Cancelled : PixivAuthError("pixiv auth cancelled")

    data object Unknown : PixivAuthError("pixiv auth unknown")
}
