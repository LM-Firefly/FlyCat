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

import kotlinx.serialization.Serializable

/**
 * Root TPROXY run mode: the core opens `tproxy-port` and programs the host iptables mangle rules itself.
 * mihomo excludes the core's own uid from those rules so its egress is never redirected back into itself.
 */
@Serializable
data class TproxyConfig(
    val port: Int = 7893,
    val dnsMode: TunDnsMode = TunDnsMode.RedirHost,
    val fakeIpRange: String? = "198.18.0.1/16",
    val fakeIpRange6: String? = "fc00::/18",
    val includeUid: List<Int> = emptyList(),
    val excludeUid: List<Int> = emptyList(),
    val bypass: List<String> = emptyList(),
    val dnsRedirect: Boolean = true,
    val allowIpv6: Boolean = false,
)
