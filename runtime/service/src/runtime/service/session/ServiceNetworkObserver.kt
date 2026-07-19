/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
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
 *
 */

package com.github.yumelira.yumebox.runtime.service.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.github.yumelira.yumebox.runtime.service.util.asSocketAddressText
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
                updateDns()
                onNetworkChanged(selectNetwork())
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                networkInfos[network]?.losingMs = System.currentTimeMillis() + maxMsToLive
                updateDns()
                onNetworkChanged(selectNetwork())
            }

            override fun onLost(network: Network) {
                networkInfos.remove(network)
                updateDns()
                onNetworkChanged(selectNetwork())
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                networkInfos[network]?.dnsList = linkProperties.dnsServers
                updateDns()
                onNetworkChanged(selectNetwork())
            }
        }

    fun start() {
        updateDns()
        onNetworkChanged(selectNetwork())
        connectivity?.registerNetworkCallback(request, callback)
    }

    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        networkInfos.clear()
        updateDns()
        onNetworkChanged(null)
    }

    private fun updateDns() {
        val dnsList = selectDns().map { it.asSocketAddressText(53) }
        if (dnsList == lastDnsList) return
        lastDnsList = dnsList
        // The out-of-process core resolves DNS from its own config; no in-process push.
    }

    private fun selectDns(): List<InetAddress> {
        val entry = networkInfos.asSequence().minByOrNull { networkToScore(it) }
        return entry?.value?.dnsList ?: emptyList()
    }

    private fun selectNetwork(): Network? =
        networkInfos.asSequence().minByOrNull { networkToScore(it) }?.key

    private fun networkToScore(entry: Map.Entry<Network, NetworkInfo>): Int {
        val capabilities = connectivity?.getNetworkCapabilities(entry.key)
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
        } + if (entry.value.isAvailable()) 0 else 10
    }
}
