/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.runtime.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.runtime.service.util.asSocketAddressText
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

private data class NetworkInfo(
    @Volatile var losingMs: Long = 0,
    @Volatile var dnsList: List<InetAddress> = emptyList(),
) {
    fun isAvailable(): Boolean = losingMs < System.currentTimeMillis()
}

class ServiceNetworkObserver(
    context: Context,
    private val onNetworkChanged: (Network?) -> Unit = {},
) {
    private val networkInfos = ConcurrentHashMap<Network, NetworkInfo>()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var lastDnsList = emptyList<String>()

    private val request =
        NetworkRequest.Builder()
            .apply {
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    addCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)
                }
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            }
            .build()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkInfos[network] = NetworkInfo()
                updateDnsAndNotify()
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                networkInfos[network]?.losingMs = System.currentTimeMillis() + maxMsToLive
                updateDnsAndNotify()
            }

            override fun onLost(network: Network) {
                networkInfos.remove(network)
                updateDnsAndNotify()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                networkInfos[network]?.dnsList = linkProperties.dnsServers
                updateDnsAndNotify()
            }
        }

    fun start() {
        updateDnsAndNotify()
        connectivity?.registerNetworkCallback(request, callback)
    }

    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        networkInfos.clear()
        updateDnsAndNotify()
    }

    /**
     * Combined DNS update and network selection in a single pass to avoid duplicate IPC calls to [ConnectivityManager.getNetworkCapabilities].
     */
    private fun updateDnsAndNotify() {
        val (network, dnsList) = selectBestNetworkAndDns()
        val dnsStrings = dnsList.map { it.asSocketAddressText(53) }
        if (dnsStrings != lastDnsList) {
            lastDnsList = dnsStrings
            Clash.notifyDnsChanged(dnsStrings)
        }
        onNetworkChanged(network)
    }

    private fun selectDns(): List<InetAddress> {
        val (_, dnsList) = selectBestNetworkAndDns()
        return dnsList
    }

    private fun selectNetwork(): Network? {
        val (network, _) = selectBestNetworkAndDns()
        return network
    }

    /**
     * Single-pass selection of best network and its DNS servers.
     * Avoids duplicate [ConnectivityManager.getNetworkCapabilities] IPC calls that would occur from separate [selectDns] and [selectNetwork] invocations.
     */
    private fun selectBestNetworkAndDns(): Pair<Network?, List<InetAddress>> {
        var bestNetwork: Network? = null
        var bestDns: List<InetAddress> = emptyList()
        var bestScore = Int.MAX_VALUE
        for ((network, info) in networkInfos) {
            val score = networkToScore(network, info)
            if (score < bestScore) {
                bestScore = score
                bestNetwork = network
                bestDns = info.dnsList
            }
        }
        return bestNetwork to bestDns
    }

    private fun networkToScore(network: Network, info: NetworkInfo): Int {
        val capabilities = connectivity?.getNetworkCapabilities(network)
        return when {
            capabilities == null -> 100
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 90
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> 2
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 4
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) -> 5
            else -> 20
        } + if (info.isAvailable()) 0 else 10
    }
}
