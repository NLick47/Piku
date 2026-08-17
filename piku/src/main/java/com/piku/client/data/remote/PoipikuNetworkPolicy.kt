package com.piku.client.data.remote

/**
 * 域名 → TLS 策略的单一来源，供 SNI 改写与证书校验共用，避免规则两处漂移。
 *
 * @property sni 实际写入 ClientHello 的 SNI 值：null 表示不发送 SNI（清空），
 *   非 null 表示原样发送（无规则时）或伪装为给定值
 * @property trustedSans 证书 SAN 白名单（该域名连接时允许出现的 DNS 名称）
 */
data class SniRule(
    val sni: String?,
    val trustedSans: Set<String>,
)

object PoipikuNetworkPolicy {

    val rules: Map<String, SniRule> = mapOf(
        // 主站：服务器按 IP 提供证书，不依赖 SNI，直接清空以绕过 SNI 检测
        "poipiku.com" to SniRule(
            sni = null,
            trustedSans = setOf("poipiku.com", "*.poipiku.com"),
        ),
        // 图片 CDN：CloudFront 必须靠 SNI 选择分发，但 cdn.poipiku.com 的 SNI 被
        // 针对性干扰，伪装成其 CNAME 目标（同一分发，证书为 *.cloudfront.net）。
        // 注意：若 CloudFront 分发域名变更，需同步更新此处与下方提示。
        "cdn.poipiku.com" to SniRule(
            sni = "d1lm8mp911lcxf.cloudfront.net",
            trustedSans = setOf("d1lm8mp911lcxf.cloudfront.net", "*.cloudfront.net"),
        ),
    )

    /**
     * 该域名是否启用 SNI 改写/证书校验规则。恒定按规则生效：
     * SNI 伪装/清空对国内外所有网络均可用（主站单证书、伪装域名即真实
     * CloudFront 分发），因此无需环境判定。
     */
    fun isManaged(hostname: String): Boolean = rules.containsKey(hostname)

    /** 该域名实际应写入 ClientHello 的 SNI 值：null 表示清空（不发送 SNI），无规则时原样保留 */
    fun sniFor(hostname: String): String? =
        if (isManaged(hostname)) rules.getValue(hostname).sni else hostname
}