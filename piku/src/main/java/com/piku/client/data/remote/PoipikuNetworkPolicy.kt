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
        // pixiv 图片 CDN：与主站一样按 IP 提供证书——清空 SNI 后 server 给
        // CN=pximg.net、SAN 为 pximg.net/*.pximg.net 的证书（2026-09 实测在
        // 210.140.139.129 上 TLS1.3 握手成功）；而带上 i.pximg.net 的 SNI 会在
        // 握手阶段被 RST（约 90ms），故必须清空。
        "i.pximg.net" to SniRule(
            sni = null,
            trustedSans = setOf("pximg.net", "*.pximg.net"),
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