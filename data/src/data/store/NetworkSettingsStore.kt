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

import com.github.yumelira.yumebox.core.model.RootTunDnsMode
import com.github.yumelira.yumebox.data.model.AccessControlMode
import com.github.yumelira.yumebox.data.model.AccessControlSortMode
import com.github.yumelira.yumebox.data.model.ProxyMode
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.data.model.TunStack
import com.tencent.mmkv.MMKV

class NetworkSettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv) {
    val proxyMode by enumFlow(ProxyMode.Tun)

    // The run mode selected in the UI. Only VpnService is available today; the root modes gate a few
    // options (e.g. disable-all-overrides) that only apply to them.
    val runMode by enumFlow(RunMode.VpnService)

    val bypassPrivateNetwork by boolFlow(true)
    val dnsHijack by boolFlow(true)
    val allowBypass by boolFlow(true)
    val enableIPv6 by boolFlow(false)
    val systemProxy by boolFlow(true)

    // When on, the override chain is skipped entirely and the raw subscription config is used as-is.
    val disableAllOverride by boolFlow(false)

    // gVisor is the reliable VpnService default (userspace, no per-socket protect needed). system/mixed
    // work too but route egress through cross-process protect; users can opt into them in settings.
    val tunStack by enumFlow(TunStack.GVisor)
    val tunRouteExcludeAddress by stringListFlow(emptyList())
    val rootTunIfName by strFlow("Yume")
    val rootTunMtu by intFlow(1500)
    val rootTunAutoRoute by boolFlow(true)
    val rootTunStrictRoute by boolFlow(true)
    val rootTunAutoRedirect by boolFlow(true)
    val rootTunIncludeAndroidUser by intListFlow(listOf(0, 10))
    val rootTunRouteExcludeAddress by stringListFlow(emptyList())
    val rootTunDnsMode by enumFlow(RootTunDnsMode.RedirHost)
    val rootTunFakeIpRange by strFlow("198.18.0.1/16")
    val rootTunFakeIpRange6 by strFlow("fc00::/18")
    val accessControlMode by enumFlow(AccessControlMode.ALLOW_ALL)
    val accessControlPackages by stringSetFlow(emptySet())
    val accessControlSelectedFirst by boolFlow(true)
    val accessControlShowSystemApps by boolFlow(false)
    val accessControlSortMode by enumFlow(AccessControlSortMode.LABEL)
}
