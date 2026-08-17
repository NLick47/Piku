package com.piku.client.data.remote

import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession

/**
 * 按 [PoipikuNetworkPolicy] 校验主机名。
 *
 * SNI 被改写（清空或伪装为 CloudFront 分发域名）后，证书 SAN 与 URL 域名
 * 不再一致，默认校验会失败。这里仅对 [PoipikuNetworkPolicy] 管理的域名
 * 按规则内的可信 SAN 白名单校验（证书仍由公网 CA 签发，安全性等价），
 * 其余域名一律走系统默认校验。
 */
class PoipikuHostnameVerifier : HostnameVerifier {

    private val defaultVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    override fun verify(hostname: String, session: SSLSession): Boolean {
        val rule = PoipikuNetworkPolicy.rules[hostname]
            ?: return defaultVerifier.verify(hostname, session)
        val cert = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
        val sans = cert.subjectAlternativeNames ?: return false
        val dnsNames = sans.filter { it[0] as Int == 2 }.mapNotNull { it[1] as? String }
        return rule.trustedSans.any { dnsNames.contains(it) }
    }
}