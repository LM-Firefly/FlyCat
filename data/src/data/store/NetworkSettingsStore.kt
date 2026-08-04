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

package com.github.yumeyucca.yumebox.data.store

import com.github.yumeyucca.yumebox.core.model.TunDnsMode
import com.github.yumeyucca.yumebox.data.model.AccessControlMode
import com.github.yumeyucca.yumebox.data.model.AccessControlSortMode
import com.github.yumeyucca.yumebox.data.model.RunMode
import com.github.yumeyucca.yumebox.data.model.TunStack
import com.tencent.mmkv.MMKV

class NetworkSettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv) {
    // Run mode selected in the UI (VpnService / Tun).
    val runMode by enumFlow(RunMode.VpnService)

    val bypassPrivateNetwork by boolFlow(true)
    val dnsHijack by boolFlow(true)
    val allowBypass by boolFlow(true)
    val enableIPv6 by boolFlow(false)
    val systemProxy by boolFlow(true)

    // All modes: drop the user override chain. Root Tun additionally skips mode geometry
    // injection and compiler runtime patches (skipRuntimePatches).
    val disableAllOverride by boolFlow(false)

    // gVisor is the reliable VpnService default (userspace, no per-socket protect); system/mixed
    // work too
    // but route egress through cross-process protect.
    val tunStack by enumFlow(TunStack.GVisor)
    val tunRouteExcludeAddress by stringListFlow(emptyList())
    val tunIfName by strFlow("Yume")
    val tunMtu by intFlow(1500)
    val tunAutoRoute by boolFlow(true)

    // strict-route off (can black-hole traffic); auto-redirect on — it carries the app TCP egress.
    val tunStrictRoute by boolFlow(false)
    val tunAutoRedirect by boolFlow(true)

    // Empty: omit include-android-user so the core includes every Android user automatically.
    val tunIncludeAndroidUser by intListFlow(emptyList())

    // redir-host is the reliable default (real IPs, no fake-ip pool/filter to get wrong); fake-ip
    // stays selectable.
    val tunDnsMode by enumFlow(TunDnsMode.RedirHost)
    val tunFakeIpRange by strFlow("198.18.0.1/16")
    val tunFakeIpRange6 by strFlow("fc00::/18")

    val accessControlMode by enumFlow(AccessControlMode.ALLOW_ALL)
    val accessControlPackages by stringSetFlow(emptySet())
    val accessControlSelectedFirst by boolFlow(true)
    val accessControlShowSystemApps by boolFlow(false)
    val accessControlSortMode by enumFlow(AccessControlSortMode.LABEL)
}
