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

package com.github.lmfirefly.flycat.runtime.service.session.transport

import android.app.PendingIntent
import android.content.Intent
import android.net.IpPrefix
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import com.github.lmfirefly.flycat.core.Clash.startHttp
import com.github.lmfirefly.flycat.core.Clash.startTun
import com.github.lmfirefly.flycat.core.Clash.stopHttp
import com.github.lmfirefly.flycat.core.Clash.stopTun
import com.github.lmfirefly.flycat.core.util.network.parseInetSocketAddress
import com.github.lmfirefly.flycat.runtime.api.constants.Components
import com.github.lmfirefly.flycat.runtime.api.session.RuntimeSpec
import com.github.lmfirefly.flycat.runtime.service.R
import com.github.lmfirefly.flycat.runtime.service.config.AccessControlMode
import com.github.lmfirefly.flycat.runtime.service.config.ServiceStore
import com.github.lmfirefly.flycat.runtime.service.config.SocketOwnerResolver
import com.github.lmfirefly.flycat.runtime.service.session.telemetry.RuntimeStartupLogStore
import com.github.lmfirefly.flycat.runtime.service.util.buildIncludedRoutesFromExcludedCidrs
import com.github.lmfirefly.flycat.runtime.service.util.parseCIDR
import com.github.lmfirefly.flycat.runtime.service.util.pendingIntentFlags
import java.net.InetAddress
import java.security.SecureRandom

class VpnTunTransport(
    private val vpnService: VpnService,
    private val store: ServiceStore = ServiceStore(),
) : RuntimeTransport {
    private val random = SecureRandom()
    private val startupLogStore =
        RuntimeStartupLogStore(vpnService, RuntimeStartupLogStore.Scope.LOCAL_TUN)
    private val ownerResolver = SocketOwnerResolver(vpnService)

    override fun start(spec: RuntimeSpec) {
        startupLogStore.append("LOCAL_TUN transport start: begin")
        val device =
            with(vpnService.Builder()) {
                val explicitRouteExcludes =
                    store.tunRouteExcludeAddress.map(String::trim).filter(String::isNotEmpty)
                val hasExplicitRouteExcludes = explicitRouteExcludes.isNotEmpty()

                addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
                if (store.allowIpv6) {
                    addAddress(TUN_GATEWAY6, TUN_SUBNET_PREFIX6)
                }

                configureRoutes(explicitRouteExcludes)
                configurePerAppRouting()

                setBlocking(false)
                setMtu(TUN_MTU)
                setSession("FlyCat")
                addDnsServer(TUN_DNS)
                if (store.allowIpv6) {
                    addDnsServer(TUN_DNS6)
                }
                setConfigureIntent(
                    PendingIntent.getActivity(
                        vpnService,
                        R.id.nf_vpn_status,
                        Intent()
                            .setComponent(Components.PROXY_SHEET_ACTIVITY)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                            ),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )

                if (Build.VERSION.SDK_INT >= 29) {
                    setMetered(false)
                }

                configureHttpProxy(hasExplicitRouteExcludes)

                if (store.allowBypass) {
                    allowBypass()
                }

                TunDevice(
                    fd = establish()?.detachFd() ?: error("Establish VPN rejected by system"),
                    stack = store.tunStackMode,
                    gateway =
                        "$TUN_GATEWAY/$TUN_SUBNET_PREFIX" +
                            if (store.allowIpv6) ",$TUN_GATEWAY6/$TUN_SUBNET_PREFIX6" else "",
                    portal = TUN_PORTAL + if (store.allowIpv6) ",$TUN_PORTAL6" else "",
                    dns =
                        if (store.dnsHijacking) {
                            NET_ANY
                        } else {
                            (TUN_DNS + if (store.allowIpv6) ",$TUN_DNS6" else "")
                        },
                )
            }

        startTun(
            fd = device.fd,
            stack = device.stack,
            gateway = device.gateway,
            portal = device.portal,
            dns = device.dns,
            markSocket = vpnService::protect,
            querySocketOwner = ownerResolver::queryOwner,
        )
        startupLogStore.append("LOCAL_TUN transport start: done")
    }

    /**
     * Route table: explicit config route excludes win (native excludeRoute on API 33+, computed
     * included routes below), then the private-network bypass list, otherwise route everything.
     */
    private fun VpnService.Builder.configureRoutes(explicitRouteExcludes: List<String>) {
        val hasExplicitRouteExcludes = explicitRouteExcludes.isNotEmpty()
        val canUseExcludeRoute = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val includedRoutes =
            if (hasExplicitRouteExcludes && !canUseExcludeRoute) {
                buildIncludedRoutesFromExcludedCidrs(
                    cidrs = explicitRouteExcludes,
                    includeIpv6 = store.allowIpv6,
                )
            } else {
                null
            }

        if (hasExplicitRouteExcludes && canUseExcludeRoute) {
            addRoute(NET_ANY, 0)
            if (store.allowIpv6) {
                addRoute(NET_ANY6, 0)
            }
            explicitRouteExcludes.map(::parseCIDR).forEach {
                runCatching {
                    excludeRoute(IpPrefix(InetAddress.getByName(it.ip), it.prefix))
                }
            }
            addRoute(TUN_DNS, 32)
            if (store.allowIpv6) {
                addRoute(TUN_DNS6, 128)
            }
        } else if (includedRoutes != null) {
            includedRoutes.ipv4.forEach { addRoute(it.ip, it.prefix) }
            if (store.allowIpv6) {
                includedRoutes.ipv6.forEach { addRoute(it.ip, it.prefix) }
            }
            addRoute(TUN_DNS, 32)
            if (store.allowIpv6) {
                addRoute(TUN_DNS6, 128)
            }
        } else if (store.bypassPrivateNetwork) {
            vpnService.resources
                .getStringArray(R.array.bypass_private_route)
                .map(::parseCIDR)
                .forEach { addRoute(it.ip, it.prefix) }
            if (store.allowIpv6) {
                vpnService.resources
                    .getStringArray(R.array.bypass_private_route6)
                    .map(::parseCIDR)
                    .forEach { addRoute(it.ip, it.prefix) }
            }
            addRoute(TUN_DNS, 32)
            if (store.allowIpv6) {
                addRoute(TUN_DNS6, 128)
            }
        } else {
            addRoute(NET_ANY, 0)
            if (store.allowIpv6) {
                addRoute(NET_ANY6, 0)
            }
        }
    }

    /**
     * Per-app routing based solely on UI access control settings. Config-level
     * `tun.include-package` / `tun.exclude-package` are intentionally ignored here;
     * only the user's selections from the access control screen take effect.
     */
    private fun VpnService.Builder.configurePerAppRouting() {
        startupLogStore.append(
            "LOCAL_TUN per-app routing: ui mode=${store.accessControlMode}" +
                " packages=${store.accessControlPackages.size}"
        )
        when (store.accessControlMode) {
            AccessControlMode.AcceptSelected -> {
                (store.accessControlPackages + vpnService.packageName).forEach {
                    runCatching { addAllowedApplication(it) }
                }
            }
            AccessControlMode.RejectSelected -> {
                (store.accessControlPackages - vpnService.packageName).forEach {
                    runCatching { addDisallowedApplication(it) }
                }
            }
            AccessControlMode.AcceptAll -> Unit
            // RejectAll: only allow the VPN service itself; no other app routes through VPN.
            AccessControlMode.RejectAll -> {
                addAllowedApplication(vpnService.packageName)
            }
        }
    }

    private fun VpnService.Builder.configureHttpProxy(hasExplicitRouteExcludes: Boolean) {
        if (Build.VERSION.SDK_INT >= 29 && store.systemProxy) {
            listenHttp()?.let {
                setHttpProxy(
                    ProxyInfo.buildDirectProxy(
                        it.address.hostAddress,
                        it.port,
                        httpProxyBlackList +
                            if (store.bypassPrivateNetwork || hasExplicitRouteExcludes) {
                                httpProxyLocalList
                            } else {
                                emptyList()
                            },
                    )
                )
            }
        }
    }

    override fun stop() {
        stopHttp()
        stopTun()
    }

    override fun onNetworkChanged() {
        vpnService.setUnderlyingNetworks(null)
    }

    private fun listenHttp(): java.net.InetSocketAddress? {
        val r = { 1 + random.nextInt(199) }
        val listenAt = "127.${r()}.${r()}.${r()}:0"
        val address = startHttp(listenAt)
        return address?.let(::parseInetSocketAddress)
    }

    private data class TunDevice(
        val fd: Int,
        val stack: String,
        val gateway: String,
        val portal: String,
        val dns: String,
    )

    private companion object {
        private const val TUN_MTU = 9000
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_SUBNET_PREFIX6 = 126
        private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_PORTAL6 = "fdfe:dcba:9876::2"
        private const val TUN_DNS = TUN_PORTAL
        private const val TUN_DNS6 = TUN_PORTAL6
        private const val NET_ANY = "0.0.0.0"
        private const val NET_ANY6 = "::"

        private val httpProxyLocalList =
            listOf(
                "localhost",
                "*.local",
                "127.*",
                "10.*",
                "172.16.*",
                "172.17.*",
                "172.18.*",
                "172.19.*",
                "172.2*",
                "172.30.*",
                "172.31.*",
                "192.168.*",
            )
        private val httpProxyBlackList =
            listOf(
                "*zhihu.com",
                "*zhimg.com",
                "*jd.com",
                "100ime-iat-api.xfyun.cn",
                "*360buyimg.com",
            )
    }
}
