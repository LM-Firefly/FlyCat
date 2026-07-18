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

package com.github.yumelira.yumebox.data.store

import com.github.yumelira.yumebox.core.contract.NetworkSettingsReader
import com.github.yumelira.yumebox.core.model.AccessControlMode
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.model.RootTunDnsMode
import com.github.yumelira.yumebox.core.model.TunStack
import com.tencent.mmkv.MMKV

class NetworkSettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), NetworkSettingsReader {
    override val proxyMode by enumFlow(ProxyMode.Tun)
    override val bypassPrivateNetwork by boolFlow(true)
    override val dnsHijack by boolFlow(true)
    override val allowBypass by boolFlow(true)
    override val enableIPv6 by boolFlow(false)
    override val systemProxy by boolFlow(true)
    override val tunStack by enumFlow(TunStack.System)
    override val tunRouteExcludeAddress by stringListFlow(emptyList())
    override val rootTunIfName by strFlow("Yume")
    override val rootTunMtu by intFlow(1500)
    override val rootTunAutoRoute by boolFlow(true)
    override val rootTunStrictRoute by boolFlow(true)
    override val rootTunAutoRedirect by boolFlow(true)
    override val rootTunIncludeAndroidUser by intListFlow(listOf(0, 10))
    override val rootTunRouteExcludeAddress by stringListFlow(emptyList())
    override val rootTunDnsMode by enumFlow(RootTunDnsMode.RedirHost)
    override val rootTunFakeIpRange by strFlow("198.18.0.1/16")
    override val rootTunFakeIpRange6 by strFlow("fc00::/18")
    override val accessControlMode by enumFlow(AccessControlMode.ALLOW_ALL)
    override val accessControlPackages by stringSetFlow(emptySet())
    override val accessControlShowSystemApps by boolFlow(false)
    override val accessControlSelectedFirst by boolFlow(true)
}
