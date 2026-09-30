package com.piku.client.data.auth

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 客户端签名的那两个头；两者必须由同一个时间字符串算出 */
data class PixivClientSignature(val time: String, val hash: String)

/**
 * pixiv 登录链路上所有会变的东西集中在这里。
 *
 * **不走 Cloudflare 中继**：cf worker 的出网 IP 被 pixiv 挡死，API 请求从那里发不出去，
 * 中转只能拿来发图片。所以登录两跳都直连 pixiv 自己的域：登录页交给 WebView
 * （设备自己的网络），换令牌走应用的原生传输（DoH + ECH）。
 *
 * 若日后建了**非 CF** 的回源，把 [loginOrigin] 换成它的地址即可（可带路径前缀）。
 */
class PixivAuthEndpoints(
    /**
     * 登录页所在源。默认直连应用接口域；
     * WebView 加载它，所以这里必须是一个**设备自己的网络到得了**的地址。
     */
    private val loginOrigin: String = PIXIV_AUTH_ORIGIN,
) {

    /** 登录页地址。挑战由调用方持有，换 token 时用同一条 verifier 回来 */
    fun loginPageUrl(codeChallenge: String): String =
        loginOrigin + LOGIN_PATH +
            "?code_challenge=$codeChallenge" +
            "&code_challenge_method=$CODE_CHALLENGE_METHOD" +
            "&client=$CLIENT" +
            "&lang=zh"

    fun tokenBaseUrl(): String = "$PIXIV_TOKEN_ORIGIN/"

    /**
     * 生成 `X-Client-Time` / `X-Client-Hash`：pixiv 要求这两个头一起出现，缺任一都按
     * "客户端凭据不合法"（1508）处理。
     *
     * - 时间必须**是当下**：UTC、带 `Z`、不留小数秒（pixiv 会校验时间是否新鲜）；
     * - 哈希是 `md5(时间 + HASH_SECRET)` 的小写十六进制，**时间字符串必须与头发出去的完全一致**。
     *
     * 纯函数、时钟由调用方给，所以能拿公开用例把算法钉死。
     */
    fun clientSignature(nowMillis: Long): PixivClientSignature {
        val time = CLIENT_TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis))
        return PixivClientSignature(time = time, hash = pixivClientHash(time))
    }

    /** 授权码换令牌的请求字段；字段名与取值都是 pixiv 侧的契约，集中一处便于对照 */
    fun authorizationCodeFields(code: String, codeVerifier: String): Map<String, String> = mapOf(
        "client_id" to CLIENT_ID,
        "client_secret" to CLIENT_SECRET,
        "code" to code,
        "code_verifier" to codeVerifier,
        "grant_type" to GRANT_AUTHORIZATION_CODE,
        "include_policy" to "true",
        "redirect_uri" to REDIRECT_URI,
    )

    /** 刷新令牌的请求字段：refresh_token 长期有效，access_token 每小时一换 */
    fun refreshTokenFields(refreshToken: String): Map<String, String> = mapOf(
        "client_id" to CLIENT_ID,
        "client_secret" to CLIENT_SECRET,
        "grant_type" to GRANT_REFRESH_TOKEN,
        "refresh_token" to refreshToken,
        "include_policy" to "true",
    )

    companion object {
        /** 登录页与授权回调都在应用接口域上 */
        const val PIXIV_AUTH_ORIGIN = "https://app-api.pixiv.net"

        /** 应用接口域：Bearer 令牌只发给它 */
        const val PIXIV_APP_API_HOST = "app-api.pixiv.net"

        /** 换令牌的 oauth 域 */
        const val PIXIV_TOKEN_ORIGIN = "https://oauth.secure.pixiv.net"

        /** 官方登录页路径（服务在应用接口域上） */
        private const val LOGIN_PATH = "/web/v1/login"

        /**
         * 应用客户端身份。oauth 与 app-api 都是给 App 用的端点，
         * 用浏览器 UA 打会被当成非法客户端——登录页那边相反，必须是浏览器 UA。
         */
        const val APP_VERSION = "5.0.234"
        const val APP_OS = "android"
        const val APP_OS_VERSION = "11"
        const val APP_USER_AGENT = "PixivAndroidApp/$APP_VERSION (Android $APP_OS_VERSION)"

        /** 授权完成后 pixiv 回跳的地址：必须与请求授权时用的一致 */
        private const val REDIRECT_URI =
            "https://" + PIXIV_APP_API_HOST + "/web/v1/users/auth/pixiv/callback"

        private const val CODE_CHALLENGE_METHOD = "S256"
        private const val CLIENT = "pixiv-android"

        private const val GRANT_AUTHORIZATION_CODE = "authorization_code"
        private const val GRANT_REFRESH_TOKEN = "refresh_token"

        /** pixiv Android 客户端的公开凭据：所有第三方客户端都用这一对，不是我们的秘密 */
        private const val CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"

        /**
         * 与 [CLIENT_ID] 成对的客户端密钥。**写错会被 pixiv 回 1508「客户端凭据不合法」**，
         * 而那条错误看不出是密钥写错了，只能靠与权威来源逐字比对。
         */
        private const val CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"

    }
}

/** 请求签名的固定密钥：与客户端密钥同属公开常量，pixiv 用它换算时间串的哈希 */
private const val HASH_SECRET = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"

/**
 * `md5(时间串 + HASH_SECRET)` 的小写十六进制。
 * 独立成函数只为一件事：能拿公开用例（那条用例的时间串带 `+02:00` 偏移）把算法钉死。
 */
internal fun pixivClientHash(time: String): String = md5Hex(time + HASH_SECRET)

/** `2019-09-02T18:51:57Z`：UTC、带 Z、无小数秒——哈希要按这个字符串算，格式错了签名就废 */
private val CLIENT_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

/**
 * 小写十六进制 MD5。自己拼而不用 `String.format("%02x")`：后者受当前 Locale 影响
 * （某些区域会用非 ASCII 数字），而签名字节必须与 pixiv 算的完全一致。
 */
private fun md5Hex(text: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
    val digits = "0123456789abcdef"
    val hex = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        hex[index * 2] = digits[value shr 4]
        hex[index * 2 + 1] = digits[value and 0x0F]
    }
    return String(hex)
}
