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
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.core.model.TunStack
import com.tencent.mmkv.MMKV

class NetworkSettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), NetworkSettingsReader {
    override val runMode by enumFlow(RunMode.Vpn)
    override val bypassPrivateNetwork by boolFlow(true)
    override val dnsHijack by boolFlow(true)
    override val allowBypass by boolFlow(true)
    override val enableIPv6 by boolFlow(false)
    override val systemProxy by boolFlow(true)
    override val disableAllOverride by boolFlow(false)
    override val tunStack by enumFlow(TunStack.GVisor)
    override val tunRouteExcludeAddress by stringListFlow(emptyList())
    override val tunIfName by strFlow("FlyCat")
    override val tunMtu by intFlow(9000)
    override val tunAutoRoute by boolFlow(true)
    override val tunStrictRoute by boolFlow(false)
    override val tunAutoRedirect by boolFlow(true)
    override val tunIncludeAndroidUser by intListFlow(emptyList())
    override val tunDnsMode by enumFlow(TunDnsMode.RedirHost)
    override val tunFakeIpRange by strFlow("198.18.0.1/16")
    override val tunFakeIpRange6 by strFlow("fc00::/18")
    override val accessControlMode by enumFlow(AccessControlMode.ALLOW_ALL)
    override val accessControlPackages by stringSetFlow(emptySet())
    override val accessControlShowSystemApps by boolFlow(false)
    override val accessControlSelectedFirst by boolFlow(true)
}
