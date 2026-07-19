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
 * Builds the built-in `tun:` config fragment for the Tun run mode from the user's app-side
 * [TunConfig]. In Tun mode the core opens its OWN kernel TUN from this block (liboverride keeps it
 * authoritative rather than force-disabling it, see the Rust `RunMode` flag), so every geometry knob —
 * interface, MTU, stack, auto-route/redirect, uid/user scoping — is chosen here in the app.
 *
 * DNS is intentionally NOT set here. The compiler ([patch_static_runtime]) injects the same complete
 * fake-ip DNS the VpnService path relies on (nameserver + default-nameserver + fake-ip-filter +
 * enhanced-mode) whenever the profile has no `dns.enable`. Setting a partial `dns:` block here would
 * flip `dns.enable` on and SUPPRESS that injection, leaving a crippled resolver — the core comes up
 * but every app that needs a domain lookup fails while IP-pool apps (Telegram) still work. Leaving
 * DNS to the compiler keeps Tun and VpnService resolving identically.
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
        if (config.excludeUidRange.isNotEmpty()) tun["exclude-uid-range"] = config.excludeUidRange
        if (config.routeAddress.isNotEmpty()) tun["route-address"] = config.routeAddress
        if (config.routeExcludeAddress.isNotEmpty()) {
            tun["route-exclude-address"] = config.routeExcludeAddress
        }

        // DNS follows the user's chosen mode. redir-host returns real IPs and works out of the box;
        // fake-ip additionally needs its pool + filter. Setting `enable: true` here would suppress
        // the compiler's full fake-ip injection, so the compiler backfills the nameserver whenever
        // this leaves it empty (see patch_static_runtime) — the resolver is never left crippled.
        val fakeIp = config.dnsMode == TunDnsMode.FakeIp
        val dns =
            linkedMapOf<String, Any?>(
                "enable" to true,
                "enhanced-mode" to if (fakeIp) "fake-ip" else "redir-host",
            )
        if (fakeIp) {
            config.fakeIpRange?.takeIf { it.isNotBlank() }?.let { dns["fake-ip-range"] = it }
            if (config.allowIpv6) {
                config.fakeIpRange6?.takeIf { it.isNotBlank() }?.let { dns["fake-ip-range6"] = it }
            }
        }

        return YamlCodec.dumpMap(linkedMapOf<String, Any?>("tun" to tun, "dns" to dns))
    }

    /** Writes the override YAML into [dir] and returns its [OverrideSpec] for the compile chain. */
    fun materialize(config: TunConfig, dir: File): OverrideSpec {
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        file.writeText(buildYaml(config))
        return OverrideSpec(path = file.absolutePath, ext = "yaml")
    }
}
