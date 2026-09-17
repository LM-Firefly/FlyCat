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

package com.github.lmfirefly.flycat.data.store

import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.model.AccessControlMode
import com.github.lmfirefly.flycat.core.model.WifiAutomationFallbackAction
import com.github.lmfirefly.flycat.core.model.WifiAutomationRule
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunDnsMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunStack
import com.tencent.mmkv.MMKV

class NetworkSettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), NetworkSettingsReader {
    init {
        // 一次性迁移：将旧版“服务”MMKV键中的值复制到“network_settings”MMKV中，以便从旧版存储布局升级的用户，其自定义的网络设置仍能在用户界面中正常显示。
        val legacy = MMKV.mmkvWithID("service", MMKV.MULTI_PROCESS_MODE)
        fun migrateBool(newKey: String, legacyKey: String, default: Boolean) {
            if (!mmkv.containsKey(newKey) && legacy.containsKey(legacyKey)) {
                mmkv.encode(newKey, legacy.decodeBool(legacyKey, default))
            }
        }
        migrateBool("bypassPrivateNetwork", "bypass_private_network", true)
        migrateBool("dnsHijack", "dns_hijacking", true)
        migrateBool("enableIPv6", "allow_ipv6", false)
        migrateBool("allowBypass", "allow_bypass", true)
        migrateBool("systemProxy", "system_proxy", true)
        if (!mmkv.containsKey("tunStack") && legacy.containsKey("tun_stack_mode")) {
            val raw = legacy.decodeString("tun_stack_mode", "system")?.lowercase() ?: "system"
            val mapped = when (raw) {
                "gvisor" -> "GVisor"
                "mixed" -> "Mixed"
                "mips" -> "Mips"
                else -> "System"
            }
            mmkv.encode("tunStack", mapped)
        }
    }
    override val runMode by enumFlow(RunMode.VpnService)
    override val bypassPrivateNetwork by boolFlow(true)
    override val ebpfBypassCn by boolFlow(false)
    override val dnsHijack by boolFlow(true)
    override val allowBypass by boolFlow(true)
    override val enableIPv6 by boolFlow(false)
    override val systemProxy by boolFlow(true)
    override val disableAllOverride by boolFlow(false)
    override val tunStack by enumFlow(TunStack.System)
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
    override val wifiAutomationEnabled by boolFlow(false)
    override val wifiAutomationLocationRequested by boolFlow(false)
    override val wifiAutomationRules: Preference<List<WifiAutomationRule>> by
        jsonListFlow(
            default = emptyList(),
            decode = { source -> decodeFromString<List<WifiAutomationRule>>(source) },
            encode = { rules -> encodeToString(rules) },
        )
    override val wifiAutomationOtherWifiAction by enumFlow(WifiAutomationFallbackAction.Keep)
    override val wifiAutomationNoWifiAction by enumFlow(WifiAutomationFallbackAction.Keep)
    override val wifiAutomationOtherWifiProfileUuid by strFlow("")
    override val wifiAutomationNoWifiProfileUuid by strFlow("")
}
