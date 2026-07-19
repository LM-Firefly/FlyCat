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
import com.github.yumelira.yumebox.core.model.TproxyConfig
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.core.util.YamlCodec
import java.io.File

/**
 * Builds the built-in `tproxy-port` + `iptables` config fragment for the TPROXY run mode. mihomo opens
 * the tproxy listener and programs the host iptables mangle rules itself (`iptables.enable: true`), and
 * tears them down on exit — no Kotlin-side iptables. The uid whitelist/blacklist (from access control)
 * flows through `iptables.include-uid` / `exclude-uid`; the core's own uid is excluded by mihomo so its
 * egress never loops. DNS is left to the compiler's full injection, same as the Tun path.
 */
object TproxyOverride {
    const val FILE_NAME = "__tproxy_override__.yaml"

    fun buildYaml(config: TproxyConfig): String {
        val iptables =
            linkedMapOf<String, Any?>(
                "enable" to true,
                "dns-redirect" to config.dnsRedirect,
            )
        if (config.bypass.isNotEmpty()) iptables["bypass"] = config.bypass
        if (config.includeUid.isNotEmpty()) iptables["include-uid"] = config.includeUid
        if (config.excludeUid.isNotEmpty()) iptables["exclude-uid"] = config.excludeUid

        val fakeIp = config.dnsMode == TunDnsMode.FakeIp
        val dns =
            linkedMapOf<String, Any?>(
                "enable" to true,
                // iptables dns-redirect needs a concrete DNS listen address (mihomo ParseAddrPort's
                // it and fails the whole iptables setup if empty), and the REDIRECT rule points :53
                // traffic at this port.
                "listen" to "0.0.0.0:1053",
                "enhanced-mode" to if (fakeIp) "fake-ip" else "redir-host",
            )
        if (fakeIp) {
            config.fakeIpRange?.takeIf { it.isNotBlank() }?.let { dns["fake-ip-range"] = it }
            if (config.allowIpv6) {
                config.fakeIpRange6?.takeIf { it.isNotBlank() }?.let { dns["fake-ip-range6"] = it }
            }
        }

        // No allow-lan here: the tproxy listener binds 0.0.0.0 on its own (mihomo ReCreateTProxy), so
        // the other inbounds (http/socks/dns) stay on 127.0.0.1 and are not exposed to the LAN.
        return YamlCodec.dumpMap(
            linkedMapOf<String, Any?>(
                "tproxy-port" to config.port,
                "iptables" to iptables,
                "dns" to dns,
            )
        )
    }

    /** Writes the override YAML into [dir] and returns its [OverrideSpec] for the compile chain. */
    fun materialize(config: TproxyConfig, dir: File): OverrideSpec {
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        file.writeText(buildYaml(config))
        return OverrideSpec(path = file.absolutePath, ext = "yaml")
    }
}
