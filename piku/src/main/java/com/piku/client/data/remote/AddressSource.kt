package com.piku.client.data.remote

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/** 解析来源（系统 DNS / DoH）：抽成接口，解析与竞速才能在单测里用假实现驱动 */
internal fun interface AddressSource {
    fun resolve(hostname: String): List<InetAddress>
}

/** 带名字的来源：诊断报告要显示每条 IP 是从哪来的（系统 DNS / alidns / cloudflare） */
internal interface NamedAddressSource {
    val name: String
}

/**
 * 带 TTL 的解析缓存。
 *
 * 缓存里的地址全部不可用时忽略缓存重新查询：否则该源在 TTL 内不会再给出新答案
 * （system 30 秒、DoH 10 分钟），正是"域名明明解析得出却一直连不上"的来源。
 */
internal class CachingAddressSource(
    private val ttlMs: Long,
    private val clock: () -> Long,
    private val isUsable: (String, InetAddress) -> Boolean,
    override val name: String = "自定义来源",
    private val delegate: AddressSource,
) : AddressSource, NamedAddressSource {

    private class Entry(val addresses: List<InetAddress>, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    override fun resolve(hostname: String): List<InetAddress> {
        val now = clock()
        cache[hostname]
            ?.takeIf { now < it.expiresAt && it.addresses.any { address -> isUsable(hostname, address) } }
            ?.let { return it.addresses }
        val addresses = delegate.resolve(hostname)
        if (addresses.isNotEmpty()) cache[hostname] = Entry(addresses, now + ttlMs)
        return addresses
    }

    /** 未过期的缓存答案，作为同一次请求的备选来源 */
    fun cached(hostname: String): List<InetAddress> {
        val now = clock()
        return cache[hostname]?.takeIf { now < it.expiresAt }?.addresses.orEmpty()
    }
}
