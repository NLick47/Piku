package com.piku.client.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkEnvironment(
    val transport: String,
    val validated: Boolean,
    val metered: Boolean,
    val systemDns: List<String>,
)

@Singleton
class NetworkEnvironmentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 读不到就报未知：诊断页不能因为读环境把 App 带崩 */
    fun read(): NetworkEnvironment = runCatching { readOrThrow() }.getOrElse {
        NetworkEnvironment("未知", validated = false, metered = false, systemDns = emptyList())
    }

    private fun readOrThrow(): NetworkEnvironment {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkEnvironment("无网络", validated = false, metered = false, systemDns = emptyList())
        val network = manager.activeNetwork
            ?: return NetworkEnvironment("无网络", validated = false, metered = false, systemDns = emptyList())
        val capabilities = manager.getNetworkCapabilities(network)
        val link = manager.getLinkProperties(network)
        val transport = when {
            capabilities == null -> "未知"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "其他"
        }
        return NetworkEnvironment(
            transport = transport,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            systemDns = link?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
        )
    }
}
