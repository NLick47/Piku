package com.piku.client.data.auth

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object PixivPkce {

    /** 32 字节随机数 base64url 后是 43 个字符，落在 RFC 要求的长度区间内 */
    fun newVerifier(random: SecureRandom = SecureRandom()): String =
        base64Url(ByteArray(32).also(random::nextBytes))

    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * 从回调地址里取授权码。
 *
 * 官方登录页完成后跳 `pixiv://account/login?code=...`；个别版本落在
 * `https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback?code=...` 上，两种都认。
 * 用 [URI] 而不是 android.net.Uri：这段是纯字符串逻辑，要能在 JVM 单测里直接跑。
 *
 * 取到的是 **rawQuery**，也就是仍处于编码态的值，必须在这里解一次码：
 * 客户端随后按表单规则还会再编码一次，不解码就成了双重编码，服务端只会回 invalid_grant。
 */
internal fun parsePixivAuthCode(url: String): String? {
    val rawQuery = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
    return rawQuery.split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == "code" }
        ?.get(1)
        ?.let(::decodeQueryValue)
        ?.takeIf { it.isNotBlank() }
}

/**
 * 按 URL 规则解码查询串里的值。
 * 刻意不把 `+` 当空格：授权码是 URL-safe 串，`+` 真要出现就是字面量。
 */
private fun decodeQueryValue(raw: String): String =
    if (!raw.contains('%')) {
        raw
    } else {
        runCatching { URLDecoder.decode(raw.replace("+", "%2B"), Charsets.UTF_8.name()) }
            .getOrDefault(raw)
    }
