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

import com.github.yumelira.yumebox.core.model.OverrideSpec
import com.github.yumelira.yumebox.core.model.TunConfig
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.core.util.YamlCodec
import java.io.File

/**
 * Builds the built-in `tun:` (+ `dns:`) config fragment for the Tun run mode from the user's app-side
 * [TunConfig]. In Tun mode the core opens its OWN kernel TUN from this block (liboverride keeps it
 * authoritative rather than force-disabling it, see the Rust `RunMode` flag), so every geometry knob —
 * interface, MTU, stack, auto-route/redirect, uid/user scoping, DNS mode — is chosen here in the app.
 *
 * It is delivered as an ordinary override in the compile chain, NOT a reserved one: the "disable all
 * overrides" switch empties the whole chain including this fragment (raw subscription then wins), by
 * design. A plain `tun:` object flows through liboverride's per-field merge and carries even the keys
 * that are not in its tun schema (device / inet4-address / include-android-user / include-uid …),
 * which pass through untouched.
 */
object TunOverride {
    const val FILE_NAME = "__tun_override__.yaml"

    fun buildYaml(config: TunConfig): String {
        val tun =
            linkedMapOf<String, Any?>(
                "enable" to true,
                "device" to config.ifName,
                "stack" to config.stack,
                "mtu" to config.mtu,
                "auto-route" to config.autoRoute,
                "strict-route" to config.strictRoute,
                "auto-redirect" to config.autoRedirect,
                // Tun mode replaces the VpnService's per-socket protect with auto-detect-interface:
                // the core follows the real default route so its own egress never loops into the tun.
                "auto-detect-interface" to true,
                "dns-hijack" to config.dnsHijack,
                "inet4-address" to config.inet4Address,
            )
        if (config.allowIpv6 && config.inet6Address.isNotEmpty()) {
            tun["inet6-address"] = config.inet6Address
        }
        if (config.includeAndroidUser.isNotEmpty()) {
            tun["include-android-user"] = config.includeAndroidUser
        }
        if (config.includeUid.isNotEmpty()) tun["include-uid"] = config.includeUid
        if (config.excludeUid.isNotEmpty()) tun["exclude-uid"] = config.excludeUid
        if (config.routeAddress.isNotEmpty()) tun["route-address"] = config.routeAddress
        if (config.routeExcludeAddress.isNotEmpty()) {
            tun["route-exclude-address"] = config.routeExcludeAddress
        }

        val fakeIp = config.dnsMode == TunDnsMode.FakeIp
        val dns =
            linkedMapOf<String, Any?>(
                // Explicitly enabling DNS pre-empts liboverride's "no DNS → inject fake-ip" default,
                // so the chosen mode (redir-host vs fake-ip) and ranges win.
                "enable" to true,
                "enhanced-mode" to if (fakeIp) "fake-ip" else "redir-host",
            )
        if (fakeIp) {
            config.fakeIpRange?.takeIf { it.isNotBlank() }?.let { dns["fake-ip-range"] = it }
            if (config.allowIpv6) {
                config.fakeIpRange6?.takeIf { it.isNotBlank() }?.let { dns["fake-ip-range6"] = it }
            }
        }

        return YamlCodec.dumpMap(linkedMapOf("tun" to tun, "dns" to dns))
    }

    /** Writes the override YAML into [dir] and returns its [OverrideSpec] for the compile chain. */
    fun materialize(config: TunConfig, dir: File): OverrideSpec {
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        file.writeText(buildYaml(config))
        return OverrideSpec(path = file.absolutePath, ext = "yaml")
    }
}
