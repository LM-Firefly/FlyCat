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

package com.github.yumelira.yumebox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Geometry for the root native-TUN run mode: the core opens its own kernel device from these. */
@Serializable
data class TunConfig(
    val ifName: String = "Yume",
    val mtu: Int = 1500,
    val stack: String = "gvisor",
    val inet4Address: List<String> = listOf("172.19.0.1/30"),
    val inet6Address: List<String> = listOf("fdfe:dcba:9876::1/126"),
    val dnsHijack: List<String> = listOf("any:53", "tcp://any:53"),
    val autoRoute: Boolean = true,
    // strict-route adds FR_ACT_UNREACHABLE fallback rules that can black-hole traffic; off by default.
    val strictRoute: Boolean = false,
    // auto-redirect programs the iptables TCP redirect that carries app egress; on by default (the
    // traffic path relies on it). Still a user toggle in the Tun options page.
    val autoRedirect: Boolean = true,
    val includeUid: List<Int> = emptyList(),
    val excludeUid: List<Int> = emptyList(),
    // Keep every system uid (0–9999) — the root core's own uid 0 included — out of the auto-route tun,
    // or the core's egress loops back into the device it created and no traffic passes.
    // Format is mihomo's uid-range grammar "start:end" (colon, NOT a hyphen).
    val excludeUidRange: List<String> = listOf("0:9999"),
    val includeAndroidUser: List<Int> = listOf(0, 10),
    val routeAddress: List<String> = emptyList(),
    val routeExcludeAddress: List<String> = emptyList(),
    val dnsMode: TunDnsMode = TunDnsMode.RedirHost,
    val fakeIpRange: String? = "198.18.0.1/16",
    val fakeIpRange6: String? = "fc00::/18",
    val allowIpv6: Boolean = true,
    val debugLogPath: String? = null,
)

@Serializable
enum class TunDnsMode {
    @SerialName("redir-host") RedirHost,
    @SerialName("fake-ip") FakeIp,
}
